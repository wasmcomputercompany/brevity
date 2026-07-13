package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsOnly
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.toPackageName
import okio.Path.Companion.toPath
import org.junit.Test

class ValidateUniqueServiceNamesTest {
  @Test
  fun producesServiceNameMapWhenSuccessful() {
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = mapOf(
        "".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
        )
      )
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      offset = Offset(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other/other.wit".toPath() to IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage
          )
        )
      )
    )
    val packages = listOf(cliPackage, otherPackage)

    val map = validateUniquePackageNames(packages)

    assertThat(map).containsOnly(
      "wasi:cli".toPackageName() to cliPackage,
      "wasi:inline".toPackageName() to inlinePackage,
      "wasi:other".toPackageName() to otherPackage,
    )
  }

}
