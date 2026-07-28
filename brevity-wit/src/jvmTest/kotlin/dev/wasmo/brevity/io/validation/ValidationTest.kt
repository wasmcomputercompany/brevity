package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitMultiplySitedException
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.assertFailsWith
import org.junit.Test

class ValidationTest {
  @Test
  fun producesPackageNameMapWhenSuccessful() {
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          location = Location("cli.wit"),
        ),
      ),
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      location = Location("file.wit", 1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
          ),
          location = Location("other/other.wit"),
        ),
      ),
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
      files = listOf(
        IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          location = Location("cli.wit"),
        ),
      ),
    )
    val otherLocation = Location("other/other.wit")
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            IoInlinePackage(
              packageName = "wasi:cli".toPackageName(),
              location = otherLocation.at(1, 2),
              declarations = emptyList(),
            ),
          ),
          location = otherLocation,
        ),
      ),
    )
    val exception = assertFailsWith<WitMultiplySitedException> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo(
      """
      |Duplicate definitions of wasi:cli
      |${"\t"}at cli.wit
      |${"\t"}at other/other.wit:1:2
      """.trimMargin(),
    )

    assertThat(exception.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", 1, 2),
      Location("cli.wit"),
    )
  }

  @Test
  fun throwsMultipleCollisions() {
    val otherLocation = Location("other/other.wit")
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          location = Location("cli.wit"),
        ),
      ),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            IoInlinePackage(
              packageName = "wasi:cli".toPackageName(),
              location = otherLocation.at(1, 2),
              declarations = emptyList(),
            ),
            IoInlinePackage(
              packageName = "wasi:other".toPackageName(),
              location = otherLocation.at(1, 2),
              declarations = emptyList(),
            ),
          ),
          location = otherLocation,
        ),
      ),
    )
    val exception = assertFailsWith<WitCompoundException> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo(
      """
      |Multiple issues found:
      |Duplicate definitions of wasi:cli
      |${"\t"}at cli.wit
      |${"\t"}at other/other.wit:1:2
      |Duplicate definitions of wasi:other
      |${"\t"}at other/other.wit
      |${"\t"}at other/other.wit:1:2
      |""".trimMargin(),
    )

    val (firstException, secondException) = exception.witExceptions.filterIsInstance<WitMultiplySitedException>()

    assertThat(firstException.locations).containsExactlyInAnyOrder(
      Location("cli.wit"),
      Location("other/other.wit", 1, 2),
    )
    assertThat(secondException.locations).containsExactlyInAnyOrder(
      Location("other/other.wit"),
      Location("other/other.wit", 1, 2),
    )
  }
}
