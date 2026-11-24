package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Stream
import wit.wasmo.testing.UnitStreams.UnitReceiverTester
import wit.wasmo.testing.UnitStreams.UnitSenderTester

internal class RealUnitSenderTester(
  private val sender: Stream.Sender<Unit>,
) : UnitSenderTester() {
  override suspend fun sendSome(count: Int, fast: Boolean): Int {
    return sender.sendSome(List(count) { Unit })
  }
}

internal class RealUnitReceiverTester(
  private val receiver: Stream.Receiver<Unit>,
) : UnitReceiverTester() {
  override suspend fun receive(count: Int, fast: Boolean): Int {
    return receiver.receive(count).size
  }
}
