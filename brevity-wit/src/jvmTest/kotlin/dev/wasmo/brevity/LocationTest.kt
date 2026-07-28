package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class LocationTest {
  @Test
  fun `Location toString`() {
    assertThat(Location("file.wit", 13, 12).toString())
      .isEqualTo("file.wit:13:12")
    assertThat(Location("file.wit").toString())
      .isEqualTo("file.wit")
  }
}
