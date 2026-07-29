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

class ValidateUniquePackageNamesTest {
  @Test
  fun producesPackageNameMapWhenSuccessful() {
    val cliLocation = Location("")
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = cliLocation,
          packageName = "wasi:cli".toPackageName(),
        ),
      ),
    )
    val otherLocation = Location("other/other.wit")
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          location = otherLocation,
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
          ),
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
    val cliLocation = Location("cli.wit", 1, 1)
    val cliExtraLocation = Location("cli-extra.wit", 1, 1)
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = cliLocation,
          packageName = "wasi:cli".toPackageName(),
        ),
        IoWitFile(
          location = cliExtraLocation,
          packageName = "wasi:cli".toPackageName(),
        ),
      ),
    )
    val otherLocation = Location("other/other.wit")
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:cli".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          location = otherLocation,
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
          ),
        ),
      ),
    )
    val exception = assertFailsWith<WitException> {
      validateUniquePackageNames(listOf(cliPackage, otherPackage))
    }

    assertThat(exception.message).isEqualTo(
      """
      |Duplicate definitions of wasi:cli
      |${"\t"}at cli.wit:1:1
      |${"\t"}at cli-extra.wit:1:1
      |${"\t"}at other/other.wit:1:2""".trimMargin(),
    )

    assertThat(exception.issue.locations).containsExactlyInAnyOrder(
      otherLocation.at(1, 2),
      cliLocation,
      cliExtraLocation,
    )
  }

  @Test
  fun throwsMultipleCollisions() {
    val cliLocation = Location("cli.wit")
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = cliLocation,
          packageName = "wasi:cli".toPackageName(),
        ),
      ),
    )
    val otherLocation = Location("other/other.wit")
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:cli".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = emptyList(),
    )
    val anotherInlinePackage = IoInlinePackage(
      packageName = "wasi:other".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = emptyList(),
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          location = otherLocation,
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
            anotherInlinePackage,
          ),
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
      otherLocation.at(1, 2),
      cliLocation,
    )
    assertThat(secondException.issue.locations).containsExactlyInAnyOrder(
      otherLocation.at(1, 2),
      otherLocation,
    )
  }
}
