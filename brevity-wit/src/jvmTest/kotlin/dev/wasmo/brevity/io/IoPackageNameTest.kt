package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier.Companion.Identifier
import dev.wasmo.brevity.IoPackageName
import kotlin.test.Test

class IoPackageNameTest {
  @Test
  fun `PackageName toString`() {
    assertThat(
      IoPackageName(
        namespaces = listOf(Identifier("abc"), Identifier("def"), Identifier("ghi")),
        names = listOf(Identifier("jkl"), Identifier("mno"), Identifier("pqr")),
      ).toString()
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr")
  }
}
