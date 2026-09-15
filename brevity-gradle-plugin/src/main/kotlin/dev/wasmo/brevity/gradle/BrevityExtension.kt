package dev.wasmo.brevity.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty

interface BrevityExtension {
  /** A set of wit package names, e.g. wasi:cli@5.0.3 */
  val ociPackages: ListProperty<String>

  /** World names like 'command', 'wasi:cli/command', or 'wasi:cli/command@0.3.0' */
  val worlds: ListProperty<String>

  /**
   * Maps WIT types to Kotlin types. The two type names must be fully-qualified like
   * `wasi:clocks/types.duration@0.3.1` to `kotin.time.Duration`.
   */
  val customTypeMappings: MapProperty<String, String>
}
