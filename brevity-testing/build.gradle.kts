plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("dev.wasmo.brevity-build")
}

brevityBuild {
  library(jvm = true)
}

kotlin {
  sourceSets {
    jvmMain {
      dependencies {
        implementation(libs.kotlinpoet)
        implementation(libs.okio)
        implementation(libs.okio.fakefilesystem)
        implementation(projects.brevity)
        implementation(projects.brevityWit)
        implementation(projects.brevityKotlinGenerator)
      }
    }
  }
}
