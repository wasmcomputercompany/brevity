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
    jvmMain {
      dependencies {
        implementation(libs.okio)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.okio.fakefilesystem)
        implementation(projects.brevityTesting)
      }
    }
  }
}
