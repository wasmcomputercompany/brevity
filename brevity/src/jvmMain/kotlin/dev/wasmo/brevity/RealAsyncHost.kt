package dev.wasmo.brevity

@BrevityInternalApi
class RealAsyncHost : Async.Host {
  override fun waitableSetNew(): WaitableSet {
    TODO("Not yet implemented")
  }

  override fun <T> contextGet(index: Int, typeId: TypeId<T>): T {
    TODO("Not yet implemented")
  }

  override fun <T> contextSet(index: Int, typeId: TypeId<T>, value: T) {
    TODO("Not yet implemented")
  }

  override fun taskCancel() {
    TODO("Not yet implemented")
  }
}
