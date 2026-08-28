plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("dev.wasmo.brevity-build")
}

brevityBuild {
  library(jvm = true)
  publish()
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.okio)
        api(projects.brevity)
        compileOnly(projects.wasi.brevityWasiP1)
        compileOnly(projects.wasi.brevityWasiP2)
        compileOnly(projects.wasi.brevityWasiP3)
      }
    }
  }
}
