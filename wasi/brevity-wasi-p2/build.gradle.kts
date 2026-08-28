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
    worlds.add("wasi:cli/imports")
    worlds.add("wasi:http/proxy")
    inputWitPackageDirectories.from(
      File(project.rootDir, "submodules/wasi-p2/preview2/cli"),
      File(project.rootDir, "submodules/wasi-p2/preview2/clocks"),
      File(project.rootDir, "submodules/wasi-p2/preview2/filesystem"),
      File(project.rootDir, "submodules/wasi-p2/preview2/http"),
      File(project.rootDir, "submodules/wasi-p2/preview2/io"),
      File(project.rootDir, "submodules/wasi-p2/preview2/random"),
      File(project.rootDir, "submodules/wasi-p2/preview2/sockets"),
    )
  }
}
