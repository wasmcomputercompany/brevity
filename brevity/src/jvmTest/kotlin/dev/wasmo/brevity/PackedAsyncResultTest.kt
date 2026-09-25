@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PackedAsyncResultTest {
  @Test
  fun `happy path`() {
    val value = PackedAsyncResult(CallbackCode.Wait, 13)
    assertThat(value.value).isEqualTo(210U)
    assertThat(value.callbackCode).isEqualTo(CallbackCode.Wait)
    assertThat(value.waitableSetIndex).isEqualTo(13)
  }

  @Test
  fun max() {
    val value = PackedAsyncResult(CallbackCode.Wait, 0xfffffff)
    assertThat(value.value).isEqualTo(4294967282U)
    assertThat(value.callbackCode).isEqualTo(CallbackCode.Wait)
    assertThat(value.waitableSetIndex).isEqualTo(0xfffffff)
  }

  @Test
  fun `callbackCode out of range`() {
    assertFailsWith<IllegalArgumentException> {
      PackedAsyncResult(5U)
    }
  }
}
