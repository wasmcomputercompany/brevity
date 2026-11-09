package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@BrevityInternalApi
class HostBridge(
  private val coroutineScope: CoroutineScope,
) {
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  private var nextRep = 6_060_000
  internal var initialStackPointer: Int = 0
  internal var initialTlsBase: Int = 0

  lateinit var memory: Memory
    private set

  lateinit var memoryAllocator: MemoryAllocator
    private set

  /** Thread used when an async task isn't currently running. */
  val defaultThread = Thread(this, Task())

  /** The currently-executed thread, which implies the current task. */
  var currentThread: Thread = defaultThread

  fun init(symbolIndex: SymbolIndex, instance: Instance) {
    // A .wasm doesn't have to declare a memory, but you can't do much without it. If the memory is
    // absent, leave the var uninitialized, and assume nothing will access it.
    val instanceMemory = instance.memory()
    if (instanceMemory != null) {
      this.memory = instanceMemory
      this.memoryAllocator = MemoryAllocator(
        cabiRealloc = instance.export("cabi_realloc"),
      )
    }

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

  @BrevityInternalApi
  inline fun <reified T : Resource> drop(id: Int): T {
    return dropInternal(id) as T
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
        val rep = nextRep++
        val subtask = Subtask(job)
        subtask.rep = rep
        subtask.hasRep = true
        repToImportedResource[rep] = subtask
        PackedSubtaskResult(SubtaskState.Started, rep)
      }

      else -> PackedSubtaskResult(SubtaskState.Returned)
    }
  }

  fun waitableSetNew(): WaitableSet = RealWaitableSet(this)

  fun taskCancel() {
    TODO("Not yet implemented")
  }

  fun subtaskCancel(subtask: Int): Int {
    TODO("Not yet implemented")
  }

  fun subtaskDrop(subtask: Int) {
    // TODO("Not yet implemented")
  }
}
