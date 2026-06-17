package dev.wasmo.brevity.gradle

interface BrevityBuildExtension {
  fun library(
    jvm: Boolean = false,
    js: Boolean = false,
    wasm: Boolean = false,
    publish: Boolean = false,
  )
}
