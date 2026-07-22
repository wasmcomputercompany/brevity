package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.encoders.Encoder

/**
 * A return value for lifting and lowering.
 */
class CoreResult(
  val name: String,
  val type: TypeName,
  val encoder: Encoder,
  /**
   * Non-null if a host function returns data to a guest-provided pointer, and not a host-allocated
   * pointer.
   */
  val parameter: ParameterSpec?
)
