package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.WitCompoundException
import dev.wasmo.brevity.WitMultiplySitedException
import dev.wasmo.brevity.WitMultiplySitedException.Location
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.io.toServiceName
import dev.wasmo.brevity.toPackageName
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath
import org.junit.Test

class ValidateUniqueServiceNamesTest {
  @Test
  fun producesServiceNameMapWhenSuccessful() {
    val cliInterface = IoInterface(
      offset = Offset(1, 2),
      name = "monotonic-clock",
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = mapOf(
        "".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        )
      )
    )
    val inlineInterface = IoInterface(
      offset = Offset(3, 4),
      name = "polytonic-clock",
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      offset = Offset(1, 2),
      declarations = listOf(inlineInterface),
    )
    val otherWorld = IoWorld(
      offset = Offset(5, 6),
      name = "clocks",
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other/other.wit".toPath() to IoWitFile(
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
    val cliInterface = IoInterface(
      offset = Offset(1, 2),
      name = "monotonic-clock",
    )
    val duplicateCliInterface = IoInterface(
      offset = Offset(1, 3),
      name = "monotonic-clock",
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = mapOf(
        "first.wit".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        ),
        "second.wit".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          items = listOf(duplicateCliInterface),
        )
      )
    )
    val inlineInterface = IoInterface(
      offset = Offset(3, 4),
      name = "polytonic-clock",
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      offset = Offset(1, 2),
      declarations = listOf(inlineInterface),
    )
    val otherWorld = IoWorld(
      offset = Offset(5, 6),
      name = "clocks",
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other/other.wit".toPath() to IoWitFile(
          packageName = "wasi:other".toPackageName(),
          items = listOf(
            inlinePackage,
            otherWorld,
          )
        )
      )
    )
    val packages = listOf(cliPackage, otherPackage)

    val exception = assertFailsWith<WitMultiplySitedException> {
      validateUniqueServiceNames(packages)
    }

    assertThat(exception.message).isEqualTo("""
      |Duplicate definitions of wasi:cli/monotonic-clock
      |${"\t"}at first.wit:1:2
      |${"\t"}at second.wit:1:3""".trimMargin())
  }

  @Test
  fun multipleCollisions() {
    val cliInterface = IoInterface(
      offset = Offset(1, 2),
      name = "monotonic-clock",
    )
    val duplicateCliInterface = IoInterface(
      offset = Offset(1, 3),
      name = "monotonic-clock",
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = mapOf(
        "first.wit".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        ),
        "second.wit".toPath() to IoWitFile(
          packageName = "wasi:cli".toPackageName(),
          items = listOf(duplicateCliInterface),
        )
      )
    )
    val inlineInterface = IoInterface(
      offset = Offset(3, 4),
      name = "polytonic-clock",
    )
    val inlinePackage = IoInlinePackage(
      packageName = "wasi:inline".toPackageName(),
      offset = Offset(1, 2),
      declarations = listOf(inlineInterface),
    )
    val otherWorld = IoWorld(
      offset = Offset(5, 6),
      name = "clocks",
    )
    val duplicateOtherWorld = IoWorld(
      offset = Offset(7, 8),
      name = "clocks",
    )
    val otherPackage = IoToplevelWitPackage(
      packageName = "wasi:other".toPackageName(),
      files = mapOf(
        "other/other.wit".toPath() to IoWitFile(
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

    val (firstException, secondException) = exception.witExceptions.filterIsInstance<WitMultiplySitedException>()

    assertThat(firstException.locations).containsExactlyInAnyOrder(
      Location("first.wit", Offset(1, 2)),
      Location("second.wit", Offset(1, 3))
    )
    assertThat(secondException.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", Offset(5, 6)),
      Location("other/other.wit", Offset(7, 8))
    )
  }

}
