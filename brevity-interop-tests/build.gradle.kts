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

// The Kotlin Toolchain rebuilds when a dependency's mtime changes, every publish writes them.
// Sync a copy so artifactw with unchanged bytes keep their mtimes.
val syncedTestRepository = layout.buildDirectory.dir("testRepository")
val syncTestRepository = tasks.register<Sync>("syncTestRepository") {
  dependsOn(publishTestingArtifacts)
  from(testRepository)
  into(syncedTestRepository)
  exclude("**/maven-metadata.xml*") // Rewritten with a timestamp on every publish.
}

tasks.named<Test>("jvmTest") {
  inputs.files(syncTestRepository)
  systemProperty("brevity.testRepository", syncedTestRepository.get().asFile.absolutePath)

  // Surface test output such as the phase timings to gradle/ci.
  testLogging.showStandardStreams = true
}
