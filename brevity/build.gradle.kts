plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("dev.wasmo.brevity-build")
}

brevityBuild {
  library(jvm = true, wasm = true)
  publish()
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.okio)
      }
    }
    val jvmMain by getting {
      dependencies {
        api(libs.chicory.runtime)
      }
    }
  }
}
