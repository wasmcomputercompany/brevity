@file:OptIn(ExperimentalContracts::class)

package dev.wasmo.brevity

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class IssueCollector internal constructor(
  issues: MutableList<Issue> = mutableListOf(),
  val locationStack: List<Location> = emptyList(),
) {
  private val _issues = issues
  val issues: List<Issue> = _issues

  fun report(issue: Issue) {
    _issues += issue.copy(locationStack = locationStack)
  }

  fun throwIfNotEmpty() {
    // Note that this clears the current issues list to avoid double-reporting if this exception
    // is later caught and rethrown.
    if (issues.isNotEmpty()) {
      throw WitCompoundException(issues.map { WitException(it) })
        .also { _issues.clear() }
    }
  }

  internal fun pushIssueLocation(location: Location) =
    IssueCollector(_issues, listOf(location).plus(locationStack))
}

context(issueCollector: IssueCollector)
fun <T> pushIssueLocation(location: Location, block: IssueCollector.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = issueCollector.pushIssueLocation(location)
  return try {
    issueCollector.block()
  } catch (e: WitCompoundException) {
    val issues = e.witExceptions
      .map { it.issue }
      .map { it.copy(locationStack = it.locationStack + issueCollector.locationStack )}

    throw WitCompoundException(issues.map { WitException(it) })
  }
}

/**
 * Convenience function to run a block of code and throw if any issues are raised by it.
 */
fun <T> collectNoIssuesOrThrow(block: IssueCollector.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = IssueCollector()
  val result = with(issueCollector) {
    try {
      block()
    } catch (e: WitCompoundException) {
      val nestedIssues = e.witExceptions.map { it.issue }

      throw WitCompoundException(
        (issueCollector.issues + nestedIssues)
          .map { WitException(it) },
      )
    }
  }

  issueCollector.throwIfNotEmpty()

  return result
}

/**
 * Run a block, returning the result and any issues raised.
 */
fun <T> collectIssues(block: IssueCollector.() -> T): Pair<T, List<Issue>> {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = IssueCollector()
  val result = with(issueCollector) {
    try {
      block()
    } catch (e: WitCompoundException) {
      val nestedIssues = e.witExceptions.map { it.issue }
      throw WitCompoundException(
        (issues + nestedIssues).map { WitException(it) },
      )
    }
  }

  return result to issueCollector.issues
}
