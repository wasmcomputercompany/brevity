package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import kotlin.test.Test

class CamelCaseTest {
  @Test
  fun `upper camel case`() {
    assertThat(Identifier("").upperCamelCase)
      .isEqualTo("")
    assertThat(Identifier("-").upperCamelCase)
      .isEqualTo("")
    assertThat(Identifier("-w--").upperCamelCase)
      .isEqualTo("W")
    assertThat(Identifier("wall-clock").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("WALL-clock").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("wall-CLOCK").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("WALL-CLOCK").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("WA%LL-C%LO%CK").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("w123-4567clock").upperCamelCase)
      .isEqualTo("W1234567clock")
  }

  @Test
  fun `lower camel case`() {
    assertThat(Identifier("").lowerCamelCase)
      .isEqualTo("")
    assertThat(Identifier("-").lowerCamelCase)
      .isEqualTo("")
    assertThat(Identifier("-w--").lowerCamelCase)
      .isEqualTo("W")
    assertThat(Identifier("w---").lowerCamelCase)
      .isEqualTo("w")
    assertThat(Identifier("wall-clock").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("WALL-clock").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("wall-CLOCK").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("WALL-CLOCK").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("WA%LL-C%LO%CK").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("w123-4567clock").lowerCamelCase)
      .isEqualTo("w1234567clock")
  }
}
