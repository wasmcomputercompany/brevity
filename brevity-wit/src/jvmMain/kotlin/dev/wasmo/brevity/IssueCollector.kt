package dev.wasmo.brevity

class IssueCollector {
  private val _issues = mutableListOf<Issue>()
  val issues: List<Issue> = _issues

  fun report(issue: Issue) {
    _issues += issue
  }
}
