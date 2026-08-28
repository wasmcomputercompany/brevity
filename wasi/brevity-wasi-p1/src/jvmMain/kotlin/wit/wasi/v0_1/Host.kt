package wit.wasi.v0_1

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Store
import com.dylibso.chicory.runtime.WasmFunctionHandle
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType
import dev.wasmo.brevity.World
import okio.Buffer

fun Wasi.World(
  hostFactory: (guest: Wasi.Guest) -> Wasi.Host,
): World<Wasi.Host, Wasi.Guest> {
  val guest = BridgeWasi.BridgeGuest()
  val host = hostFactory(guest)
  return BridgeWasi(guest, host)
}

internal class BridgeWasi(
  override val guest: Wasi.Guest,
  override val host: Wasi.Host,
) : World<Wasi.Host, Wasi.Guest> {
  override fun initExports(instance: Instance) {
  }

  override fun initImports(store: Store) {
    store.addFunction(
      HostFunction(
        "wasi_snapshot_preview1",
        "random_get",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          error("unexpected call")
        },
      ),
    )
    store.addFunction(
      HostFunction(
        "wasi_snapshot_preview1",
        "poll_oneoff",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          error("unexpected call")
        },
      ),
    )
    store.addFunction(
      HostFunction(
        "wasi_snapshot_preview1",
        "clock_time_get",
        FunctionType.of(
          listOf(ValType.I32, ValType.I64, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          error("unexpected call")
        },
      ),
    )
    store.addFunction(
      HostFunction(
        "wasi_snapshot_preview1",
        "proc_exit",
        FunctionType.of(
          listOf(ValType.I32),
          listOf(),
        ),
        WasmFunctionHandle { instance, args ->
          error("unexpected call")
        },
      ),
    )
    store.addFunction(
      HostFunction(
        "wasi_snapshot_preview1",
        "fd_write",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          error("unexpected call")
        },
      ),
    )
    store.addFunction(
      HostFunction(
        "wasi_snapshot_preview1",
        "fd_write",
        FunctionType.of(
          listOf(ValType.I32, ValType.I32, ValType.I32, ValType.I32),
          listOf(ValType.I32),
        ),
        WasmFunctionHandle { instance, args ->
          val result = fdWrite(
            instance = instance,
            fd = args[0].toInt(),
            iovs = args[1].toInt(),
            iovsSize = args[2].toInt(),
            returnPointer = args[3].toInt(),
          )
          longArrayOf(result.toLong())
        },
      ),
    )
  }

  private fun fdWrite(
    instance: Instance,
    fd: Int,
    iovs: Int,
    iovsSize: Int,
    returnPointer: Int,
  ): Int {
    val memory = instance.memory()
    val buffer = Buffer()

    var iovsAddress = iovs
    for (i in 0 until iovsSize) {
      val sliceAddress = memory.readInt(iovsAddress)
      iovsAddress += 4
      val sliceSize = memory.readInt(iovsAddress)
      iovsAddress += 4

      buffer.write(memory.readBytes(sliceAddress, sliceSize))
    }

    val size = buffer.size.toInt()
    val errno = host.write(fd, buffer)
    memory.writeI32(returnPointer, size)
    return errno.ordinal
  }

  internal class BridgeGuest() : Wasi.Guest {
  }
}
