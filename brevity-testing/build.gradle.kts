plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("brevity-build")
}

brevityBuild {
  library(jvm = true)
}

kotlin {
  sourceSets {
    jvmMain {
      dependencies {
        implementation(libs.okio)
        implementation(projects.brevity)
        implementation(projects.brevityWit)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.okio.fakefilesystem)
      }
    }
  }
}
