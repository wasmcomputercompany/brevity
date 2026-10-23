@file:OptIn(
  UnsafeWasmMemoryApi::class,
  BrevityInternalApi::class,
)

package dev.wasmo.brevity

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@BrevityInternalApi
object GuestBridge {
  private val typeToResourceHandle = mutableMapOf<KType, (Int) -> Resource>()
  private val idToResource = mutableMapOf<Int, Resource>()
  private var nextId = 4_040_000

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

  inline fun <reified T : Resource> registerResourceHandle(
    noinline constructor: (id: Int) -> T,
  ) {
    registerResourceHandle(typeOf<T>(), constructor)
  }

  @PublishedApi
  internal fun registerResourceHandle(type: KType, createHandle: (id: Int) -> Resource) {
    typeToResourceHandle[type] = createHandle
  }

  fun <T : Resource> toIndex(resource: T): Int {
    val id = nextId++
    idToResource[id] = resource
    return id
  }

  inline fun <reified T : Resource> fromIndex(index: Int): T {
    return fromIndex(typeOf<T>(), index) as T
  }

  @PublishedApi
  internal fun fromIndex(type: KType, index: Int): Resource? {
//    val createHandle = typeToResourceHandle[type]
//    if (createHandle != null) return createHandle(index)
    return idToResource[index]
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
