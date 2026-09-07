package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier.Companion.Identifier
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.SemVer
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import kotlin.test.Test

class TypeNameTest {
  @Test
  fun `TypeName toString`() {
    assertThat(
      TypeName.Declared(
        serviceName = ServiceName(
          PackageName(
            namespaces = listOf(Identifier("abc").constrain()),
            names = listOf(Identifier("def").constrain()),
          ),
          name = Identifier("ghi").constrain(),
        ),
        name = Identifier("jkl").constrain(),
      ).toString(),
    ).isEqualTo("abc:def/ghi.jkl")

    assertThat(
      TypeName.Declared(
        serviceName = ServiceName(
          PackageName(
            namespaces = listOf(Identifier("abc").constrain()),
            names = listOf(Identifier("def").constrain()),
            version = SemVer("1.2.3"),
          ),
          name = Identifier("ghi").constrain(),
        ),
        name = Identifier("jkl").constrain(),
      ).toString(),
    ).isEqualTo("abc:def/ghi.jkl@1.2.3")
  }
}
