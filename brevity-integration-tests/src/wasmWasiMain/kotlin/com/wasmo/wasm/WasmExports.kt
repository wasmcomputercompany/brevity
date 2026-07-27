package com.wasmo.wasm

import wit.wasi.cli.v0_2_0.Command
import wit.wasi.cli.v0_2_0.guest

/*
 * The libraries we depend on include @WasmExport-annotated functions that export these symbols, but
 * unfortunately Kotlin/Wasm doesn't expose such functions unless they're in the root module.
 */

// Equivalent to the generated function wit.wasi.cli.v0_2_0.run_run_export()
@WasmExport("wasi:cli/run@0.2.0#run")
private fun run_run_export(): Int {
  val result = Command.guest.run.run(
  )
  return (result as Int)
}
