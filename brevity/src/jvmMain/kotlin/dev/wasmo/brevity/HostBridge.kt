package dev.wasmo.brevity

import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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

  val defaultThread = DefaultThread()

  /** The currently-executing thread. */
  var currentThread: Thread = defaultThread

  /** The currently-executing task. */
  val currentTask: Task<*>
    get() = (currentThread as TaskThread).task

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
    this.defaultThread.stackPointer = initialStackPointer

    this.initialTlsBase = symbolIndex.initialTlsBase(instance)
    this.defaultThread.tlsBase = initialTlsBase
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
    return suspendCancellableCoroutine { continuation ->
      val thread = TaskThread(
        bridge = this,
        task = Task(continuation),
      ).apply {
        this.stackPointer = initialStackPointer
        this.tlsBase = initialTlsBase
      }

      coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
        thread.execute(
          execute = execute,
          callback = callback,
        )
      }
    }
  }

  fun launchSubtask(block: suspend () -> Unit): PackedSubtaskResult {
    val subtask = Subtask(this)
    return subtask.start(coroutineScope, block)
  }

  fun taskCancel() {
    TODO("Not yet implemented")
  }

  fun subtaskCancel(subtask: Int): Int {
    TODO("Not yet implemented")
  }

  fun subtaskDrop(subtask: Int) {
    // TODO("Not yet implemented")
  }

  inline fun <T> executeThread(
    thread: Thread,
    block: () -> T,
  ): T {
    check(currentThread == defaultThread)
    currentThread = thread
    try {
      return block()
    } finally {
      currentThread = defaultThread
    }
  }
}
