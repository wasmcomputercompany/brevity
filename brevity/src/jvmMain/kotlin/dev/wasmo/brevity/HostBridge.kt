package dev.wasmo.brevity

import com.dylibso.chicory.runtime.ExportFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.wasm.InvalidException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@BrevityInternalApi
class HostBridge {
  private val repToExportedResource = mutableMapOf<Int, Resource>()
  private val repToImportedResource = mutableMapOf<Int, Resource>()
  private var nextTaskId = 1_000_000
  private var nextRep = 2_000_000
  internal var initialStackPointer: Int = 0
  internal var initialTlsBase: Int = 0

  lateinit var memory: Memory
    private set

  lateinit var memoryAllocator: MemoryAllocator
    private set

  private var wasmTaskHook: ExportFunction? = null

  val defaultThread = DefaultThread()

  /** The currently-executing thread. */
  var currentThread: Thread = defaultThread

  /** The currently-executing task. */
  val currentTask: RootTask<*>
    get() = (currentThread as TaskThread).task

  fun init(symbolIndex: SymbolIndex, instance: Instance) {
    wasmTaskHook = try {
      instance.exports().function("__wasm_task_hook")
    } catch (_: InvalidException) {
      null
    }

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
    name: String,
    execute: () -> PackedAsyncResult,
    callback: (Event) -> PackedAsyncResult,
  ): T {
    val coroutineContext = currentCoroutineContext()
    return suspendCancellableCoroutine { continuation ->
      val rootTask = RootTask(
        name = "$name ${nextTaskId++}",
        coroutineContext = coroutineContext,
        continuation = continuation,
      )
      val thread = TaskThread(
        bridge = this,
        task = rootTask,
      ).apply {
        this.stackPointer = initialStackPointer
        this.tlsBase = initialTlsBase
      }

      rootTask.scope.launch(
        start = CoroutineStart.UNDISPATCHED,
      ) {
        thread.execute(
          execute = execute,
          callback = callback,
        )
      }
    }
  }

  fun launchSubtask(block: suspend () -> Unit): PackedSubtaskResult {
    val subtask = Subtask(this, currentTask)
    return subtask.start(block)
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

  inline fun executeThread(
    thread: Thread,
    startLocation: WasmTaskHookLocation,
    block: () -> PackedAsyncResult,
  ): PackedAsyncResult {
    check(currentThread == defaultThread)
    currentThread = thread
    wasmTaskHook(startLocation)
    try {
      val result = block()
      wasmTaskHook(
        when (result.callbackCode) {
          CallbackCode.Exit -> WasmTaskHookLocation.AsyncLiftedFunctionEnd
          else -> WasmTaskHookLocation.AsyncLiftedFunctionSuspend
        },
      )
      return result
    } finally {
      currentThread = defaultThread
    }
  }

  /**
   * Invoke the `__wasm_task_hook` manually.
   *
   * https://github.com/bytecodealliance/wasm-tools/pull/2603
   *
   * This is necessary because we don't properly support components composed of multiple core
   * modules. When we can compose multiple components together, we can delete this, because it can
   * be wired in by shimming components.
   */
  fun wasmTaskHook(location: WasmTaskHookLocation) {
    val wasmTaskHook = this.wasmTaskHook ?: return
    wasmTaskHook.apply(location.ordinal.toLong())
  }
}
