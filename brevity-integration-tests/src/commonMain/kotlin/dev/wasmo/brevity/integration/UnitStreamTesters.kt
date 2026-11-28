package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Stream
import dev.wasmo.brevity.receiveUnits
import dev.wasmo.brevity.sendUnits
import wit.wasmo.testing.UnitStreams.UnitReceiverTester
import wit.wasmo.testing.UnitStreams.UnitSenderTester

internal class RealUnitSenderTester(
  private val sender: Stream.Sender<Unit>,
) : UnitSenderTester() {
  override suspend fun sendSome(count: Int, fast: Boolean): Int {
    return when {
      fast -> sender.sendUnits(count)
      else -> sender.sendSome(List(count) { Unit })
    }
  }
}

internal class RealUnitReceiverTester(
  private val receiver: Stream.Receiver<Unit>,
) : UnitReceiverTester() {
  override suspend fun receive(count: Int, fast: Boolean): Int {
    return when {
      fast -> receiver.receiveUnits(count)
      else -> receiver.receive(count).size
    }
  }
}
