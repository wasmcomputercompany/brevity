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
        implementation(projects.brevityWasiP1)
        implementation(projects.brevityWasiP2)
      }
    }
    jvmMain {
      dependencies {
        implementation(libs.chicory.runtime)
        implementation(libs.chicory.wabt)
        implementation(libs.okhttp)
        implementation(libs.okio)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.burst.coroutines)
        implementation(libs.kotlinpoet)
        implementation(libs.okio.fakefilesystem)
        implementation(projects.brevityWit)
        implementation(projects.brevityKotlinGenerator)
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

val rustCargoBuild = tasks.register("rustCargoBuild", Exec::class.java) {
  group = "rust"
  description = "Generate .wasm components from Rust sources"
  workingDir = File(projectDir, "rust")
  commandLine(
    probeForCargoTool("cargo"), "build",
    "--target=wasm32-wasip2",
    "--release",
  )
}

val rustComponentUnbundle = tasks.register("rustComponentUnbundle", Exec::class.java) {
  group = "rust"
  dependsOn(rustCargoBuild)
  description = "Unbundle the .wasm component into a .wasm core module"
  workingDir = File(projectDir, "rust")
  commandLine(
    probeForCargoTool("wasm-tools"), "component", "unbundle",
    "--module-dir", "target/unbundled/",
    "--output", "target/unbundled/component.wasm",
    "./target/wasm32-wasip2/release/wasmo_testing.wasm",
  )
}

/**
 * For some inexplicable reason Java’s PATH isn’t resolving
 * certain Rust tools, so we do that manually.
 */
fun probeForCargoTool(tool: String): String {
  val paths = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
  return paths.map { File(it, tool) }
    .firstOrNull { it.canExecute() }
    ?.absolutePath
    ?: tool
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
  dependsOn(rustComponentUnbundle)

  // Required by BridgeEveryTypeTest.
  dependsOn(publishTestingArtifacts)
}
