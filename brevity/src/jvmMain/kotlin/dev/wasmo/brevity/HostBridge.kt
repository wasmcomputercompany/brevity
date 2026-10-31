package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import dev.wasmo.brevity.RealAsyncHost.RealWaitableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@BrevityInternalApi
class HostBridge(
  private val coroutineScope: CoroutineScope,
) {
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  internal val waitables = mutableMapOf<Int, RealAsyncHost.Waitable>()
  private var nextRep = 6_060_000
  private var nextWaitableIndex = 8_080_000
  private lateinit var _memory: Memory
  internal var initialStackPointer: Int = 0
  internal var initialTlsBase: Int = 0

  val memory: Memory
    get() = _memory

  lateinit var memoryAllocator: MemoryAllocator
    private set

  /** Thread used when an async task isn't currently running. */
  val defaultThread = Thread(this, Task())

  /** The currently-executed thread, which implies the current task. */
  var currentThread: Thread = defaultThread

  fun init(symbolIndex: SymbolIndex, instance: Instance) {
    this._memory = instance.memory()
    this.memoryAllocator = MemoryAllocator(
      cabiRealloc = instance.export("cabi_realloc"),
    )
    this.initialStackPointer = symbolIndex.initialStackPointer(instance)
    this.defaultThread.contextSet(0, initialStackPointer)

    this.initialTlsBase = symbolIndex.initialTlsBase(instance)
    this.defaultThread.contextSet(1, initialTlsBase)
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

  fun <T : Resource> getImportedResource(rep: Int): T {
    val resource = repToImportedResource[rep] ?: error("unexpected rep: $rep")
    return resource as T
  }

  @PublishedApi
  internal fun dropInternal(rep: Int): Resource? {
    return repToExportedResource.remove(rep)
  }

  suspend fun <T> launchTask(
    execute: () -> PackedAsyncResult,
    callback: (Event) -> PackedAsyncResult,
  ): T {
    val thread = Thread(this, Task())

    var packedAsyncResult = thread.execute { execute() }

    while (true) {
      when (packedAsyncResult.callbackCode) {
        CallbackCode.Exit -> return thread.taskResult as T

        CallbackCode.Yield -> {
          yield()
          packedAsyncResult = thread.execute { callback(Event.None) }
        }

        CallbackCode.Wait -> {
          val waitableSet = getImportedResource<RealWaitableSet>(
            packedAsyncResult.waitableSetIndex,
          )
          val event = waitableSet.waitForEvent()
          packedAsyncResult = thread.execute { callback(event) }
        }

        CallbackCode.Max -> TODO()
      }
    }
  }

  fun launchSubtask(block: suspend () -> Unit): PackedSubtaskResult {
    val job = coroutineScope.launch {
      block()
    }

    return when {
      job.isCancelled -> PackedSubtaskResult(SubtaskState.CanceledBeforeReturned)

      !job.isCompleted -> {
        val waitableIndex = nextWaitableIndex++
        waitables[waitableIndex] = RealAsyncHost.Waitable(waitableIndex, job)
        PackedSubtaskResult(SubtaskState.Started, waitableIndex)
      }

      else -> PackedSubtaskResult(SubtaskState.Returned)
    }
  }
}

@BrevityInternalApi
class Thread(
  val bridge: HostBridge,
  val task: Task,
) {
  val context = intArrayOf(
    bridge.initialStackPointer,
    bridge.initialTlsBase,
  )
  var taskResult: Any? = null

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextget
   */
  fun contextGet(index: Int): Int {
    return context[index]
  }

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextset
   */
  fun contextSet(index: Int, value: Int) {
    context[index] = value
  }

  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }

  inline fun <T> execute(block: () -> T): T {
    check(bridge.currentThread == bridge.defaultThread)
    bridge.currentThread = this
    try {
      return block()
    } finally {
      bridge.currentThread = bridge.defaultThread
    }
  }
}

@BrevityInternalApi
class Task

@BrevityInternalApi
inline fun <reified T : Resource> HostBridge.drop(id: Int): T {
  return dropInternal(id) as T
}

@BrevityInternalApi
class MemoryAllocator internal constructor(
  private val cabiRealloc: ExportFunction,
) {
  /** Invoke `cabi_realloc(originalAddress, originalSize, align, newSize)`. */
  fun allocate(byteCount: Int): Int = cabiRealloc.apply(0L, 0L, 0L, byteCount.toLong())[0].toInt()
}
