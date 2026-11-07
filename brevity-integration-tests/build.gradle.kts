@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  alias(libs.plugins.burst)
  id("dev.wasmo.brevity-build")
  id("dev.wasmo.brevity")
}

brevityBuild {
  wasmExecutable()
  library(jvm = true, wasm = true)
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlinx.coroutines.core)
        implementation(projects.brevity)
        implementation(projects.wasi.brevityWasiP3)
      }
    }
    jvmMain {
      dependencies {
        implementation(libs.chicory.runtime)
        implementation(libs.chicory.wabt)
        implementation(libs.kotlinpoet)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.okhttp)
        implementation(libs.okio)
        implementation(projects.brevityKotlinGenerator)
        implementation(projects.brevityWit)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.burst.coroutines)
        implementation(libs.okio.fakefilesystem)
        implementation(projects.wasi.brevityWasi)
        implementation(projects.wasi.brevityWasiP1)
        implementation(projects.wasi.brevityWasiP2)
      }
    }
  }
}

brevity {
  worlds.add("wasmo:testing/wasmo-testing")
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

val compileDevelopmentExecutableKotlinWasmWasi =
  tasks.named("compileDevelopmentExecutableKotlinWasmWasi")

tasks.named<Test>("jvmTest") {
  // Required by RunKotlinWasmTest.
  dependsOn(compileDevelopmentExecutableKotlinWasmWasi)

  // Required for the BrevityExecutionTester.
  dependsOn(publishTestingArtifacts)
  inputs.files(fileTree(testRepository) { include("**/*.jar", "**/*.klib") })
  systemProperty("brevity.testRepository", testRepository.get().asFile.absolutePath)

  // Surface test output such as the phase timings to gradle/ci.
  testLogging.showStandardStreams = true
}
