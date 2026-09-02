package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Identifier.Companion.Identifier
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CamelCaseTest {
  @Test
  fun `upper camel case`() {
    assertFailsWith<IllegalStateException> { Identifier("").upperCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("-").upperCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("-w--").upperCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("WA%LL-C%LO%CK").upperCamelCase }

    assertThat(Identifier("wall-clock").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("WALL-clock").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("wall-CLOCK").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("WALL-CLOCK").upperCamelCase)
      .isEqualTo("WallClock")
    assertThat(Identifier("w123-4567clock").upperCamelCase)
      .isEqualTo("W1234567clock")
  }

  @Test
  fun `lower camel case`() {
    assertFailsWith<IllegalStateException> { Identifier("").lowerCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("-").lowerCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("-w--").lowerCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("w---").lowerCamelCase }
    assertFailsWith<IllegalStateException> { Identifier("WA%LL-C%LO%CK").lowerCamelCase }

    assertThat(Identifier("wall-clock").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("WALL-clock").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("wall-CLOCK").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("WALL-CLOCK").lowerCamelCase)
      .isEqualTo("wallClock")
    assertThat(Identifier("w123-4567clock").lowerCamelCase)
      .isEqualTo("w1234567clock")
  }
}
