plugins {
  id("org.jetbrains.kotlin.multiplatform")
  alias(libs.plugins.burst)
  id("dev.wasmo.brevity-build")
  id("dev.wasmo.brevity")
}

brevityBuild {
  library(jvm = true, wasm = true)
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(projects.brevity)
      }
    }
    jvmMain {
      dependencies {
        implementation(libs.okio)
        implementation(libs.chicory.runtime)
        implementation(libs.chicory.wabt)
        implementation(libs.okhttp)
        implementation(libs.okio)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.burst.coroutines)
        implementation(libs.okio.fakefilesystem)
      }
    }
  }
}

brevity {
  generateKotlin {
    inputWitPackageDirectories.from(
      File(projectDir, "src/commonMain/wit"),
    )
  }
}

val rustCargoBuild = tasks.register("rustCargoBuild", Exec::class.java) {
  group = "rust"
  description = "Generate .wasm components from Rust sources"
  workingDir = File(projectDir, "rust")
  commandLine(
    "${environment["HOME"]}/.cargo/bin/cargo", "build",
    "--target=wasm32-wasip2",
    "--release",
  )
}

val rustComponentUnbundle = tasks.register("rustComponentUnbundle", Exec::class.java) {
  group = "rust"
  dependsOn(rustCargoBuild)
  description = "Unbundle the .wasm component into a .wasm core module"
  workingDir = File(projectDir, "rust")
  commandLine(
    "${environment["HOME"]}/.cargo/bin/wasm-tools", "component", "unbundle",
    "--module-dir", "target/unbundled/",
    "--output", "target/unbundled/component.wasm",
    "./target/wasm32-wasip2/release/wasmo_testing.wasm",
  )
}

// Required by RunKotlinWasmTest.
val compileDevelopmentExecutableKotlinWasmWasi = tasks.named("compileDevelopmentExecutableKotlinWasmWasi")
tasks.named("jvmTest") {
  dependsOn(compileDevelopmentExecutableKotlinWasmWasi)
  dependsOn(rustComponentUnbundle)
}
