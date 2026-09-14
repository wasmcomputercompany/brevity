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
    customTypeMappings.put("wasi:clocks/wall-clock.datetime@0.2.0", "kotlin.time.Instant")
    inputWitPackageNames.addAll(
      "wasi:cli@0.2.0",
      "wasi:clocks@0.2.0",
      "wasi:filesystem@0.2.0",
      "wasi:http@0.2.0",
      "wasi:io@0.2.0",
      "wasi:random@0.2.0",
      "wasi:sockets@0.2.0",
    )
  }
}
