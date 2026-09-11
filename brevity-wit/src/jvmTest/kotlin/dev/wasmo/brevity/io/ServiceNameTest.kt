package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.SemVer
import dev.wasmo.brevity.ServiceName
import kotlin.test.Test

class ServiceNameTest {
  @Test
  fun `ServiceName toString`() {
    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(Identifier("abc")),
          names = listOf(Identifier("def")),
        ),
        name = Identifier("ghi"),
      ).toString(),
    ).isEqualTo("abc:def/ghi")

    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(Identifier("abc")),
          names = listOf(Identifier("def")),
          version = SemVer("1.2.3"),
        ),
        name = Identifier("ghi"),
      ).toString(),
    ).isEqualTo("abc:def/ghi@1.2.3")

    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(Identifier("abc"), Identifier("def"), Identifier("ghi")),
          names = listOf(Identifier("jkl"), Identifier("mno"), Identifier("pqr")),
        ),
        Identifier(name = "stu"),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr/stu")

    assertThat(
      ServiceName(
        PackageName(
          namespaces = listOf(Identifier("abc"), Identifier("def"), Identifier("ghi")),
          names = listOf(Identifier("jkl"), Identifier("mno"), Identifier("pqr")),
          version = SemVer("1.2.3"),
        ),
        name = Identifier("stu"),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr/stu@1.2.3")
  }
}
