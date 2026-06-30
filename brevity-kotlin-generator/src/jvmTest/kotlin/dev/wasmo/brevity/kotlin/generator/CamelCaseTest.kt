package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import kotlin.test.Test

class CamelCaseTest {
  @Test
  fun `camel case`() {
    assertThat(Identifier("").toCamelCase(upperCamel = true))
      .isEqualTo("")
    assertThat(Identifier("-").toCamelCase(upperCamel = true))
      .isEqualTo("")
    assertThat(Identifier("-w--").toCamelCase(upperCamel = true))
      .isEqualTo("W")
    assertThat(Identifier("wall-clock").toCamelCase(upperCamel = true))
      .isEqualTo("WallClock")
    assertThat(Identifier("WALL-clock").toCamelCase(upperCamel = true))
      .isEqualTo("WallClock")
    assertThat(Identifier("wall-CLOCK").toCamelCase(upperCamel = true))
      .isEqualTo("WallClock")
    assertThat(Identifier("WALL-CLOCK").toCamelCase(upperCamel = true))
      .isEqualTo("WallClock")
    assertThat(Identifier("w123-4567clock").toCamelCase(upperCamel = true))
      .isEqualTo("W1234567clock")
  }
}
