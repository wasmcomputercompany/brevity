package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName as KtTypeName
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.AbstractRecordEncoder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.MAX_FLAT_PARAMS

class BridgeFunction private constructor(
  private val supportAsync: Boolean,
  private val nameAllocator: NameAllocator,
  val documentation: String? = null,
  val async: Boolean = false,
  val isSupported: Boolean = true,
  val functionName: FunctionName,
  val liftedReceiver: Receiver,
  private val loweredReceiver: FlatParameter? = null,
  private val receiverName: String,
  val liftedParameters: List<LiftedParameter>,
  private val loweredParameters: LoweredParameters,
  val loweredResult: LoweredResult = LoweredResult.VoidReturn,
) {
  val kotlinName: String
    get() = functionName.kotlinName

  val loweredParameterSpecs: List<ParameterSpec>
    get() = buildList {
      if (loweredReceiver != null) {
        addAll(loweredReceiver.coreSpecs)
      }

      when (loweredParameters) {
        is LoweredParameters.Flattened -> {
          for (coreParameter in loweredParameters.parameters) {
            addAll(coreParameter.coreSpecs)
          }
        }

        is LoweredParameters.Stored -> {
          add(loweredParameters.addressSpec)
        }
      }

      if (loweredResult is LoweredResult.PointerParameter) {
        add(loweredResult.pointerParameter)
      }
    }

  val loweredParameterTypes: List<CoreType>
    get() = buildList {
      if (liftedReceiver is Receiver.Id) {
        add(CoreType.I32)
      }

      when (loweredParameters) {
        is LoweredParameters.Flattened -> {
          for (coreParameter in loweredParameters.parameters) {
            addAll(coreParameter.encoder.coreTypes)
          }
        }

        is LoweredParameters.Stored -> {
          add(CoreType.Pointer)
        }
      }

      if (loweredResult is LoweredResult.PointerParameter) {
        add(CoreType.Pointer)
      }
    }

  val loweredReturnType: CoreType?
    get() = loweredResult.loweredReturnType

  /** Returns a name allocator that already has the names used by this function allocated. */
  fun nameAllocator() = nameAllocator.copy()

  /** Lift parameters, call the function, and lower the result. */
  context(codeBuilder: CodeBuilder)
  fun liftInboundCall(parameterValues: List<CodeBlock>): CodeBlock? {
    val coreValues = parameterValues.zip(loweredParameterTypes) { value, type ->
      codeBuilder.platform.runtimeValueToCoreValue(value, type)
    }
    val p = coreValues.iterator()

    val receiverValue = when (liftedReceiver) {
      is Receiver.Id -> codeBuilder.platform.liftResource(
        id = p.next(),
        handleType = liftedReceiver.type,
      )

      is Receiver.InboundInstance -> liftedReceiver.codeBlock
      Receiver.OutboundInstance -> error("unexpected receiver")
    }

    val liftedParameterValues = when (loweredParameters) {
      is LoweredParameters.Flattened -> {
        loweredParameters.parameters.map { coreParameter ->
          coreParameter.encoder.liftFlat(
            values = coreParameter.encoder.coreTypes.map { p.next() },
          )
        }
      }

      is LoweredParameters.Stored -> {
        loweredParameters.loadAll(
          baseAddress = codeBuilder.platform.liftAddress(p.next()),
        )
      }
    }

    val pointerParameterValue = when {
      loweredResult is LoweredResult.PointerParameter -> {
        codeBuilder.addStatement(
          "val %N = %L",
          loweredResult.pointerParameter.name,
          codeBuilder.platform.liftAddress(p.next()),
        )
        CodeBlock.of("%N", loweredResult.pointerParameter.name)
      }

      else -> null
    }

    check(!p.hasNext()) {
      "unexpected parameter count"
    }

    codeBuilder.platform.afterLiftParameters()

    when (functionName) {
      is FunctionName.TaskReturn -> {
        codeBuilder.add("%L.taskReturn(⇥", codeBuilder.bridge)
        if (liftedParameterValues.size == 1) {
          codeBuilder.add("\n%L,\n", liftedParameterValues.single())
        }
        codeBuilder.add("⇤)\n")
      }

      is FunctionName.AsyncLift -> {
        require(loweredResult is LoweredResult.AsyncLift)
        codeBuilder.addStatement("val %N = %L", receiverName, receiverValue)
        codeBuilder.beginControlFlow(
          "val %N = %L.launchTask",
          loweredResult.packedAsyncResultName,
          codeBuilder.bridge,
        )
        invoke(liftedParameterValues)
        codeBuilder.endControlFlow()
      }

      else -> {
        codeBuilder.addStatement("val %N = %L", receiverName, receiverValue)
        invoke(liftedParameterValues)
      }
    }

    codeBuilder.platform.beforeLowerReturnValue()

    return when (loweredResult) {
      LoweredResult.VoidReturn -> null

      is LoweredResult.SingleCoreValueReturn -> {
        codeBuilder.platform.coreValueToRuntimeValue(
          loweredResult.result.encoder.lowerFlat(
            CodeBlock.of("%N", loweredResult.result.loweredName),
          ).single(),
          loweredResult.result.encoder.coreTypes.single(),
        )
      }

      is LoweredResult.PointerReturn -> {
        codeBuilder.addStatement(
          "val %N = %L",
          loweredResult.addressName,
          codeBuilder.allocate("%L", loweredResult.result.encoder.byteCount),
        )
        val resultAddressValue = CodeBlock.of("%N", loweredResult.addressName)
        loweredResult.result.encoder.store(
          baseAddress = resultAddressValue,
          value = CodeBlock.of("%N", loweredResult.result.loweredName),
        )
        codeBuilder.platform.coreValueToRuntimeValue(
          codeBuilder.platform.lowerAddress(resultAddressValue),
          CoreType.Pointer,
        )
      }

      is LoweredResult.PointerParameter -> {
        loweredResult.result.encoder.store(
          baseAddress = pointerParameterValue!!,
          value = CodeBlock.of("%N", loweredResult.result.loweredName),
        )
        null
      }

      is LoweredResult.AsyncLift -> {
        codeBuilder.platform.coreValueToRuntimeValue(
          CodeBlock.of(
            "%N.value.toInt()",
            loweredResult.packedAsyncResultName,
          ),
          CoreType.I32,
        )
      }
    }
  }

  context(codeBuilder: CodeBuilder)
  private fun invoke(liftedParameterValues: List<CodeBlock>) {
    val result = loweredResult.result
    if (result != null) {
      codeBuilder.add("val %N = ", result.loweredName)
    }
    codeBuilder.add("%N.%N(⇥", receiverName, kotlinName)
    if (liftedParameters.isNotEmpty()) {
      codeBuilder.add("\n")
    }
    for ((index, parameter) in liftedParameters.withIndex()) {
      codeBuilder.add(
        "%N = %L,\n",
        parameter.spec.name,
        liftedParameterValues[index],
      )
    }
    codeBuilder.add("⇤)\n")
  }

  context(codeBuilder: CodeBuilder)
  fun lowerParameterValues(): List<CodeBlock> = buildList {
    if (liftedReceiver is Receiver.Id) {
      add(
        codeBuilder.platform.coreValueToRuntimeValue(
          CodeBlock.of("this.%N", "id"),
          CoreType.I32,
        ),
      )
    }

    when (loweredParameters) {
      is LoweredParameters.Flattened -> {
        for ((p, coreParameter) in loweredParameters.parameters.withIndex()) {
          val coreValues = coreParameter.encoder.lowerFlat(liftedParameters[p].value)
          for ((index, type) in coreParameter.encoder.coreTypes.withIndex()) {
            add(codeBuilder.platform.coreValueToRuntimeValue(coreValues[index], type))
          }
        }
      }

      is LoweredParameters.Stored -> {
        codeBuilder.addStatement(
          "val %N = %L",
          loweredParameters.addressSpec.name,
          codeBuilder.allocate("%L", loweredParameters.byteCount),
        )
        val addressParameterValue = CodeBlock.of(
          "%N",
          loweredParameters.addressSpec.name,
        )
        loweredParameters.storeAll(
          baseAddress = addressParameterValue,
          fieldValues = liftedParameters.map { it.value },
        )
        add(
          codeBuilder.platform.coreValueToRuntimeValue(
            codeBuilder.platform.lowerAddress(addressParameterValue),
            CoreType.Pointer,
          ),
        )
      }
    }

    if (loweredResult is LoweredResult.PointerParameter) {
      codeBuilder.addStatement(
        "val %N = %L",
        loweredResult.pointerParameter.name,
        codeBuilder.allocate("%L", CodeBlock.of("%L", loweredResult.result.encoder.byteCount)),
      )
      val pointer = CodeBlock.of("%N", loweredResult.pointerParameter.name)
      add(
        codeBuilder.platform.coreValueToRuntimeValue(
          codeBuilder.platform.lowerAddress(pointer),
          CoreType.Pointer,
        ),
      )
    }
  }

  context(codeBuilder: CodeBuilder)
  fun liftReturnValue(): CodeBlock? {
    return when (loweredResult) {
      LoweredResult.VoidReturn -> null

      is LoweredResult.SingleCoreValueReturn -> {
        loweredResult.result.encoder.liftFlat(
          values = listOf(
            codeBuilder.platform.runtimeValueToCoreValue(
              CodeBlock.of("%N", loweredResult.result.loweredName),
              loweredResult.result.encoder.coreTypes.single(),
            ),
          ),
        )
      }

      is LoweredResult.PointerReturn -> {
        loweredResult.result.encoder.load(
          codeBuilder.platform.runtimeValueToCoreValue(
            CodeBlock.of("%N", loweredResult.result.loweredName),
            CoreType.Pointer,
          ),
        )
      }

      is LoweredResult.PointerParameter -> {
        loweredResult.result.encoder.load(
          codeBuilder.platform.runtimeValueToCoreValue(
            CodeBlock.of("%N", loweredResult.pointerParameter),
            CoreType.Pointer,
          ),
        )
      }

      is LoweredResult.AsyncLift -> {
        CodeBlock.of(
          "%L.taskResult<%T>()",
          codeBuilder.bridge,
          loweredResult.result?.kotlinType ?: UNIT,
        )
      }
    }
  }

  fun outboundFunction(bridge: CodeBlock, platform: Platform, invoker: Invoker): FunSpec {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = platform,
      nameAllocator = nameAllocator(),
    )

    require(liftedReceiver !is Receiver.InboundInstance)

    return FunSpec.builder(kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        context(codeBuilder) {
          if (async && supportAsync) {
            addModifiers(KModifier.SUSPEND)
          }
          addParameters(liftedParameters.map { it.spec })
          val result = loweredResult.result
          if (result != null) {
            returns(result.kotlinType)
          }

          val loweredParameterValues = lowerParameterValues()

          invoker.invoke(loweredParameterValues)

          val returnValue = liftReturnValue()

          if (result != null) {
            codeBuilder.addStatement("val %N = %L", result.liftedName, returnValue)
          }

          codeBuilder.platform.afterLiftResult()

          if (result != null) {
            codeBuilder.add("return %L", result.liftedName)
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  /** Polymorphic receiver of the API call. */
  sealed interface Receiver {
    /** A resource identified by an ID integer. */
    data class Id(
      val type: TypeName.Declared,
    ) : Receiver {
      val name: Identifier
        get() = Identifier("self")
    }

    /**
     * A runtime-global instance for an inbound call.
     *
     * @param codeBlock the global instance to invoke for the lifted call.
     */
    data class InboundInstance(
      val codeBlock: CodeBlock,
    ) : Receiver

    /**
     * A runtime-global instance for an outbound call. This doesn't need to identify the subject
     * instance because that's implied by the function name.
     */
    data object OutboundInstance : Receiver
  }

  data class LiftedParameter(
    val spec: ParameterSpec,
    val value: CodeBlock,
  )

  sealed interface LoweredParameters {
    /**
     * Parameters are flattened to core values. Each lifted parameter corresponds to one or more
     * core parameters.
     */
    class Flattened(
      val parameters: List<FlatParameter>,
    ) : LoweredParameters

    /** The caller allocates memory and writes parameters there. This uses tuple encoding. */
    class Stored(
      val addressSpec: ParameterSpec,
      fieldEncoders: List<Encoder>,
    ) : AbstractRecordEncoder(fieldEncoders), LoweredParameters {
      override val instanceNameHint: String
        get() = "parameters"

      override fun fieldValuesToInstance(fieldValues: List<CodeBlock>) =
        error("no instance for a list of parameters")

      override fun instanceToFieldValues(record: CodeBlock) =
        error("no instance for a list of parameters")
    }
  }

  /**
   * A list of parameters that use core types only, to be lifted on inbound calls and lowered on
   * outbound calls.
   */
  class FlatParameter(
    val encoder: Encoder,
    val coreSpecs: List<ParameterSpec>,
  )

  class Result(
    val loweredName: String,
    val liftedName: String,
    val arrayName: String,
    val type: TypeName,
    val kotlinType: KtTypeName,
    val encoder: Encoder,
  )

  /** How we transmit the result across the boundary. */
  sealed interface LoweredResult {
    val result: Result?
    val loweredReturnType: CoreType?

    /** The function does not return a value. */
    object VoidReturn : LoweredResult {
      override val result: Result?
        get() = null
      override val loweredReturnType: CoreType?
        get() = null
    }

    /** Encode the result as a single core value and return it. */
    data class SingleCoreValueReturn(
      override val result: Result,
    ) : LoweredResult {
      override val loweredReturnType: CoreType = result.encoder.coreTypes.single()
    }

    /** The callee allocates memory, writes the result there, and returns the address. */
    data class PointerReturn(
      override val result: Result,
      val addressName: String,
    ) : LoweredResult {
      override val loweredReturnType: CoreType
        get() = CoreType.Pointer
    }

    /** The caller allocates memory, and passes the address as a parameter. */
    data class PointerParameter(
      override val result: Result,
      val pointerParameter: ParameterSpec,
    ) : LoweredResult {
      override val loweredReturnType: CoreType?
        get() = null
    }

    /**
     * Call a callback function to provide the result to the caller. The lowered function returns a
     * `PackedAsyncResult`
     */
    data class AsyncLift(
      override val result: Result?,
      val packedAsyncResultName: String,
    ) : LoweredResult {
      override val loweredReturnType: CoreType
        get() = CoreType.I32
    }
  }

  enum class Orientation {
    HostCallsGuest,
    GuestCallsHost,
  }

  interface Invoker {
    /** Append code to [codeBuilder] to call the lowered function. */
    context(codeBuilder: CodeBuilder)
    fun invoke(parameterValues: List<CodeBlock>)
  }

  class Factory(
    private val kotlinMapper: KotlinMapper,
    private val encoderFactory: EncoderFactory,
    private val supportAsync: Boolean,
  ) {
    fun create(
      receiver: Receiver,
      orientation: Orientation,
      value: IrFunction,
    ): BridgeFunction {
      val nameAllocator = NameAllocator()

      val coreReceiver = flatParameter(nameAllocator, receiver)

      val liftedParameters = value.parameters.map { parameter ->
        liftedParameter(nameAllocator, parameter.kotlinName, parameter.type)
      }

      val coreParameters = value.parameters.map {
        flatParameter(nameAllocator, it.name, it.type)
      }

      val loweredParameters = lowerParameters(nameAllocator, coreParameters)

      return BridgeFunction(
        supportAsync = supportAsync,
        nameAllocator = nameAllocator,
        documentation = documentation(value, liftedParameters),
        async = supportAsync && value.async,
        isSupported = value.isSupported,
        functionName = when {
          value.async -> FunctionName.AsyncLift(value.functionName)
          else -> value.functionName
        },
        liftedReceiver = receiver,
        loweredReceiver = coreReceiver,
        receiverName = nameAllocator.newName("self"),
        liftedParameters = liftedParameters,
        loweredParameters = loweredParameters,
        loweredResult = loweredResult(
          nameAllocator = nameAllocator,
          orientation = orientation,
          async = value.async,
          type = value.returnType,
        ),
      )
    }

    fun taskReturn(
      receiver: Receiver,
      value: IrFunction,
    ): BridgeFunction {
      val nameAllocator = NameAllocator()

      val coreReceiver = flatParameter(nameAllocator, receiver)

      val returnType = value.returnType
      val coreParameters = mutableListOf<FlatParameter>()
      val liftedParameters = mutableListOf<LiftedParameter>()
      if (returnType != null) {
        coreParameters += flatParameter(nameAllocator, Identifier("result"), returnType)
        liftedParameters += liftedParameter(nameAllocator, "liftedResult", returnType)
      }

      val loweredParameters = lowerParameters(nameAllocator, coreParameters)
      val functionName = FunctionName.TaskReturn(value.functionName)

      return BridgeFunction(
        supportAsync = supportAsync,
        nameAllocator = nameAllocator,
        documentation = null,
        functionName = functionName,
        liftedReceiver = receiver,
        loweredReceiver = coreReceiver,
        receiverName = nameAllocator.newName("self"),
        liftedParameters = liftedParameters,
        loweredParameters = loweredParameters,
      )
    }

    private fun documentation(
      value: IrFunction,
      liftedParameters: List<LiftedParameter>,
    ): String? {
      val result = buildString {
        val functionDocumentation = value.documentation
        if (functionDocumentation != null) {
          this.append(functionDocumentation.content.trimIndent())
          this.append("\n\n")
        }

        for ((index, parameter) in value.parameters.withIndex()) {
          val parameterDocumentation = parameter.documentation ?: continue
          this.append("@param ${liftedParameters[index].spec.name} ")
          this.append(parameterDocumentation.content.trimIndent().replace("\n", "\n  "))
          this.append("\n\n")
        }
      }

      return result.trim().takeIf { it.isNotEmpty() }
    }

    private fun lowerParameters(
      nameAllocator: NameAllocator,
      coreParameters: List<FlatParameter>,
    ): LoweredParameters {
      return when {
        coreParameters.sumOf { it.coreSpecs.size } <= MAX_FLAT_PARAMS ->
          LoweredParameters.Flattened(coreParameters)

        else -> LoweredParameters.Stored(
          addressSpec = ParameterSpec(
            nameAllocator.newName("parameterAddress"),
            CoreType.Pointer.kotlinCoreType,
          ),
          fieldEncoders = coreParameters.map { it.encoder },
        )
      }
    }

    private fun liftedParameter(
      nameAllocator: NameAllocator,
      nameHint: String,
      type: TypeName,
    ): LiftedParameter {
      val name = nameAllocator.newName(nameHint)
      return LiftedParameter(
        spec = ParameterSpec.builder(name, kotlinMapper.get(type)).build(),
        value = CodeBlock.of("%N", name),
      )
    }

    private fun flatParameter(
      nameAllocator: NameAllocator,
      receiver: Receiver,
    ): FlatParameter? {
      return when (receiver) {
        is Receiver.Id -> flatParameter(nameAllocator, receiver.name, receiver.type)
        else -> null
      }
    }

    private fun flatParameter(
      nameAllocator: NameAllocator,
      name: Identifier,
      typeName: TypeName,
    ): FlatParameter {
      val encoder = encoderFactory.get(typeName)
      return FlatParameter(
        encoder = encoder,
        coreSpecs = buildList {
          for ((v, coreType) in encoder.coreTypes.withIndex()) {
            val nameHint = encoder.nameHints?.getOrNull(v)
            val coreName = when {
              nameHint != null -> nameAllocator.newName(
                Identifier("${name}-${nameHint.name}").lowerCamelCase,
              )

              v == 0 -> nameAllocator.newName(name.lowerCamelCase)
              else -> nameAllocator.newName(suggestion = "${name.lowerCamelCase}${v + 1}")
            }
            add(ParameterSpec(coreName, coreType.kotlinCoreType))
          }
        },
      )
    }

    private fun loweredResult(
      nameAllocator: NameAllocator,
      type: TypeName?,
      orientation: Orientation,
      async: Boolean,
    ): LoweredResult {
      if (type == null) {
        return when {
          async -> LoweredResult.AsyncLift(
            result = null,
            packedAsyncResultName = nameAllocator.newName("packedAsyncResultName"),
          )

          else -> LoweredResult.VoidReturn
        }
      }

      val result = Result(
        loweredName = nameAllocator.newName("result"),
        liftedName = nameAllocator.newName("liftedResult"),
        arrayName = nameAllocator.newName("resultArray"),
        type = type,
        kotlinType = kotlinMapper.get(type),
        encoder = encoderFactory.get(type),
      )

      return when {
        async -> LoweredResult.AsyncLift(
          result = result,
          packedAsyncResultName = nameAllocator.newName("packedAsyncResultName"),
        )

        result.encoder.coreTypes.size == 1 -> LoweredResult.SingleCoreValueReturn(
          result = result,
        )

        orientation == Orientation.GuestCallsHost -> LoweredResult.PointerParameter(
          result = result,
          pointerParameter = ParameterSpec(
            nameAllocator.newName("resultParameter"),
            CoreType.Pointer.kotlinCoreType,
          ),
        )

        else -> LoweredResult.PointerReturn(
          result = result,
          addressName = nameAllocator.newName("resultAddress"),
        )
      }
    }
  }
}
