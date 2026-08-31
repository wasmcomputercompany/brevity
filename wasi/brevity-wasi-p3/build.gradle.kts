plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("dev.wasmo.brevity-build")
  id("dev.wasmo.brevity")
}

brevityBuild {
  library(jvm = true, wasm = true)
  publish()
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.okio)
        api(projects.brevity)
      }
    }
  }
}

brevity {
  generateKotlin {
    worlds.add("wasi:http/service@0.3.1")
    inputWitPackageDirectories.from(
      File(project.rootDir, "submodules/wasi-p3/proposals/cli/wit"),
      File(project.rootDir, "submodules/wasi-p3/proposals/clocks/wit"),
      File(project.rootDir, "submodules/wasi-p3/proposals/filesystem/wit"),
      File(project.rootDir, "submodules/wasi-p3/proposals/http/wit"),
      File(project.rootDir, "submodules/wasi-p3/proposals/random/wit"),
      File(project.rootDir, "submodules/wasi-p3/proposals/sockets/wit"),
    )
  }
}
