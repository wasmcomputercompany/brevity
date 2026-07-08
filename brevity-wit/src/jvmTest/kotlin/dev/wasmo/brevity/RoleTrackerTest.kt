package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.io.toServiceName
import dev.wasmo.brevity.ir.TypeNameDeclared
import kotlin.test.Test
import okio.Path.Companion.toPath

class RoleTrackerTest {

  @Test
  fun happyPath() {
    val tester = BrevityTester(
      "testing.wit".toPath() to """
        |package namespace:package-name;
        |
        |interface monotonic-clock {
        |  now: func() -> s64;
        |}
        |
        |world test {
        |  resource person {
        |    get-name: func() -> string;
        |  }
        |
        |  type track = string;
        |
        |  record album {
        |    name: string,
        |    tracks: list<track>,
        |  }
        |
        |  record measurement {
        |    type: string,
        |    amount: f64,
        |  }
        |
        |  import monotonic-clock;
        |
        |  export greet: func(recipient: person);
        |
        |  export analyze: func(album: album) -> list<measurement>;
        |}
        """.trimMargin(),
    )

    assertThat(tester.roleTracker["namespace:package-name/monotonic-clock".toServiceName()])
      .isEqualTo(
        RoleTracker.Entry(
          host = true,
          guest = false,
        ),
      )

    assertThat(tester.roleTracker[TypeNameDeclared("namespace:package-name/test", "person")])
      .isEqualTo(
        RoleTracker.Entry(
          host = true,
          guest = false,
        ),
      )

    assertThat(tester.roleTracker[TypeNameDeclared("namespace:package-name/test", "track")])
      .isEqualTo(
        RoleTracker.Entry(
          host = true,
          guest = false,
        ),
      )

    assertThat(tester.roleTracker[TypeNameDeclared("namespace:package-name/test", "album")])
      .isEqualTo(
        RoleTracker.Entry(
          host = true,
          guest = false,
        ),
      )

    assertThat(tester.roleTracker[TypeNameDeclared("namespace:package-name/test", "measurement")])
      .isEqualTo(
        RoleTracker.Entry(
          host = false,
          guest = true,
        ),
      )
  }
}
