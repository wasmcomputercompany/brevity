package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.SemVer
import dev.wasmo.brevity.ServiceName
import kotlin.test.Test

class IoServiceNameTest {
  @Test
  fun `ServiceName toString`() {
    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(Identifier.Identifier("abc")),
          names = listOf(Identifier.Identifier("def")),
        ),
        name = Identifier.Identifier("ghi"),
      ).toString(),
    ).isEqualTo("abc:def/ghi")

    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(Identifier.Identifier("abc")),
          names = listOf(Identifier.Identifier("def")),
          version = SemVer("1.2.3"),
        ),
        name = Identifier.Identifier("ghi"),
      ).toString(),
    ).isEqualTo("abc:def/ghi@1.2.3")

    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(
            Identifier.Identifier("abc"),
            Identifier.Identifier("def"),
            Identifier.Identifier("ghi"),
          ),
          names = listOf(
            Identifier.Identifier("jkl"),
            Identifier.Identifier("mno"),
            Identifier.Identifier("pqr"),
          ),
        ),
        name = Identifier.Identifier("stu"),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr/stu")

    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(
            Identifier.Identifier("abc"),
            Identifier.Identifier("def"),
            Identifier.Identifier("ghi"),
          ),
          names = listOf(
            Identifier.Identifier("jkl"),
            Identifier.Identifier("mno"),
            Identifier.Identifier("pqr"),
          ),
          version = SemVer("1.2.3"),
        ),
        name = Identifier.Identifier("stu"),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr/stu@1.2.3")
  }
}
