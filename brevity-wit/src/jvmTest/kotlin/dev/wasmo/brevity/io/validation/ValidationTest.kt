package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitException
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
      location = Location("file.wit").at(1, 2),
      declarations = emptyList(),
    )
    val otherLocation = Location("other/other.wit")
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
          ),
          location = otherLocation,
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
    val cliLocation = Location("cli.wit")
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          location = cliLocation,
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
    val exception = assertFailsWith<WitException> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo(
      """
      |Duplicate definitions of wasi:cli
      |${"\t"}at cli.wit
      |${"\t"}at other/other.wit:1:2
      """.trimMargin(),
    )

    assertThat(exception.issue.locations).containsExactlyInAnyOrder(
      otherLocation.at(1, 2),
      cliLocation,
    )
  }

  @Test
  fun throwsMultipleCollisions() {
    val cliLocation = Location("cli.wit")
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          location = cliLocation,
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

    val (firstException, secondException) = exception.witExceptions.filterIsInstance<WitException>()

    assertThat(firstException.issue.locations).containsExactlyInAnyOrder(
      cliLocation,
      otherLocation.at(1, 2),
    )
    assertThat(secondException.issue.locations).containsExactlyInAnyOrder(
      otherLocation,
      otherLocation.at(1, 2),
    )
  }
}
