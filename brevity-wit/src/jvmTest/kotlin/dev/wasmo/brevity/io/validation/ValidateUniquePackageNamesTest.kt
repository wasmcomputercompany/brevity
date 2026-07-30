package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.toPackageName
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

    val issueCollector = IssueCollector()
    val map = with(issueCollector) { validateUniquePackageNames(packages) }

    assertThat(map).isNotNull()

    assertThat(map!!).containsOnly(
      "wasi:cli".toPackageName() to cliPackage,
      "wasi:inline".toPackageName() to inlinePackage,
      "wasi:other".toPackageName() to otherPackage,
    )

    assertThat(issueCollector.issues).isEmpty()
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
    val issueCollector = IssueCollector()

    val result = with(issueCollector) { validateUniquePackageNames(listOf(cliPackage, otherPackage)) }
    assertThat(result).isNull()

    assertThat(issueCollector.issues.single().locations).containsExactlyInAnyOrder(
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
    val issueCollector = IssueCollector()
    val result = with(issueCollector) { validateUniquePackageNames(listOf(cliPackage, otherPackage)) }
    assertThat(result).isNull()

    val (firstIssue, secondIssue) = issueCollector.issues

    assertThat(firstIssue.locations).containsExactlyInAnyOrder(
      otherLocation.at(1, 2),
      cliLocation,
    )
    assertThat(secondIssue.locations).containsExactlyInAnyOrder(
      otherLocation.at(1, 2),
      otherLocation,
    )
  }
}

inline fun <reified T : Any> Any.assertIsInstanceOf(): T {
  assertThat(this).isInstanceOf(T::class.java)
  return this as T
}
