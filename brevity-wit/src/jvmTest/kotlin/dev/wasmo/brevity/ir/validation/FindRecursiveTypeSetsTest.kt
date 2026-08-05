package dev.wasmo.brevity.ir.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrField
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.ir.TypeNameDeclared
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test

class FindRecursiveTypeSetsTest {
  @Test
  fun `finds mutual recursion`() {
    val mutuallyRecursiveRecord1 = IrRecord(
      serviceName = "wasi:cli/world",
      name = "mutuallyRecursiveRecord1",
      fields = listOf(
        IrField(
          name = "mutuallyRecursiveRecord2",
          type = TypeNameDeclared("wasi:cli/world", "mutuallyRecursiveRecord2"),
        ),
      ),
    )
    val mutuallyRecursiveRecord2 = IrRecord(
      serviceName = "wasi:cli/world",
      name = "mutuallyRecursiveRecord1",
      fields = listOf(
        IrField(
          name = "record1",
          type = TypeNameDeclared("wasi:cli/world", "mutuallyRecursiveRecord1"),
        ),
      ),
    )

    val nonRecursiveRecord = IrRecord(
      serviceName = "wasi:cli/world",
      name = "nonRecursiveRecord",
      fields = listOf(
        IrField(
          name = "boolean",
          type = TypeName.Bool,
        ),
      ),
    )
    val index = DeclarationIndex(
      listOf(
        IrWitPackage(
          packageName = "wasi:cli".toPackageName(),
          services = listOf(
            IrWorld(
              serviceName = "wasi:cli/world",
              types = listOf(
                mutuallyRecursiveRecord1,
                mutuallyRecursiveRecord2,
                nonRecursiveRecord,
              ),
            ),
          ),
        ),
      ),
    )

    val result = findRecursiveTypeSets(index)

    assertThat(result).isEqualTo(
      listOf(setOf(mutuallyRecursiveRecord1.type, mutuallyRecursiveRecord2.type)),
    )
  }

  @Test
  fun `finds simple recursion, but not single types`() {
    val simplyRecursiveRecord = IrRecord(
      serviceName = "wasi:cli/world",
      name = "simplyRecursiveRecord",
      fields = listOf(
        IrField(
          name = "simplyRecursiveRecord",
          type = TypeNameDeclared("wasi:cli/world", "simplyRecursiveRecord"),
        ),
      ),
    )

    val nonRecursiveRecord = IrRecord(
      serviceName = "wasi:cli/world",
      name = "nonRecursiveRecord",
      fields = listOf(
        IrField(
          name = "boolean",
          type = TypeName.Bool,
        ),
      ),
    )
    val index = DeclarationIndex(
      listOf(
        IrWitPackage(
          packageName = "wasi:cli".toPackageName(),
          services = listOf(
            IrWorld(
              serviceName = "wasi:cli/world",
              types = listOf(
                nonRecursiveRecord,
                simplyRecursiveRecord,
              ),
            ),
          ),
        ),
      ),
    )

    val result = findRecursiveTypeSets(index)

    assertThat(result).isEqualTo(
      listOf(setOf(simplyRecursiveRecord.type)),
    )
  }

  @Test
  fun `finds alias cycles`() {
    val recursiveAlias1 = IrTypeAlias(
      serviceName = "wasi:cli/world",
      name = "recursiveAlias1",
      target = TypeNameDeclared("wasi:cli/world", "recursiveAlias2"),
    )
    val recursiveAlias2 = IrTypeAlias(
      serviceName = "wasi:cli/world",
      name = "recursiveAlias2",
      target = TypeNameDeclared("wasi:cli/world", "recursiveAlias1"),
    )

    val nonRecursiveRecord = IrRecord(
      serviceName = "wasi:cli/world",
      name = "nonRecursiveRecord",
      fields = listOf(
        IrField(
          name = "boolean",
          type = TypeName.Bool,
        ),
      ),
    )
    val index = DeclarationIndex(
      listOf(
        IrWitPackage(
          packageName = "wasi:cli".toPackageName(),
          services = listOf(
            IrWorld(
              serviceName = "wasi:cli/world",
              types = listOf(
                nonRecursiveRecord,
                recursiveAlias1,
                recursiveAlias2,
              ),
            ),
          ),
        ),
      ),
    )

    val result = findRecursiveTypeSets(index)

    assertThat(result).isEqualTo(
      listOf(setOf(recursiveAlias1.type, recursiveAlias2.type)),
    )
  }
}
