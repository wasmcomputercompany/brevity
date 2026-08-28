package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.lowerSnakeCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase

/**
 * @param compareAsString true for types like Float that don't implement equals symmetrically. (For
 *   example, Float.NaN is not reflective.)
 */
data class SampleType(
  val id: Identifier,
  val mustAllocate: Boolean = false,
  val compareAsString: Boolean = false,
  val witType: String,
  val kotlinType: String,
  val rustType: String,
  val values: List<SampleValue>,
) {
  val idUpperCamel: String
    get() = id.upperCamelCase
  val idLowerCamel: String
    get() = id.lowerCamelCase
  val idLowerSnake: String
    get() = id.lowerSnakeCase
}

data class SampleValue(
  val kotlin: String,
  val rust: String,
)
