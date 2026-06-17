plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("brevity-build")
}

brevityBuild {
  library(jvm = true, wasm = true)
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
