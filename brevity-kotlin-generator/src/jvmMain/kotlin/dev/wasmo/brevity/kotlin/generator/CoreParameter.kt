package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.kotlin.encoders.AbstractRecordEncoder
import dev.wasmo.brevity.kotlin.encoders.Encoder

/**
 * Lower a list of parameters in one of two ways:
 *
 *  * Flattened to core parameters, where each lifted parameter corresponds to one or more core
 *    parameters.
 *
 *  * Stored to memory. We encode the list of parameters as if it were a tuple.
 */
sealed interface ParameterListEncoder {
  class Flattened(
    val coreParameters: List<CoreParameter>,
  ) : ParameterListEncoder

  class Stored(
    val addressSpec: ParameterSpec,
    fieldEncoders: List<Encoder>,
  ) : AbstractRecordEncoder(fieldEncoders), ParameterListEncoder {
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
class CoreParameter(
  val encoder: Encoder,
  val specs: List<ParameterSpec>,
  val names: List<String>,
)
