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
  ociPackages.addAll(
    "wasi:cli@0.3.1",
    "wasi:clocks@0.3.1",
    "wasi:filesystem@0.3.1",
    "wasi:http@0.3.1",
    "wasi:random@0.3.1",
    "wasi:sockets@0.3.1",
  )
  worlds.add("wasi:http/service@0.3.1")
}
