package dev.wasmo.brevity

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class IssueCollector {
  private val _issues = mutableListOf<Issue>()
  val issues: List<Issue> = _issues

  fun report(issue: Issue) {
    _issues += issue
  }

  fun throwIfNotEmpty() {
    if (issues.isNotEmpty()) {
      throw WitCompoundException(
        issues.map { WitException(it) }
      )
    }

  }
}

/**
 * Convenience function to run a block of code and throw if any issues are raised by it.
 */
@OptIn(ExperimentalContracts::class)
fun <T> withIssueCollector(block: IssueCollector.()->T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = IssueCollector()
  val result = with(issueCollector) {
    block()
  }

  issueCollector.throwIfNotEmpty()

  return result
}
