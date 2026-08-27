@file:OptIn(ExperimentalContracts::class)

package dev.wasmo.brevity

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class IssueCollector private constructor(
  issues: MutableList<Issue>,
  val locationStack: List<Location>,
) {
  constructor(): this(issues = mutableListOf(), locationStack = emptyList())

  private val _issues = issues
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

  fun <T> pushIssueLocation(location: Location, block: IssueCollector.() -> T): T {
    contract {
      callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val issueCollector = IssueCollector(_issues, locationStack + location)
    return issueCollector.block()
  }
}

/**
 * Convenience function to run a block of code and throw if any issues are raised by it.
 */
fun <T> collectNoIssuesOrThrow(block: IssueCollector.()->T): T {
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

/**
 * Run a block, returning the result and any issues raised.
 */
fun <T> collectIssues(block: IssueCollector.()->T): Pair<T, List<Issue>> {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = IssueCollector()
  val result = with(issueCollector) {
    block()
  }

  return result to issueCollector.issues
}
