package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.kotlin.encoders.Encoder

/**
 * A list of parameters that use core types only, to be lifted on inbound calls and lowered on
 * outbound calls.
 */
class CoreParameter(
  val encoder: Encoder,
  val specs: List<ParameterSpec>,
  val names: List<String>,
)

