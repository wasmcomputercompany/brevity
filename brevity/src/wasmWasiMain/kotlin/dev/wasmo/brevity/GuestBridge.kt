@file:OptIn(
  UnsafeWasmMemoryApi::class,
  BrevityInternalApi::class,
)

package dev.wasmo.brevity

import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@BrevityInternalApi
object GuestBridge {
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private var nextRep = 4_040_000

  private var taskResult: Any? = null

  val brevityDispatcher = BrevityDispatcher()

  private val brevityScope: CoroutineScope
    get() = brevityDispatcher.scope

  // Prevent kotlinx-coroutines DefaultExecutor from registering a hook when exported functions
  // exit. That hook is particularly problematic for `cabi_realloc`, because it prevents that
  // function from being bridged.
  init {
    kotlin.wasm.internal.onExportedFunctionExit = {}
  }

  fun <T : Resource> putExportedResource(resource: T): Int {
    val rep = nextRep++
    repToExportedResource[rep] = resource
    return rep
  }

  fun <T : Resource> getExportedResource(rep: Int): T {
    val resource = repToExportedResource[rep] ?: error("unexpected rep: $rep")
    return resource as T
  }

  fun <T : Resource> putImportedResource(resource: T): Int {
    val rep = nextRep++
    repToImportedResource[rep] = resource
    return rep
  }

  fun <T : Resource> getImportedResource(rep: Int, newHandle: () -> T): T {
    val existing = repToImportedResource[rep]
    if (existing == null) {
      val result = newHandle()
      result.rep = rep
      result.hasRep = true
      return result
    }
    return existing as T
  }

  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }

  fun launchTask(block: suspend () -> Unit): PackedAsyncResult {
    brevityScope.launch {
      block()
    }
    brevityDispatcher.runUntilIdle()
    return PackedAsyncResult(CallbackCode.Exit)
  }

  fun resumeTask(eventCode: Int, p1: Int, p2: Int): PackedAsyncResult {
    return PackedAsyncResult(CallbackCode.Exit)
  }

  suspend fun <T> awaitTaskResult(): T {
    return taskResult as T
  }
}

@BrevityInternalApi
fun Pointer.loadPointer(): Pointer {
  return Pointer(loadInt().toUInt())
}

@BrevityInternalApi
fun Pointer.loadString(byteCount: Int): String {
  return loadByteArray(byteCount).decodeToString()
}

@BrevityInternalApi
fun Pointer.loadByteArray(byteCount: Int): ByteArray {
  val result = ByteArray(byteCount)
  for (i in 0 until byteCount) {
    result[i] = (this + i).loadByte()
  }
  return result
}

@BrevityInternalApi
fun Pointer.storeString(value: String) {
  val byteArray = value.encodeToByteArray()
  storeByteArray(byteArray)
}

@BrevityInternalApi
fun Pointer.storeByteArray(value: ByteArray) {
  for ((i, element) in value.withIndex()) {
    (this + i).storeByte(element)
  }
}
