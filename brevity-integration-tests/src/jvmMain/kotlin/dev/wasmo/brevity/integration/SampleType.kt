package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier

/**
 * @param compareAsString true for types like Float that don't implement equals symmetrically. (For
 *   example, Float.NaN is not reflective.)
 * @param kotlinEqualityMethod is the kotlin method on the type that will do the appropriate deep
 *   comparison. (For example, contentEquals for arrays)
 * @param borrowRustValue transforms a value to it may be passed to a function that doesn't require
 *   a copy. For example, this is necessary for types like `string` that are asymmetric when used as
 *   return types vs. parameters.
 */
data class SampleType(
  val id: Identifier,
  val compareAsString: Boolean = false,
  val kotlinEqualityMethod: String? = null,
  val witType: String,
  val kotlinType: String,
  val kotlinTypeMapping: Boolean = false,
  val rustType: String,
  val values: List<SampleValue>,
  val borrowRustValue: (String) -> String = { it },
)

data class SampleValue(
  val kotlin: String,
  val rust: String,
)
