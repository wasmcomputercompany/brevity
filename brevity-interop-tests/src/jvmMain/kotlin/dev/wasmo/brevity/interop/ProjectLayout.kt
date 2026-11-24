package dev.wasmo.brevity.interop

import okio.Path

class ProjectLayout(
  /** The Kotlin Toolchain project and Cargo workspace shared by all testers. */
  val root: Path,
  val name: String,
) {
  val path: Path
    get() = root / name

  val brevityWit: Path
    get() = path / "wit"
  val rustWit: Path
    get() = path / "rust" / "wit"

  val brevity: Path
    get() = path / "$name-brevity"
  val brevitySrc: Path
    get() = brevity / "src"
  val brevitySrcJvm: Path
    get() = brevity / "src@jvm"
  val brevitySrcWasmWasi: Path
    get() = brevity / "src@wasmWasi"

  val guest: Path
    get() = path / "$name-guest"
  val guestSrc: Path
    get() = guest / "src"

  val host: Path
    get() = path / "$name-host"
  val hostSrc: Path
    get() = host / "src"

  val rust: Path
    get() = path / "rust"
  val rustSrc: Path
    get() = rust / "src"
}
