package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.containsExactly
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MalformedIdentifierTest {

  /**
   * Confirm that we don't proceed to link if any identifiers are malformed. Without that check
   * this would incorrectly report duplicate symbols (because malformed symbols get replaced with
   * the same constant placeholder).
   */
  @Test
  fun `malformed identifiers in one interface`() {
    val location = Location("testing.wit")

    val e = assertFailsWith<WitCompoundException> {
      BrevityTester(
        location.path to """
          |package namespace:package-name;
          |
          |interface monotonic-clock {
          |  Now: func() -> s64;
          |  Later: func() -> s64;
          |}
          """.trimMargin(),
      )
    }

    assertThat(e.witExceptions.map { it.issue })
      .containsExactly(
        Issue("malformed identifier: Now", location.at(4, 3)),
        Issue("malformed identifier: Later", location.at(5, 3)),
      )
  }
}
