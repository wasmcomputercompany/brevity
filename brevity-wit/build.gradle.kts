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
    jvmMain {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.okio)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.okio.fakefilesystem)
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.brevityTesting)
      }
    }
  }
}
