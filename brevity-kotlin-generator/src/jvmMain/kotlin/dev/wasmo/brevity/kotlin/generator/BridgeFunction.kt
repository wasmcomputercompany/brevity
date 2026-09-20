package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.AbstractRecordEncoder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.MAX_FLAT_PARAMS

class BridgeFunction(
  val kotlinName: String,
  val receiver: FlatParameter? = null,
  val parameterList: ParameterList,
  val result: Result?,
) {
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
  sealed interface ParameterList {
    class Flattened(
      val parameters: List<FlatParameter>,
    ) : ParameterList

    class Stored(
      val addressSpec: ParameterSpec,
      fieldEncoders: List<Encoder>,
    ) : AbstractRecordEncoder(fieldEncoders), ParameterList {
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

      val parameterList = when {
        coreParameters.sumOf { it.coreSpecs.size } <= MAX_FLAT_PARAMS -> ParameterList.Flattened(
          coreParameters,
        )

        else -> ParameterList.Stored(
          addressSpec = ParameterSpec(
            nameAllocator.newName("parameterAddress"),
            CoreType.Pointer.kotlinCoreType,
          ),
          fieldEncoders = coreParameters.map { it.encoder },
        )
      }

      return BridgeFunction(
        kotlinName = value.kotlinName,
        receiver = coreReceiver,
        parameterList = parameterList,
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
