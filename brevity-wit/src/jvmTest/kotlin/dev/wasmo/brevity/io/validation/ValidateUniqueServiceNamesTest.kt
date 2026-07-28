package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.io.toServiceName
import dev.wasmo.brevity.toPackageName
import kotlin.test.assertFailsWith
import org.junit.Test

class ValidateUniqueServiceNamesTest {
  @Test
  fun producesServiceNameMapWhenSuccessful() {
    val cliLocation = Location("")
    val cliInterface = IoInterface(
      location = cliLocation,
      name = "monotonic-clock",
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = cliLocation,
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        )
      )
    )
    val otherLocation = Location("other/other.wit")
    val inlineInterface = IoInterface(
      location = otherLocation.at(3, 4),
      name = "polytonic-clock",
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = listOf(inlineInterface),
    )
    val otherWorld = IoWorld(
      location = otherLocation.at(5, 6),
      name = "clocks",
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          location = otherLocation,
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
            otherWorld,
          )
        )
      )
    )
    val packages = listOf(cliPackage, otherPackage)

    val map = validateUniqueServiceNames(packages)

    assertThat(map).containsOnly(
      "wasi:cli/monotonic-clock".toServiceName() to cliInterface,
      "wasi:inline/polytonic-clock".toServiceName() to inlineInterface,
      "wasi:other/clocks".toServiceName() to otherWorld,
    )
  }

  @Test
  fun singleCollision() {
    val firstLocation = Location("first.wit")
    val secondLocation = Location("second.wit")
    val cliInterface = IoInterface(
      location = firstLocation.at(1, 2),
      name = "monotonic-clock",
    )
    val duplicateCliInterface = IoInterface(
      location = secondLocation.at(1, 3),
      name = "monotonic-clock",
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = firstLocation,
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        ),
        IoWitFile(
          location = secondLocation,
          packageName = "wasi:cli".toPackageName(),
          items = listOf(duplicateCliInterface),
        )
      )
    )
    val otherLocation = Location("other/other.wit")
    val inlineInterface = IoInterface(
      location = otherLocation.at(3, 4),
      name = "polytonic-clock",
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = listOf(inlineInterface),
    )
    val otherWorld = IoWorld(
      location = otherLocation.at(5, 6),
      name = "clocks",
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          location = otherLocation,
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
            otherWorld,
          )
        )
      )
    )
    val packages = listOf(cliPackage, otherPackage)

    val exception = assertFailsWith<WitException> {
      validateUniqueServiceNames(packages)
    }

    assertThat(exception.message).isEqualTo("""
      |Duplicate definitions of wasi:cli/monotonic-clock
      |${"\t"}at first.wit:1:2
      |${"\t"}at second.wit:1:3""".trimMargin())
  }

  @Test
  fun multipleCollisions() {
    val firstLocation = Location("first.wit")
    val secondLocation = Location("second.wit")
    val cliInterface = IoInterface(
      location = firstLocation.at(1, 2),
      name = "monotonic-clock",
    )
    val duplicateCliInterface = IoInterface(
      location = secondLocation.at(1, 3),
      name = "monotonic-clock",
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = firstLocation,
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        ),
        IoWitFile(
          location = secondLocation,
          packageName = "wasi:cli".toPackageName(),
          items = listOf(duplicateCliInterface),
        )
      )
    )
    val otherLocation = Location("other/other.wit")

    val inlineInterface = IoInterface(
      location = otherLocation.at(3, 4),
      name = "polytonic-clock",
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      location = otherLocation.at(1, 2),
      declarations = listOf(inlineInterface),
    )
    val otherWorld = IoWorld(
      location = otherLocation.at(5, 6),
      name = "clocks",
    )
    val duplicateOtherWorld = IoWorld(
      location = otherLocation.at(7, 8),
      name = "clocks",
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = listOf(
        IoWitFile(
          location = otherLocation,
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
            otherWorld,
            duplicateOtherWorld
          )
        )
      )
    )
    val packages = listOf(cliPackage, otherPackage)

    val exception = assertFailsWith<WitCompoundException> {
      validateUniqueServiceNames(packages)
    }

    val (firstException, secondException) = exception.witExceptions.filterIsInstance<WitException>()

    assertThat(firstException.issue.locations).containsExactlyInAnyOrder(
      Location("first.wit", 1, 2),
      Location("second.wit", 1, 3)
    )
    assertThat(secondException.issue.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", 5, 6),
      Location("other/other.wit", 7, 8)
    )
  }

}
