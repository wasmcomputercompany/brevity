package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class LocationTest {
  @Test
  fun `Location toString`() {
    val location = Location("file.wit")
    assertThat(location.toString())
      .isEqualTo("file.wit")
    assertThat(location.at(13, 12).toString())
      .isEqualTo("file.wit:13:12")
  }
}
