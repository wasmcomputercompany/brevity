package wit.wasi.v0_1

sealed interface Subscription {
  val userdata: ULong

  data class Clock(
    override val userdata: ULong,
    val clockId: ClockId,
    val timeout: ULong,
    val precision: ULong,
    val absTime: Boolean,
  ) : Subscription

  data class Read(
    override val userdata: ULong,
  ) : Subscription

  data class Write(
    override val userdata: ULong,
  ) : Subscription
}

data class Event(
  val userdata: ULong,
  val errno: Errno,
  val subscription: Subscription,
  val nbytes: ULong = 0UL,
  val flags: UShort = 0.toUShort(),
)
