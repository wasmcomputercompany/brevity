package dev.wasmo.brevity.integration

import okio.Path

class ProjectLayout(
  val path: Path,
) {
  val brevityWit: Path
    get() = path / "wit"
  val rustWit: Path
    get() = path / "rust" / "wit"

  val brevity: Path
    get() = path / "brevity"
  val brevitySrc: Path
    get() = brevity / "src"
  val brevitySrcJvm: Path
    get() = brevity / "src@jvm"
  val brevitySrcWasmWasi: Path
    get() = brevity / "src@wasmWasi"

  val guest: Path
    get() = path / "guest"
  val guestSrc: Path
    get() = guest / "src"

  val host: Path
    get() = path / "host"
  val hostSrc: Path
    get() = host / "src"

  val rust: Path
    get() = path / "rust"
  val rustSrc: Path
    get() = rust / "src"
}
