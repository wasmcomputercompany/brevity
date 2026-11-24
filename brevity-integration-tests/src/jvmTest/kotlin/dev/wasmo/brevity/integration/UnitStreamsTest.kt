package dev.wasmo.brevity.integration

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Stream
import dev.wasmo.brevity.integration.StreamsTest.Orientation
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import wit.wasmo.testing.UnitStreams
import wit.wasmo.testing.UnitStreams.UnitReceiverTester
import wit.wasmo.testing.UnitStreams.UnitSenderTester

/**
 * Unit streams are a special case because they don't require values to be encoded, just counts.
 */
@Burst
class UnitStreamsTest(
  private val orientation: Orientation,
) {
  @Test
  fun `unit without fast optimizations`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val send3 = async {
      sender.sendSome(3, fast = false)
    }
    val receive3 = receiver.receive(3, fast = false)
    assertThat(send3.await()).isEqualTo(3)
    assertThat(receive3).isEqualTo(3)

    receiver.close()
    sender.close()
  }

  private fun UnitStreams.create(): Pair<UnitSenderTester, UnitReceiverTester> {
    return when (orientation) {
      Orientation.HostToGuest -> {
        val (sender, receiver) = Stream<Unit>()
        RealUnitSenderTester(sender) to createHostGuest(receiver)
      }

      Orientation.GuestToHost -> {
        val [sender, receiver] = createGuestHost()
        sender to RealUnitReceiverTester(receiver)
      }

      Orientation.HostToHost -> {
        val (sender, receiver) = Stream<Unit>()
        RealUnitSenderTester(sender) to RealUnitReceiverTester(receiver)
      }
    }
  }
}
