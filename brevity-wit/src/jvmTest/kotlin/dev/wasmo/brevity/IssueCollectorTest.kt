package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlin.test.assertFailsWith
import org.junit.Test

class IssueCollectorTest {
  @Test
  fun `throws WitCompoundException with proper stack when issues are reported`() {
    val location = Location("file.wit")
    val issue1 = Issue("oop!", location)
    val issue2 = Issue("ahrp", location.at(100, 200))
    val exception = assertFailsWith< WitCompoundException> {
      collectNoIssuesOrThrow {
        pushIssueLocation(location.at(1, 2)) {
          pushIssueLocation(location.at(5, 6)) {
            report(issue1)
          }

          report(issue2)
        }
      }
    }

    assertThat(exception.witExceptions.map { it.issue } ).containsExactly(
      issue1.copy(
        locationStack = listOf(
          location.at(5, 6),
          location.at(1, 2),
        )
      ),
      issue2.copy(
        locationStack = listOf(
          location.at(1, 2),
        )
      ),
    )
  }

  @Test
  fun `nested collectNoIssuesOrThrow composes correctly`() {
    val location = Location("file.wit")
    val issue1 = Issue("oop!", location)
    val issue2 = Issue("ahrp", location.at(100, 200))
    val exception = assertFailsWith< WitCompoundException> {
      collectNoIssuesOrThrow {
        pushIssueLocation(location.at(1, 2)) {
          report(issue1)
          collectNoIssuesOrThrow {
            pushIssueLocation(location.at(5, 6)) {
              report(issue2)
            }
          }
        }
      }
    }

    assertThat(exception.witExceptions.map { it.issue } ).containsExactly(
      issue1.copy(
        locationStack = listOf(
          location.at(1, 2),
        )
      ),
      issue2.copy(
        locationStack = listOf(
          location.at(5, 6),
          location.at(1, 2),
        )
      ),
    )
  }

  @Test
  fun `collectIssues issues get included in collectNoIssuesOrThrow output`() {
    val location = Location("file.wit")
    val issue1 = Issue("oop!", location)
    val issue2 = Issue("ahrp", location.at(100, 200))
    val exception = assertFailsWith< WitCompoundException> {
      collectIssues {
        pushIssueLocation(location.at(1, 2)) {
          report(issue1)
          collectNoIssuesOrThrow {
            pushIssueLocation(location.at(5, 6)) {
              report(issue2)
            }
          }
        }
      }
    }

    assertThat(exception.witExceptions.map { it.issue } ).containsExactly(
      issue1.copy(
        locationStack = listOf(
          location.at(1, 2),
        )
      ),
      issue2.copy(
        locationStack = listOf(
          location.at(5, 6),
          location.at(1, 2),
        )
      ),
    )
  }
}
