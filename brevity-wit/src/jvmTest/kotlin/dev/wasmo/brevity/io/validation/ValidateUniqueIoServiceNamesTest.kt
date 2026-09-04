package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.io.IoFlag
import dev.wasmo.brevity.io.IoFlags
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.io.toServiceName
import dev.wasmo.brevity.toPackageName
import org.junit.Test

class ValidateUniqueIoServiceNamesTest {
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
        ),
      ),
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
          ),
        ),
      ),
    )
    val packages = listOf(cliPackage, otherPackage)
    val issueCollector = IssueCollector()

    val map = with(issueCollector) { validateUniqueServiceNames(packages) }
    assertThat(map).isNotNull()

    assertThat(map!!).containsOnly(
      "wasi:cli/monotonic-clock".toServiceName() to cliInterface,
      "wasi:inline/polytonic-clock".toServiceName() to inlineInterface,
      "wasi:other/clocks".toServiceName() to otherWorld,
    )

    assertThat(issueCollector.issues).isEmpty()
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
        ),
      ),
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
          ),
        ),
      ),
    )
    val packages = listOf(cliPackage, otherPackage)
    val issueCollector = IssueCollector()

    val result = with(issueCollector) { validateUniqueServiceNames(packages) }
    assertThat(result).isNull()

    assertThat(issueCollector.issues.single()).isEqualTo(
      Issue(
        "Duplicate definitions of wasi:cli/monotonic-clock",
        listOf(
          Location("first.wit", 1, 2),
          Location("second.wit", 1, 3),
        ),
      ),
    )
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
        ),
      ),
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
            duplicateOtherWorld,
          ),
        ),
      ),
    )
    val packages = listOf(cliPackage, otherPackage)
    val issueCollector = IssueCollector()

    val result = with(issueCollector) { validateUniqueServiceNames(packages) }
    assertThat(result).isNull()

    val (firstIssue, secondIssue) = issueCollector.issues

    assertThat(firstIssue.locations).containsExactlyInAnyOrder(
      Location("first.wit", 1, 2),
      Location("second.wit", 1, 3),
    )
    assertThat(secondIssue.locations).containsExactlyInAnyOrder(
      Location("other/other.wit", 5, 6),
      Location("other/other.wit", 7, 8),
    )
  }

  @Test
  fun invalidFlags() {
    val cliLocation = Location("")
    val flagCount = 33
    val cliInterface = IoInterface(
      location = cliLocation,
      name = "monotonic-clock",
      items = listOf(
        IoFlags(
          location = cliLocation.at(1, 2),
          name = "streets",
          flags = buildList {
            repeat(flagCount) { index ->
              val flag = IoFlag(
                location = cliLocation.at(1, 2 + index),
                name = "$index Street",
              )

              add(flag)
            }
          }
        )
      )
    )
    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = cliLocation,
          packageName = "wasi:cli".toPackageName(),
          items = listOf(cliInterface),
        ),
      ),
    )

    val issueCollector = IssueCollector()

    val results = with(issueCollector) {
      validateUniqueServiceNames(listOf(cliPackage))
    }

    assertThat(issueCollector.issues).containsOnly(
      Issue(
        "Flags are limited to no more than 32 flags; $flagCount flags defined",
        cliLocation.at(1, 2),
      )
    )

    assertThat(results).isNotNull()

    assertThat(results!!["wasi:cli/monotonic-clock".toServiceName()])
      .isEqualTo(cliInterface)

  }

  @Test
  fun collisionsAreCaseInsensitive() {
    val firstLocation = Location("first.wit")
    val secondLocation = Location("second.wit")
    val cliInterface = IoInterface(
      location = firstLocation.at(1, 2),
      name = "MONOTONIC-clock",
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
        ),
      ),
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
          ),
        ),
      ),
    )
    val packages = listOf(cliPackage, otherPackage)
    val issueCollector = IssueCollector()

    val result = with(issueCollector) { validateUniqueServiceNames(packages) }
    assertThat(result).isNull()

    assertThat(issueCollector.issues.single()).isEqualTo(
      Issue(
        "Duplicate definitions of wasi:cli/monotonic-clock",
        listOf(
          Location("first.wit", 1, 2),
          Location("second.wit", 1, 3),
        ),
      ),
    )
  }

}
