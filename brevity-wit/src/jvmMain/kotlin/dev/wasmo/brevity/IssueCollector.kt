@file:OptIn(ExperimentalContracts::class)

package dev.wasmo.brevity

import dev.wasmo.brevity.IssueCollector.ThrowIfEmptyThrown
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class IssueCollector(
  issues: MutableList<Issue> = mutableListOf(),
) {
  class ThrowIfEmptyThrown internal constructor(private val issueCollector: IssueCollector): Exception() {
    fun throwIssues(location: Location? = null): Nothing =
      throw WitCompoundException(issueCollector.issues.map {
        WitException(it.copy(locationStack = if (location != null) {
          it.locationStack + location
        } else {
          it.locationStack
        }
        ))
      })
  }

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
      throw ThrowIfEmptyThrown(this)
    }
  }
}

context(issueCollector: IssueCollector)
inline fun <T> pushIssueLocation(location: Location, crossinline block: IssueCollector.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val innerCollector = IssueCollector()
  return try {
    innerCollector.block().also {
      for (issue in innerCollector.issues) {
        // On success, copy any collected issues within the scope to the enclosing
        // collector
        issueCollector.report(
          issue.copy(
            locationStack = issue.locationStack + location
          )
        )
      }
    }
  } catch (e: ThrowIfEmptyThrown) {
    e.throwIssues(location)
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
inline fun <T> collectNoIssuesOrThrow(crossinline block: IssueCollector.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = IssueCollector()
  return with(issueCollector) {
    try {
      block().also {
        issueCollector.throwIfNotEmpty()
      }
    } catch (e: ThrowIfEmptyThrown) {
      e.throwIssues()
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
inline fun <T> collectIssues(crossinline block: IssueCollector.() -> T): Pair<T, List<Issue>> {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  val issueCollector = IssueCollector()
  val result = with(issueCollector) {
    try {
      block()
    } catch (e: ThrowIfEmptyThrown) {
      e.throwIssues()
    } catch (e: WitCompoundException) {
      val nestedIssues = e.witExceptions.map { it.issue }
      throw WitCompoundException(
        (issues + nestedIssues).map { WitException(it) },
      )
    }
  }

  return result to issueCollector.issues
}
