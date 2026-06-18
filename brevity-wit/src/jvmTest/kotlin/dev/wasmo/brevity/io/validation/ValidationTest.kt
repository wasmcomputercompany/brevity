package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.assertFailsWith
import okio.Path
import okio.Path.Companion.toPath
import org.junit.Test

class ValidationTest {
  @Test
  fun producesPackageNameMapWhenSuccessful() {
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = emptyMap()
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      offset = Offset(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other.wit".toPath() to IoWitFile(
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

  @Test
  fun throwsOnCollision() {
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = emptyMap()
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:cli".toPackageName(),
      offset = Offset(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other.wit".toPath() to IoWitFile(
          items = listOf(
            inlinePackage
          )
        )
      )
    )
    val exception = assertFailsWith<Exception> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo("Duplicate package definitions for wasi:cli")
  }
}
