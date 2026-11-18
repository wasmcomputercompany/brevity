plugins {
  id("org.jetbrains.kotlin.multiplatform")
  alias(libs.plugins.burst)
  id("dev.wasmo.brevity-build")
}

brevityBuild {
  library(jvm = true)
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.okio)
        implementation(projects.brevityKotlinGenerator)
        implementation(projects.brevityWit)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.burst.coroutines)
      }
    }
  }
}

// The Kotlin Toolchain compiles the generated projects against brevity published to the local test
// repository.
val testRepository = rootProject.layout.buildDirectory.dir("localMaven")
val publishTestingArtifacts = listOf(
  ":brevity",
  ":wasi:brevity-wasi",
  ":wasi:brevity-wasi-p1",
  ":wasi:brevity-wasi-p2",
  ":wasi:brevity-wasi-p3",
).map { "$it:publishAllPublicationsToTestRepository" }

tasks.named<Test>("jvmTest") {
  dependsOn(publishTestingArtifacts)
  inputs.files(fileTree(testRepository) { include("**/*.jar", "**/*.klib") })
  systemProperty("brevity.testRepository", testRepository.get().asFile.absolutePath)

  // Surface test output such as the phase timings to gradle/ci.
  testLogging.showStandardStreams = true
}
