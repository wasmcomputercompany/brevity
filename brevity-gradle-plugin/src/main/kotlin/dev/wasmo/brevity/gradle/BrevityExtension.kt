package dev.wasmo.brevity.gradle

import org.gradle.api.Action

interface BrevityExtension {
  fun downloadOciWit(action: Action<DownloadOciWitTask>)
  fun generateKotlin(action: Action<BrevityTask>)
}
