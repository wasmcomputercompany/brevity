plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("dev.wasmo.brevity-build")
}

brevityBuild {
  library(jvm = true, wasm = true)
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
