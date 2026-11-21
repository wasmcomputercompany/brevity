@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(BrevityInternalApi::class)

package dev.wasmo.brevity

@BrevityInternalApi
abstract class StreamBinding<T> {
  abstract val alignment: Int
  abstract val byteCount: Int
  abstract fun store(memoryAllocator: MemoryAllocator, address: Int, value: T)
  abstract fun load(address: Int): T
}
