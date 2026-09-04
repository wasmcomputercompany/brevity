@file:OptIn(ExperimentalContracts::class)

package dev.wasmo.brevity

import kotlin.collections.plus
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Manages a shared set of issues, and provides interfaces for scoped collection of issues
 */
interface IssueRegistry {
  val all: List<Issue>

  /**
   * A sequence of contextual locations, ordered from innermost to outermost.
   */
  val locationStack: List<Location>
}

interface IssueCollector : IssueRegistry {
  fun report(issue: Issue)
}

/**
 * Singleton instance used to introduce [issues] into the namespace
 */
object IssueReceiver {
  context(issueCollector: IssueCollector)
  val issues: IssueCollector
    get() = issueCollector
}

/**
 * Constructs a fresh [IssueRegistry] with an empty issue list
 */
fun issues(): IssueRegistry = RealIssueCollector(
  _all = mutableListOf(),
  _scoped = mutableListOf(),
  locationStack = emptyList(),
  parent = null,
)

private class RealIssueCollector(
  val _all: MutableList<Issue>,
  val _scoped: MutableList<Issue>,
  override val locationStack: List<Location>,
  val parent: RealIssueCollector?,
) : IssueCollector {
  override val all: List<Issue> = _all

  val scoped: List<Issue> = _scoped

  override fun report(issue: Issue) {
    val localized = issue.copy(
      locationStack = issue.locationStack + locationStack,
    )
    _all += localized
    _scoped += localized
  }

  fun newChild(
    scoped: MutableList<Issue> = mutableListOf(),
    locationStack: List<Location> = this.locationStack,
  ) = RealIssueCollector(
    _all = _all,
    _scoped = scoped,
    locationStack = locationStack,
    parent = this,
  )
}

/**
 * If an issue is thrown within [block], upon completion [collectNoneOrThrow] will throw
 * an exception containing all issues.
 */
fun <T> IssueRegistry.collectNoneOrThrow(block: context(IssueCollector) IssueReceiver.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  require(this is RealIssueCollector) // only implementation

  val issueCollector = newChild()
  val result = with(issueCollector) {
    IssueReceiver.block()
  }

  if (all.isNotEmpty()) {
    throw WitCompoundException(
      all.map { WitException(it) }
    )
  }

  return result
}

/**
 * Collect issues within the given scope and return them, along with a return value
 */
fun <T> IssueRegistry.collect(block: context(IssueCollector) IssueReceiver.()->T): Pair<T, List<Issue>> {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  require(this is RealIssueCollector) // only implementation

  val issueCollector = newChild()
  val result = with(issueCollector) {
    IssueReceiver.block()
  }

  return result to issueCollector.scoped
}

/**
 * Push a location onto the front of [locationStack]
 *
 * Issues reported here are reported into the enclosing scope from [collectNoneOrThrow] or
 * [collect].
 */
fun <T> IssueRegistry.push(location: Location, block: context(IssueRegistry) IssueReceiver.() -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  require(this is RealIssueCollector) // only implementation

  val issueCollector = newChild(
    scoped = _scoped,
    locationStack = listOf(location) + locationStack,
  )
  val result = with(issueCollector) {
    IssueReceiver.block()
  }

  return result
}
