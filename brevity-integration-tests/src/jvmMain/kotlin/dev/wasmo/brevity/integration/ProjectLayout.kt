package dev.wasmo.brevity.integration

import okio.Path

class ProjectLayout(
  val path: Path,
) {
  val wit: Path
    get() = path / "wit"

  val api: Path
    get() = path / "api"
  val apiSrc: Path
    get() = api / "src"

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
