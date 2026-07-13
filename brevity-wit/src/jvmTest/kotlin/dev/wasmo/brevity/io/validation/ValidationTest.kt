package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitMultiplySitedException
import dev.wasmo.brevity.WitMultiplySitedException.Location
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath
import org.junit.Test

class ValidationTest {
  @Test
  fun producesPackageNameMapWhenSuccessful() {
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

  @Test
  fun throwsOnCollision() {
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = mapOf(
        "cli.wit".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
        )
      )
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:cli".toPackageName(),
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
    val exception = assertFailsWith<WitMultiplySitedException> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo("""
      |Duplicate definitions of wasi:cli
      |${"\t"}at cli.wit:0:0
      |${"\t"}at other/other.wit:1:2""".trimMargin())

    assertThat(exception.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", Offset(1, 2)),
      Location("cli.wit", Offset(0, 0))
    )
  }

  @Test
  fun throwsMultipleCollisions() {
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = mapOf(
        "cli.wit".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
        )
      )
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:cli".toPackageName(),
      offset = Offset(1, 2),
      declarations = emptyList(),
    )
    val anotherInlinePackage = IoInlinePackage(
      packageName = "wasi:other".toPackageName(),
      offset = Offset(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other/other.wit".toPath() to IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
            anotherInlinePackage,
          )
        )
      )
    )
    val exception = assertFailsWith<WitCompoundException> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo("""
      |Multiple issues found:
      |Duplicate definitions of wasi:cli
      |${"\t"}at cli.wit:0:0
      |${"\t"}at other/other.wit:1:2
      |Duplicate definitions of wasi:other
      |${"\t"}at other/other.wit:0:0
      |${"\t"}at other/other.wit:1:2
      |""".trimMargin())

    val (firstException, secondException) = exception.witExceptions.filterIsInstance<WitMultiplySitedException>()

    assertThat(firstException.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", Offset(1, 2)),
      Location("cli.wit", Offset(0, 0))
    )
    assertThat(secondException.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", Offset(1, 2)),
      Location("other/other.wit", Offset(0, 0))
    )
  }
}
