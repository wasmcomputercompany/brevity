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
        implementation(libs.kotlinpoet)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.okio)
        implementation(projects.brevityWit)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.okio.fakefilesystem)
        implementation(projects.brevityTesting)
      }
    }
  }
}
