package dev.wasmo.brevity

@BrevityInternalApi
class RealAsyncHost : Async.Host {
  val context = Array(2) { 0 }

  override fun waitableSetNew(): WaitableSet {
    TODO("Not yet implemented")
  }

  override fun <T> contextGet(index: Int, typeId: TypeId<T>): T {
    check(typeId == TypeId.I32) { "only i32 implemented: $typeId" }
    return context[index] as T
  }

  override fun <T> contextSet(index: Int, typeId: TypeId<T>, value: T) {
    check(typeId == TypeId.I32) { "only i32 implemented: $typeId" }
    context[index] = value as Int
  }

  override fun taskCancel() {
    TODO("Not yet implemented")
  }
}
