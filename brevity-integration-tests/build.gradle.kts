plugins {
  id("org.jetbrains.kotlin.multiplatform")
  alias(libs.plugins.burst)
  id("dev.wasmo.brevity-build")
  id("dev.wasmo.brevity")
}

brevityBuild {
  wasmExecutable()
  library(jvm = true, wasm = true)
}

brevity {
  worlds.add("wasmo:testing/wasmo-testing")
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(projects.brevity)
        implementation(projects.wasi.brevityWasiP3)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.burst.coroutines)
        implementation(libs.chicory.runtime)
        implementation(libs.chicory.wabt)
        implementation(libs.okhttp)
        implementation(libs.okio)
        implementation(libs.okio.fakefilesystem)
        implementation(projects.wasi.brevityWasi)
        implementation(projects.wasi.brevityWasiP1)
        implementation(projects.wasi.brevityWasiP2)
      }
    }
  }
}

val compileDevelopmentExecutableKotlinWasmWasi =
  tasks.named("compileDevelopmentExecutableKotlinWasmWasi")

tasks.named<Test>("jvmTest") {
  dependsOn(compileDevelopmentExecutableKotlinWasmWasi)
}
