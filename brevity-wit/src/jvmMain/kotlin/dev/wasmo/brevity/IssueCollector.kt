@file:OptIn(ExperimentalContracts::class)

package dev.wasmo.brevity

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class IssueCollector internal constructor(
  issues: MutableList<Issue> = mutableListOf(),
) {
  private val _issues = issues
  val issues: List<Issue> = _issues

  fun report(issue: Issue) {
    _issues += issue
  }

  fun throwIfNotEmpty() {
    if (issues.isNotEmpty()) {
      // The collectIssue APIs all stitch together issues across the stack
      // in try/catch blocks, so our issues will actually be packaged up by our
      // caller instead of us here.
      throw WitCompoundException(emptyList())
    }
  }
}

context(issueCollector: IssueCollector)
fun <T> pushIssueLocation(location: Location, block: IssueCollector.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val innerCollector = IssueCollector()
  return try {
    innerCollector.block().also {
      innerCollector.issues.map {
        // On success, copy any collected issues within the scope to the enclosing
        // collector
        issueCollector.report(it.copy(
          locationStack = it.locationStack + location
        ))
      }
    }
  } catch (e: WitCompoundException) {
    // Outer issues will be handled by an enclosing block, but issues in the exception
    // and issues in our collector need the location added to them
    val thrownIssues = e.witExceptions.map { it.issue }
    val innerIssues = innerCollector.issues

    throw WitCompoundException(
      (innerIssues + thrownIssues)
        .map { it.copy(locationStack = it.locationStack + location) }
        .map { WitException(it) })
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
  return with(issueCollector) {
    try {
      block().also {
        issueCollector.throwIfNotEmpty()
      }
    } catch (e: WitCompoundException) {
      val nestedIssues = e.witExceptions.map { it.issue }

      throw WitCompoundException(
        (issueCollector.issues + nestedIssues)
          .map { WitException(it) },
      )
    }
  }
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
