package dev.wasmo.brevity.integration

import assertk.assertThat
import assertk.assertions.containsExactly
import com.dylibso.chicory.wabt.Wat2Wasm
import com.dylibso.chicory.wasm.Parser
import dev.wasmo.brevity.WasmInstance
import kotlin.test.Test

class RunWatTest {
  @Test
  fun `run wat`() {
    val wasmInstance = WasmInstance(
      Parser.parse(
        Wat2Wasm.parse(
          """
          (module
            (func (export "addTwo") (param i32 i32) (result i32)
              local.get 0
              local.get 1
              i32.add
            )
          )
          """.trimIndent(),
        ),
      ),
      worlds = listOf(),
    )

    val addTwo = wasmInstance.instance.export("addTwo")
    val result = addTwo.apply(40, 2)
    assertThat(result).containsExactly(42L)
  }
}
