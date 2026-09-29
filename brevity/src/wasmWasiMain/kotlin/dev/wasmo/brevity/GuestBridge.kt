@file:OptIn(
  UnsafeWasmMemoryApi::class,
  BrevityInternalApi::class,
)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object GuestBridge {
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 4_040_000

  private var taskResult: Any? = null

  private val brevityDispatcher = BrevityDispatcher()
  private val brevityScope = CoroutineScope(brevityDispatcher)

  // Prevent kotlinx-coroutines DefaultExecutor from registering a hook when exported functions
  // exit. That hook is particularly problematic for `cabi_realloc`, because it prevents that
  // function from being bridged.
  init {
    kotlin.wasm.internal.onExportedFunctionExit = {}
  }

  fun <T : Resource> toId(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  fun <T : Resource> fromId(id: Int, constructor: (Int) -> T): T {
    return constructor(id)
  }

  @BrevityInternalApi
  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }

  @BrevityInternalApi
  fun launchTask(block: suspend () -> Unit): PackedAsyncResult {
    brevityScope.launch {
      block()
    }
    brevityDispatcher.runUntilIdle()
    return PackedAsyncResult(CallbackCode.Exit)
  }

  @BrevityInternalApi
  fun resumeTask(eventCode: Int, p1: Int, p2: Int): PackedAsyncResult {
    return PackedAsyncResult(CallbackCode.Exit)
  }

  @BrevityInternalApi
  suspend fun <T> awaitTaskResult(): T {
    return taskResult as T
  }
}

fun Pointer.loadPointer(): Pointer {
  return Pointer(loadInt().toUInt())
}

fun Pointer.loadString(byteCount: Int): String {
  return loadByteArray(byteCount).decodeToString()
}

fun Pointer.loadByteArray(byteCount: Int): ByteArray {
  val result = ByteArray(byteCount)
  for (i in 0 until byteCount) {
    result[i] = (this + i).loadByte()
  }
  return result
}

fun Pointer.storeString(value: String) {
  val byteArray = value.encodeToByteArray()
  storeByteArray(byteArray)
}

fun Pointer.storeByteArray(value: ByteArray) {
  for ((i, element) in value.withIndex()) {
    (this + i).storeByte(element)
  }
}
