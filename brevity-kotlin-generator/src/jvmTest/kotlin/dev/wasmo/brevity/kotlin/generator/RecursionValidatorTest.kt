package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.collectIssues
import dev.wasmo.brevity.ir.IrVariant
import okio.Path.Companion.toPath
import org.junit.Test

class RecursionValidatorTest {
  @Test
  fun happyPath() {
    val recursive = IrVariant(
      serviceName = "wit:cli/console", name = "recursive-type", cases = emptyList(),
      location = Location("file.wit".toPath(), 1, 2),
    )
    val mutuallyRecursive1 = IrVariant(
      serviceName = "wit:cli/console", name = "mutually-recursive-1", cases = emptyList(),
      location = Location("file.wit".toPath(), 3, 4),
    )
    val mutuallyRecursive2 = IrVariant(
      serviceName = "wit:cli/console", name = "mutually-recursive-2", cases = emptyList(),
      location = Location("file.wit".toPath(), 5, 6),
    )

    val index = DeclarationIndex(
      types = listOf(recursive, mutuallyRecursive1, mutuallyRecursive2)
        .associateBy { it.type },
      services = emptyMap(),
    )

    val subject = RecursionValidator { declarationIndex ->
      listOf(
        setOf(recursive.type),
        setOf(
          mutuallyRecursive1.type,
          mutuallyRecursive2.type,
        ),
      )
    }

    val (_, issues) = collectIssues {
      subject.validate(index)
    }

    assertThat(issues).containsExactlyInAnyOrder(
      Issue("Invalid recursion", Location("file.wit", 1, 2)),
      Issue(
        "Invalid mutual recursion",
        listOf(
          Location("file.wit", 3, 4),
          Location("file.wit", 5, 6),
        ),
      ),
    )
  }
}
