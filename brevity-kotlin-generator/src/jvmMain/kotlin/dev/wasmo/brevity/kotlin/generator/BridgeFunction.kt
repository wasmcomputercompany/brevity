package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
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
  val function: IrFunction,
  val kotlinName: String,
  val liftedReceiver: Receiver? = null,
  val loweredReceiver: FlatParameter? = null,
  val liftedParameters: List<ParameterSpec>,
  val liftedParameterValues: List<CodeBlock>,
  val loweredParameters: LoweredParameters,
  val result: Result?,
) {

  context(codeBuilder: CodeBuilder)
  fun lowerParameterValues(): List<Pair<CodeBlock, CoreType>> {
    return buildList {
      if (liftedReceiver is Receiver.Id) {
        add(CodeBlock.of("this.%N", "id") to CoreType.I32)
      }

      when (loweredParameters) {
        is LoweredParameters.Flattened -> {
          for ((p, coreParameter) in loweredParameters.parameters.withIndex()) {
            val loweredParameters = coreParameter.encoder.lowerFlat(liftedParameterValues[p])
            for ((v, coreType) in coreParameter.encoder.coreTypes.withIndex()) {
              add(loweredParameters[v] to coreType)
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
          add(codeBuilder.platform.lowerAddress(addressParameterValue) to CoreType.Pointer)
        }
      }
    }
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

  /**
   * Lower a list of parameters in one of two ways:
   *
   *  * Flattened to core parameters, where each lifted parameter corresponds to one or more core
   *    parameters.
   *
   *  * Stored to memory. We encode the list of parameters as if it were a tuple.
   */
  sealed interface LoweredParameters {
    class Flattened(
      val parameters: List<FlatParameter>,
    ) : LoweredParameters

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

  /**
   * A return value for lifting and lowering.
   */
  class Result(
    val name: String,
    val type: TypeName,
    val encoder: Encoder,
    /**
     * Non-null if a host function returns data to a guest-provided pointer, and not a
     * host-allocated pointer.
     */
    val pointerParameter: ParameterSpec?,
  )

  class Factory(
    val receiver: Receiver,
    val kotlinMapper: KotlinMapper,
    val encoderFactory: EncoderFactory,
    val nameAllocator: NameAllocator,
  ) {
    fun create(value: IrFunction): BridgeFunction {
      // Pre-allocate the names we'll need.
      for (parameter in value.parameters) {
        nameAllocator.newName(parameter.kotlinName, parameter.name)
      }
      if (receiver is Receiver.Id) {
        nameAllocator.newName(receiver.name.lowerCamelCase, receiver.name)
      }

      val coreReceiver: FlatParameter? = when (receiver) {
        is Receiver.Id -> flatParameter(receiver.name, receiver.type)
        else -> null
      }

      val coreParameters = value.parameters.map {
        flatParameter(it.name, it.type)
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
        function = value,
        kotlinName = value.kotlinName,
        liftedReceiver = receiver,
        loweredReceiver = coreReceiver,
        liftedParameters = liftedParameters,
        liftedParameterValues = liftedParameterValues,
        loweredParameters = loweredParameters,
        result = value.returnType?.let { result(it) },
      )
    }

    private fun flatParameter(name: Identifier, typeName: TypeName): FlatParameter {
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

    private fun result(type: TypeName): Result {
      val encoder = encoderFactory.get(type)
      return Result(
        name = nameAllocator.newName("result"),
        type = type,
        encoder = encoder,
        pointerParameter = when {
          encoder.coreTypes.size > 1 -> {
            ParameterSpec(
              nameAllocator.newName("resultParameter"),
              CoreType.Pointer.kotlinCoreType,
            )
          }

          else -> null
        },
      )
    }
  }
}
