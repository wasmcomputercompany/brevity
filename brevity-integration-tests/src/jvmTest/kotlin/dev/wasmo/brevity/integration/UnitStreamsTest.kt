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
  private val orientation: Orientation = Orientation.HostToGuest,
) {
  @Test
  fun `send and receive equal sized small batches`(
    fastSend: Boolean = true,
    fastReceive: Boolean = false,
  ) = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val send3 = async {
      sender.sendSome(
        count = 3,
        fast = fastSend,
      )
    }
    val receive3 = receiver.receive(
      count = 3,
      fast = fastReceive,
    )
    assertThat(send3.await()).isEqualTo(3)
    assertThat(receive3).isEqualTo(3)

    receiver.close()
    sender.close()
  }

  @Test
  fun `send small batch and receive large batch`(
    fastSend: Boolean = true,
  ) = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val sendFew = async {
      sender.sendSome(
        count = 3,
        fast = fastSend,
      )
    }
    val receiveMany = receiver.receive(
      count = 268_435_455,
      fast = true,
    )
    assertThat(sendFew.await()).isEqualTo(3)
    assertThat(receiveMany).isEqualTo(3)

    receiver.close()
    sender.close()
  }

  @Test
  fun `send large batch and receive small batch`(
    fastReceive: Boolean = false,
  ) = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val sendMany = async {
      sender.sendSome(
        count = 268_435_455,
        fast = true,
      )
    }
    val receiveFew = receiver.receive(
      count = 3,
      fast = fastReceive,
    )
    assertThat(sendMany.await()).isEqualTo(3)
    assertThat(receiveFew).isEqualTo(3)

    receiver.close()
    sender.close()
  }

  @Test
  fun `send and receive large batch`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val sendMany = async {
      sender.sendSome(
        count = 268_435_455,
        fast = true,
      )
    }
    val receiveMany = receiver.receive(
      count = 268_435_455,
      fast = true,
    )
    assertThat(sendMany.await()).isEqualTo(268_435_455)
    assertThat(receiveMany).isEqualTo(268_435_455)

    receiver.close()
    sender.close()
  }

  @Test
  fun `two sends on a single receive`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val receive = async {
      receiver.receive(100_000_000, true)
    }
    val sends = async {
      val a = sender.sendSome(33_333_333, true)
      val b = sender.sendSome(44_444_444, true)
      return@async a + b
    }
    assertThat(receive.await()).isEqualTo(77_777_777)
    assertThat(sends.await()).isEqualTo(77_777_777)

    receiver.close()
    sender.close()
  }

  @Test
  fun `two receives on a single send`() = runTest {
    val tester = BrevityIntegrationTester()
    val [sender, receiver] = tester.world.guest.unitStreams.create()

    val send = async {
      sender.sendSome(100_000_000, true)
    }
    val receives = async {
      val a = receiver.receive(33_333_333, true)
      val b = receiver.receive(44_444_444, true)
      return@async a + b
    }
    assertThat(receives.await()).isEqualTo(77_777_777)
    assertThat(send.await()).isEqualTo(77_777_777)

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
