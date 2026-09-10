package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
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
            namespaces = listOf(Identifier("abc")),
            names = listOf(Identifier("def")),
          ),
          name = Identifier("ghi"),
        ),
        name = Identifier("jkl"),
      ).toString(),
    ).isEqualTo("abc:def/ghi.jkl")

    assertThat(
      TypeName.Declared(
        serviceName = ServiceName(
          PackageName(
            namespaces = listOf(Identifier("abc")),
            names = listOf(Identifier("def")),
            version = SemVer("1.2.3"),
          ),
          name = Identifier("ghi"),
        ),
        name = Identifier("jkl"),
      ).toString(),
    ).isEqualTo("abc:def/ghi.jkl@1.2.3")
  }
}
