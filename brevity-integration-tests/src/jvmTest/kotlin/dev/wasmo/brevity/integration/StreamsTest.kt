package dev.wasmo.brevity.integration

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.wasmo.brevity.Stream
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import wit.wasmo.testing.Streams

/**
 * TODO: tests that call `assertFailsWith` assert that a specific function throws, but when built-in
 *   functions (like `stream-write-0`) throw, the entire Wasm engine should be immediately and
 *   permanently torn down. These tests make these failures look recoverable; they are not.
 */
@Burst
class StreamsTest(
  private val orientation: Orientation = Orientation.HostToGuest,
) {
  @Test
  fun `happy path`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    val sendAbc = async {
      sender.sendSome(listOf("a", "b", "c"))
    }
    val receiveAbc = receiver.receive(3)
    assertThat(sendAbc.await()).isEqualTo(3)
    assertThat(receiveAbc).containsExactly("a", "b", "c")

    receiver.closeReceiver()
    sender.closeSender()
  }

  @Test
  fun `send is larger`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    val sendAbcde = async {
      sender.sendSome(listOf("a", "b", "c", "d", "e"))
    }
    val receive3 = receiver.receive(3)
    assertThat(sendAbcde.await()).isEqualTo(3)
    assertThat(receive3).containsExactly("a", "b", "c")

    receiver.closeReceiver()
    sender.closeSender()
  }

  @Test
  fun `receive is larger`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    val sendAbc = async {
      sender.sendSome(listOf("a", "b", "c"))
    }
    val receive5 = receiver.receive(5)
    assertThat(sendAbc.await()).isEqualTo(3)
    assertThat(receive5).containsExactly("a", "b", "c")

    receiver.closeReceiver()
    sender.closeSender()
  }

  @Test
  fun `sending to closed receiver immediately returns 0`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()
    assertThat(sender.isReceiverClosed()).isFalse()

    receiver.closeReceiver()
    assertThat(sender.sendSome(listOf("g", "h", "i"))).isEqualTo(0)
    assertThat(sender.isReceiverClosed()).isTrue()

    sender.closeSender()
  }

  @Test
  fun `receiving from closed sender immediately returns empty list`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()
    assertThat(receiver.isSenderClosed()).isFalse()

    sender.closeSender()
    assertThat(receiver.receive(1)).isEmpty()
    assertThat(receiver.isSenderClosed()).isTrue()

    receiver.closeReceiver()
  }

  @Test
  fun `send on a closed sender traps`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()
    sender.closeSender()
    assertFailsWith<IllegalStateException> {
      sender.sendSome(listOf("a", "b", "c"))
    }

    receiver.closeReceiver()
  }

  @Test
  fun `receiving on a closed receiver traps`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()
    receiver.closeReceiver()
    assertFailsWith<IllegalStateException> {
      receiver.receive(5)
    }

    sender.closeSender()
  }

  @Test
  fun `receiving while receiving traps`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    async {
      assertFailsWith<ConcurrentModificationException> {
        yield()
        receiver.receive(1)
      }

      sender.sendSomeOrFail(listOf("a")) // Unblock the receive call. TODO: replace with cancel.
    }

    receiver.receive(5)
  }

  @Test
  fun `sending while sending traps`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    async {
      assertFailsWith<ConcurrentModificationException> {
        yield()
        sender.sendSome(listOf("b"))
      }

      receiver.receiveOrFail(1) // Unblock the receive call. TODO: replace with cancel.
    }

    sender.sendSomeOrFail(listOf("a"))
  }

  @Test
  fun `closing while waiting for a receive traps`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    async {
      assertFailsWith<ConcurrentModificationException> {
        yield()
        receiver.closeReceiver()
      }

      sender.sendSomeOrFail(listOf("a")) // Unblock the receive call. TODO: replace with cancel.
    }

    receiver.receive(5)
  }

  @Test
  fun `closing while waiting for a send traps`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()

    async {
      assertFailsWith<ConcurrentModificationException> {
        yield()
        sender.closeSender()
      }

      receiver.receiveOrFail(1) // Unblock the send call. TODO: replace with cancel.
    }

    sender.sendSomeOrFail(listOf("a"))
  }

  private fun Streams.create(): Pair<Streams.SenderTester, Streams.ReceiverTester> {
    return when (orientation) {
      Orientation.HostToGuest -> {
        val (sender, receiver) = Stream<String>()
        RealSenderTester(sender) to createHostGuest(receiver)
      }

      Orientation.GuestToHost -> {
        val [sender, receiver] = createGuestHost()
        sender to RealReceiverTester(receiver)
      }

      Orientation.HostToHost -> {
        val (sender, receiver) = Stream<String>()
        RealSenderTester(sender) to RealReceiverTester(receiver)
      }
    }
  }

  /**
   * Note that we don't yet attempt to support guest-to-guest, because our implementation
   * strategy requires the host always holds all state.
   */
  enum class Orientation {
    HostToGuest,
    GuestToHost,
    HostToHost,
  }
}
