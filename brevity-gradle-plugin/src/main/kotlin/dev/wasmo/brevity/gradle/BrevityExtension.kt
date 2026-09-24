package dev.wasmo.brevity.gradle

import org.gradle.api.Action
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

interface BrevityExtension {
  /**
   * Path to a config.toml file.
   *
   * wkg will use its default if this is omitted.
   *
   * Special note: wkg interprets paths within this config as relative to the working
   * dir where wkg is run, which will be build/brevity/sourceWit. Take heed!
   */
  val config: RegularFileProperty

  /** A set of wit package names, e.g. wasi:cli@5.0.3 */
  val ociPackages: ListProperty<String>

  /** World names like 'command', 'wasi:cli/command', or 'wasi:cli/command@0.3.0' */
  val worlds: ListProperty<String>

  /**
   * Maps WIT types to Kotlin types. The two type names must be fully-qualified like
   * `wasi:clocks/types.duration@0.3.1` to `kotin.time.Duration`.
   */
  val customTypeMappings: MapProperty<String, String>

  fun publish(action: Action<in BrevityPublishExtension>)

  interface BrevityPublishExtension {
    /** True if publishing a workspace, false if not. */
    val isWorkspace: Property<Boolean>
  }
}
