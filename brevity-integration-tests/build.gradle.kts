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
        implementation(projects.brevity)
        implementation(projects.wasi.brevityWasi)
        implementation(projects.wasi.brevityWasiP1)
        implementation(projects.wasi.brevityWasiP2)
        implementation(projects.wasi.brevityWasiP3)
      }
    }
    jvmMain {
      dependencies {
        implementation(libs.chicory.runtime)
        implementation(libs.chicory.wabt)
        implementation(libs.kotlinpoet)
        implementation(libs.okhttp)
        implementation(libs.okio)
        implementation(projects.brevityWit)
        implementation(projects.brevityKotlinGenerator)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.burst.coroutines)
        implementation(libs.okio.fakefilesystem)
      }
    }
  }
}

brevity {
  generateKotlin {
    worlds.add("wasmo:testing/wasmo-testing")
    inputWitPackageDirectories.from(
      File(projectDir, "src/commonMain/wit"),
    )
  }
}

val publishTestingArtifacts = tasks.register("publishToMavenLocal", Exec::class.java) {
  description = "Publish the library to ~/.m2/repository for testing"
  workingDir = rootDir
  commandLine(
    "${rootDir}/gradlew",
    "-Pbrevity.version=0-testing",
    "-Pbrevity.build.directory=build/publish-for-tests",
    "publishAllPublicationsToMavenLocalRepository",
  )
}

val compileDevelopmentExecutableKotlinWasmWasi = tasks.named("compileDevelopmentExecutableKotlinWasmWasi")

tasks.named("jvmTest") {
  // Required by RunKotlinWasmTest.
  dependsOn(compileDevelopmentExecutableKotlinWasmWasi)

  // Required by BridgeEveryTypeTest.
  dependsOn(publishTestingArtifacts)
}
