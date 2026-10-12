@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PackedSubtaskResultTest {
  @Test
  fun `happy path`() {
    val value = PackedSubtaskResult(SubtaskState.Returned, 13)
    assertThat(value.value).isEqualTo(210U)
    assertThat(value.subtaskState).isEqualTo(SubtaskState.Returned)
    assertThat(value.subtaskIndex).isEqualTo(13)
  }

  @Test
  fun max() {
    val value = PackedSubtaskResult(SubtaskState.CanceledBeforeReturned, 0xfffffff)
    assertThat(value.value).isEqualTo(4294967284U)
    assertThat(value.subtaskState).isEqualTo(SubtaskState.CanceledBeforeReturned)
    assertThat(value.subtaskIndex).isEqualTo(0xfffffff)
  }

  @Test
  fun `subtaskState out of range`() {
    assertFailsWith<IllegalArgumentException> {
      PackedSubtaskResult(6U)
    }
  }
}
