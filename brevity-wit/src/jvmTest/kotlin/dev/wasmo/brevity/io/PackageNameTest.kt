package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.PackageName
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PackageNameTest {
  @Test
  fun `PackageName toString`() {
    assertThat(
      PackageName(
        namespaces = listOf(Identifier("abc"), Identifier("def"), Identifier("ghi")),
        names = listOf(Identifier("jkl"), Identifier("mno"), Identifier("pqr")),
      ).toString(),
    ).isEqualTo("abc:def:ghi:jkl/mno/pqr")
  }

  @Test
  fun `qualify local PackageName`() {
    assertThat(
      PackageName(
        namespaces = listOf(Identifier("local")),
        names = listOf(Identifier("curl")),
      ).qualify(
        PackageName(
          namespaces = listOf(Identifier("wasm")),
          names = listOf(Identifier("cli")),
        ),
      ),
    ).isEqualTo(
      PackageName(
        namespaces = listOf(Identifier("wasm")),
        names = listOf(Identifier("cli"), Identifier("curl"))
      )
    )
  }

  @Test
  fun `qualify global PackageName`() {
    assertThat(
      PackageName(
        namespaces = listOf(Identifier("wasm")),
        names = listOf(Identifier("cli")),
      )
      .qualify(
        PackageName(
          namespaces = listOf(Identifier("local")),
          names = listOf(Identifier("curl")),
        ),
      ),
    ).isEqualTo(
      PackageName(
        namespaces = listOf(Identifier("wasm")),
        names = listOf(Identifier("cli"))
      )
    )
  }

  @Test
  fun `qualify local PackageName with another local PackageName`() {
    val localPackageName = PackageName(
      namespaces = listOf(Identifier("local")),
      names = listOf(Identifier("curl")),
    )
    val e = assertFailsWith<IllegalArgumentException> {
      localPackageName.qualify(localPackageName)
    }
    assertThat(e.message).equals(
      "Cannot qualify using a local package name: $localPackageName"
    )
  }
}
