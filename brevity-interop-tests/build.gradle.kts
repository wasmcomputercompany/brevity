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
// repository. It rebuilds when a dependency's mtime changes and every publish rewrites them, so
// the tests read a synced copy where artifacts with unchanged bytes keep their mtimes.
val publishTestingArtifacts = listOf(
  ":brevity",
  ":wasi:brevity-wasi",
  ":wasi:brevity-wasi-p1",
  ":wasi:brevity-wasi-p2",
  ":wasi:brevity-wasi-p3",
).map { "$it:publishAllPublicationsToTestRepository" }

val syncTestRepository = tasks.register<Sync>("syncTestRepository") {
  dependsOn(publishTestingArtifacts)
  from(rootProject.layout.buildDirectory.dir("localMaven"))
  into(layout.buildDirectory.dir("testRepository"))
  exclude("**/maven-metadata.xml*") // Rewritten with a timestamp on every publish.
}

tasks.named<Test>("jvmTest") {
  inputs.files(syncTestRepository)

  // Surface test output such as the phase timings to gradle/ci.
  testLogging.showStandardStreams = true
}
