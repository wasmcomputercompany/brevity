package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.encoders.AbstractRecordEncoder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.MAX_FLAT_PARAMS

class BridgeFunction(
  val nameAllocator: NameAllocator,
  val supportAsync: Boolean,
  val function: IrFunction,
  val kotlinName: String,
  val liftedReceiver: Receiver,
  private val loweredReceiver: FlatParameter? = null,
  val liftedParameterSpecs: List<ParameterSpec>,
  private val liftedParameterValues: List<CodeBlock>,
  private val loweredParameters: LoweredParameters,
  val result: Result?,
) {
  val functionName: FunctionName
    get() = function.functionName

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

      if (result is Result.PointerParameter) {
        add(result.pointerParameter)
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

      if (result is Result.PointerParameter) {
        add(CoreType.Pointer)
      }
    }

  val loweredReturnType: CoreType?
    get() = when (result) {
      null -> null
      is Result.PointerReturn -> CoreType.Pointer
      is Result.SingleCoreValueReturn -> result.encoder.coreTypes.single()
      is Result.PointerParameter -> null
    }

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
      result is Result.PointerParameter -> {
        codeBuilder.addStatement(
          "val %N = %L",
          result.pointerParameter.name,
          codeBuilder.platform.liftAddress(p.next()),
        )
        CodeBlock.of("%N", result.pointerParameter.name)
      }

      else -> null
    }

    check(!p.hasNext()) {
      "unexpected parameter count"
    }

    codeBuilder.platform.afterLiftParameters()

    val self = nameAllocator.newName("self")
    codeBuilder.addStatement("val %N = %L", self, receiverValue)
    if (result != null) {
      codeBuilder.add("val %N = ", result.loweredName)
    }
    codeBuilder.add("%N.%N(⇥", self, kotlinName)
    if (function.parameters.isNotEmpty()) {
      codeBuilder.add("\n")
    }
    for ((index, parameter) in function.parameters.withIndex()) {
      codeBuilder.add(
        "%N = %L,\n",
        nameAllocator[parameter.name],
        liftedParameterValues[index],
      )
    }
    codeBuilder.add("⇤)\n")

    codeBuilder.platform.beforeLowerReturnValue()

    return when (result) {
      is Result.PointerReturn -> {
        codeBuilder.addStatement(
          "val %N = %L",
          result.addressName,
          codeBuilder.allocate("%L", result.encoder.byteCount),
        )
        val resultAddressValue = CodeBlock.of("%N", result.addressName)
        result.encoder.store(
          baseAddress = resultAddressValue,
          value = CodeBlock.of("%N", result.loweredName),
        )
        codeBuilder.platform.coreValueToRuntimeValue(
          codeBuilder.platform.lowerAddress(resultAddressValue),
          CoreType.Pointer,
        )
      }

      is Result.SingleCoreValueReturn -> {
        codeBuilder.platform.coreValueToRuntimeValue(
          result.encoder.lowerFlat(CodeBlock.of("%N", result.loweredName)).single(),
          result.encoder.coreTypes.single(),
        )
      }

      is Result.PointerParameter -> {
        result.encoder.store(
          baseAddress = pointerParameterValue!!,
          value = CodeBlock.of("%N", result.loweredName),
        )
        null
      }

      null -> null
    }
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
          val coreValues = coreParameter.encoder.lowerFlat(liftedParameterValues[p])
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
          fieldValues = liftedParameterValues,
        )
        add(
          codeBuilder.platform.coreValueToRuntimeValue(
            codeBuilder.platform.lowerAddress(addressParameterValue),
            CoreType.Pointer,
          ),
        )
      }
    }

    if (result is Result.PointerParameter) {
      codeBuilder.addStatement(
        "val %N = %L",
        result.pointerParameter.name,
        codeBuilder.allocate("%L", CodeBlock.of("%L", result.encoder.byteCount)),
      )
      val pointer = CodeBlock.of("%N", result.pointerParameter.name)
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
    return when (result) {
      is Result.PointerParameter -> {
        result.encoder.load(
          codeBuilder.platform.runtimeValueToCoreValue(
            CodeBlock.of("%N", result.pointerParameter),
            CoreType.Pointer,
          ),
        )
      }

      is Result.PointerReturn -> {
        result.encoder.load(
          codeBuilder.platform.runtimeValueToCoreValue(
            CodeBlock.of("%N", result.loweredName),
            CoreType.Pointer,
          ),
        )
      }

      is Result.SingleCoreValueReturn -> {
        result.encoder.liftFlat(
          values = listOf(
            codeBuilder.platform.runtimeValueToCoreValue(
              CodeBlock.of("%N", result.loweredName),
              result.encoder.coreTypes.single(),
            ),
          ),
        )
      }

      null -> null
    }
  }

  fun outboundFunction(
    codeBuilder: CodeBuilder,
    invoker: Invoker,
  ): FunSpec {
    require(liftedReceiver !is Receiver.InboundInstance)

    return FunSpec.builder(kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        context(codeBuilder) {
          if (function.async && supportAsync) {
            addModifiers(KModifier.SUSPEND)
          }
          addParameters(liftedParameterSpecs)
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

  /** How we transmit the result across the boundary. */
  sealed interface Result {
    val loweredName: String
    val liftedName: String
    val arrayName: String
    val type: TypeName
    val kotlinType: KtTypeName
    val encoder: Encoder

    /** Encode the result as a single core value and return it. */
    data class SingleCoreValueReturn(
      override val loweredName: String,
      override val liftedName: String,
      override val arrayName: String,
      override val type: TypeName,
      override val kotlinType: KtTypeName,
      override val encoder: Encoder,
    ) : Result

    /** The callee allocates memory, writes the result there, and returns the address. */
    data class PointerReturn(
      override val loweredName: String,
      override val liftedName: String,
      override val arrayName: String,
      override val type: TypeName,
      override val kotlinType: KtTypeName,
      override val encoder: Encoder,
      val addressName: String,
    ) : Result

    /** The caller allocates memory, and passes the address as a parameter. */
    data class PointerParameter(
      override val loweredName: String,
      override val liftedName: String,
      override val arrayName: String,
      override val type: TypeName,
      override val kotlinType: KtTypeName,
      override val encoder: Encoder,
      val pointerParameter: ParameterSpec,
    ) : Result
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

      // Pre-allocate the names we'll need.
      for (parameter in value.parameters) {
        nameAllocator.newName(parameter.kotlinName, parameter.name)
      }
      if (receiver is Receiver.Id) {
        nameAllocator.newName(receiver.name.lowerCamelCase, receiver.name)
      }

      val coreReceiver: FlatParameter? = when (receiver) {
        is Receiver.Id -> flatParameter(nameAllocator, receiver.name, receiver.type)
        else -> null
      }

      val coreParameters = value.parameters.map {
        flatParameter(nameAllocator, it.name, it.type)
      }

      val loweredParameters = when {
        coreParameters.sumOf { it.coreSpecs.size } <= MAX_FLAT_PARAMS -> LoweredParameters.Flattened(
          coreParameters,
        )

        else -> LoweredParameters.Stored(
          addressSpec = ParameterSpec(
            nameAllocator.newName("parameterAddress"),
            CoreType.Pointer.kotlinCoreType,
          ),
          fieldEncoders = coreParameters.map { it.encoder },
        )
      }

      val liftedParameters = value.parameters.map { parameter ->
        ParameterSpec.builder(
          nameAllocator[parameter.name],
          kotlinMapper.get(parameter.type),
        ).build()
      }

      val liftedParameterValues = value.parameters.map { parameter ->
        CodeBlock.of("%N", nameAllocator[parameter.name])
      }

      return BridgeFunction(
        nameAllocator = nameAllocator,
        supportAsync = supportAsync,
        function = value,
        kotlinName = value.kotlinName,
        liftedReceiver = receiver,
        loweredReceiver = coreReceiver,
        liftedParameterSpecs = liftedParameters,
        liftedParameterValues = liftedParameterValues,
        loweredParameters = loweredParameters,
        result = value.returnType?.let { result(nameAllocator, orientation, it) },
      )
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

              v == 0 -> nameAllocator[name]
              else -> nameAllocator.newName(
                suggestion = "${name.lowerCamelCase}${v + 1}",
                tag = name to v,
              )
            }
            add(ParameterSpec(coreName, coreType.kotlinCoreType))
          }
        },
      )
    }

    private fun result(
      nameAllocator: NameAllocator,
      orientation: Orientation,
      type: TypeName,
    ): Result {
      val encoder = encoderFactory.get(type)
      val kotlinType = kotlinMapper.get(type)
      return when {
        encoder.coreTypes.size == 1 -> Result.SingleCoreValueReturn(
          loweredName = nameAllocator.newName("result"),
          liftedName = nameAllocator.newName("liftedResult"),
          arrayName = nameAllocator.newName("resultArray"),
          type = type,
          kotlinType = kotlinType,
          encoder = encoder,
        )

        orientation == Orientation.GuestCallsHost -> Result.PointerParameter(
          loweredName = nameAllocator.newName("result"),
          liftedName = nameAllocator.newName("liftedResult"),
          arrayName = nameAllocator.newName("resultArray"),
          type = type,
          kotlinType = kotlinType,
          encoder = encoder,
          pointerParameter = ParameterSpec(
            nameAllocator.newName("resultParameter"),
            CoreType.Pointer.kotlinCoreType,
          ),
        )

        else -> Result.PointerReturn(
          loweredName = nameAllocator.newName("result"),
          liftedName = nameAllocator.newName("liftedResult"),
          arrayName = nameAllocator.newName("resultArray"),
          type = type,
          kotlinType = kotlinType,
          encoder = encoder,
          addressName = nameAllocator.newName("resultAddress"),
        )
      }
    }
  }
}
