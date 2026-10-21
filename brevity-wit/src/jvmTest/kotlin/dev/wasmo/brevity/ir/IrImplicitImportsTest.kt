package dev.wasmo.brevity.ir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IrMapper
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test

class IrImplicitImportsTest {
  @Test
  fun `referenced interface is implicitly imported`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  export database;
          |}
          |
          |interface errors {
          |  type error = string;
          |  report: func(error: error);
          |}
          |
          |interface database {
          |  use errors.{error};
          |  commit: func() -> option<error>;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(irWorld.exports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactly(ServiceName("namespace:name", "database"))
    assertThat(irWorld.imports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactly(ServiceName("namespace:name", "errors"))
  }

  @Test
  fun `transitively referenced interface is implicitly imported`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  export database;
          |}
          |
          |interface errors {
          |  type error = string;
          |  report: func(error: error);
          |}
          |
          |interface utilities {
          |  type error-list = list<error>;
          |  use errors.{error};
          |}
          |
          |interface database {
          |  use utilities.{error-list};
          |  commit: func() -> option<error-list>;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(irWorld.exports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactly(ServiceName("namespace:name", "database"))
    assertThat(irWorld.imports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactlyInAnyOrder(
        ServiceName("namespace:name", "errors"),
        ServiceName("namespace:name", "utilities"),
      )
  }

  @Test
  fun `referenced interface is not imported if already exported`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  export errors;
          |  export database;
          |}
          |
          |interface errors {
          |  type error = string;
          |  report: func(error: error);
          |}
          |
          |interface database {
          |  use errors.{error};
          |  commit: func() -> option<error>;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(irWorld.exports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactlyInAnyOrder(
        ServiceName("namespace:name", "errors"),
        ServiceName("namespace:name", "database"),
      )
    assertThat(irWorld.imports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .isEmpty()
  }

  @Test
  fun `unreferenced interface is not imported`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  export database;
          |}
          |
          |interface errors {
          |  report: func(error: string);
          |}
          |
          |interface database {
          |  commit: func() -> option<string>;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(irWorld.exports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactly(ServiceName("namespace:name", "database"))
    assertThat(irWorld.imports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .isEmpty()
  }

  @Test
  fun `use is sufficient to implicitly import`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  use errors.{error};
          |}
          |
          |interface errors {
          |  type error = string;
          |  report: func(error: string);
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(irWorld.exports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .isEmpty()
    assertThat(irWorld.imports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactly(ServiceName("namespace:name", "errors"))
  }

  @Test
  fun `interface can be imported and exported`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  import errors;
          |  export errors;
          |  export database;
          |}
          |
          |interface errors {
          |  type error = string;
          |  report: func(error: error);
          |}
          |
          |interface database {
          |  use errors.{error};
          |  commit: func() -> option<error>;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(irWorld.exports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactlyInAnyOrder(
        ServiceName("namespace:name", "errors"),
        ServiceName("namespace:name", "database"),
      )
    assertThat(irWorld.imports.filterIsInstance<IrExternalApi>().map { it.serviceName })
      .containsExactly(ServiceName("namespace:name", "errors"))
  }

  @Test
  fun `referenced interface is implicitly imported if also named`() = collectNoIssuesOrThrow {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "namespace:name".toPackageName(),
        files = listOf(
          """
          |package namespace:name;
          |
          |world test-world {
          |  import problems: errors;
          |  export database;
          |}
          |
          |interface errors {
          |  type error = string;
          |  report: func(error: error);
          |}
          |
          |interface database {
          |  use errors.{error};
          |  commit: func() -> option<error>;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irWorld = irPackage.services.filterIsInstance<IrWorld>().single()
    assertThat(
      irWorld.exports.filterIsInstance<IrExternalApi>().map { it.plainName to it.serviceName },
    ).containsExactly(
      null to ServiceName("namespace:name", "database"),
    )
    assertThat(
      irWorld.imports.filterIsInstance<IrExternalApi>().map { it.plainName to it.serviceName },
    ).containsExactlyInAnyOrder(
      Identifier("problems") to ServiceName("namespace:name", "errors"),
      null to ServiceName("namespace:name", "errors"),
    )
  }
}
