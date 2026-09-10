package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.SemVer
import kotlin.test.Test

class PackageNameTest {
  @Test
  fun `PackageName toString`() {
    assertThat(
      PackageName(
        namespaces = listOf(Identifier("abc")),
        names = listOf(Identifier("def")),
      ).toString(),
    ).isEqualTo("abc:def")

    assertThat(
      PackageName(
        namespaces = listOf(Identifier("abc"), Identifier("def"), Identifier("ghi")),
        names = listOf(Identifier("jkl"), Identifier("mno"), Identifier("pqr")),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr")

    assertThat(
      PackageName(
        namespaces = listOf(Identifier("abc"), Identifier("def"), Identifier("ghi")),
        names = listOf(Identifier("jkl"), Identifier("mno"), Identifier("pqr")),
        version = SemVer("1.2.3"),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr@1.2.3")
  }
}

