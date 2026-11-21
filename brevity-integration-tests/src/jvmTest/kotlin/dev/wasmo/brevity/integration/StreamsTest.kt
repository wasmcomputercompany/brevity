package dev.wasmo.brevity.integration

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import dev.wasmo.brevity.Stream
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import wit.wasmo.testing.Streams

@Burst
class StreamsTest(
  private val orientation: Orientation,
) {
  @Test
  fun `happy path`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.streams.create()
    assertThat(sender.isReceiverClosed()).isFalse()

    val sendAbc = async {
      sender.sendSome(listOf("a", "b", "c"))
    }
    val receiveAb = receiver.receive(2)
    assertThat(sendAbc.await()).isEqualTo(2)
    assertThat(receiveAb).containsExactly("a", "b")

    val sendDef = async {
      sender.sendSome(listOf("d", "e", "f"))
    }
    val receiveDef = receiver.receive(4)
    assertThat(sendDef.await()).isEqualTo(3)
    assertThat(receiveDef).containsExactly("d", "e", "f")

    receiver.closeReceiver()
    assertThat(sender.sendSome(listOf("g", "h", "i"))).isEqualTo(0)
    sender.closeSender()
    receiver.close()
    sender.close()
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
