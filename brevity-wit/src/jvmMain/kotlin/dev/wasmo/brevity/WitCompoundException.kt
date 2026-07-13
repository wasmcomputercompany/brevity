package dev.wasmo.brevity

class WitCompoundException(
  val witExceptions: List<Exception>,
) : IllegalStateException(
  buildString {
    appendLine("Multiple issues found:")
    for (witException in witExceptions) {
      appendLine(witException.message)
    }
  }
)
