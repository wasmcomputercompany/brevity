plugins {
  id("org.jetbrains.kotlin.multiplatform")
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
        api(projects.brevity)
      }
    }
  }
}

brevity {
  generateKotlin {
    inputWitPackageDirectories.from(
      File(project.rootDir, "submodules/WASI/proposals/cli/wit"),
      File(project.rootDir, "submodules/WASI/proposals/clocks/wit"),
      File(project.rootDir, "submodules/WASI/proposals/filesystem/wit"),
      File(project.rootDir, "submodules/WASI/proposals/http/wit"),
      File(project.rootDir, "submodules/WASI/proposals/random/wit"),
      File(project.rootDir, "submodules/WASI/proposals/sockets/wit"),
    )
  }
}
