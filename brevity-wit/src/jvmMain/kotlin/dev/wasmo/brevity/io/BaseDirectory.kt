package dev.wasmo.brevity.io

import okio.Path

/**
 * Returns a directory to resolve `.wit` paths against for display. We want this to be long enough
 * so users can find the originating files, but not so long that they leak details of the CI
 * environment in which they're produced.
 *
 * Sample values:
 *  * `cli/command.wit`
 *  * `cli/wit/environment.wit`
 */
val Path.baseDirectory : Path
  get() {
    var current = this
    while (true) {
      if (current.name != "wit") return current.parent ?: current
      current = current.parent ?: return current
    }
  }
