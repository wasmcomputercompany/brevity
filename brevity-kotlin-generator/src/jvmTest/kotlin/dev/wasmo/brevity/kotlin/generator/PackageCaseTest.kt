package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PackageCaseTest {
  @Test
  fun `package case`() {
    assertFailsWith<IllegalStateException> { Identifier("").upperCamelCase }

    assertThat(Identifier("wall-clock").packageCase).isEqualTo("wallclock")
    assertThat(Identifier("WALL-clock").packageCase).isEqualTo("wallclock")
    assertThat(Identifier("w123-4567clock").packageCase).isEqualTo("w1234567clock")
  }
}
