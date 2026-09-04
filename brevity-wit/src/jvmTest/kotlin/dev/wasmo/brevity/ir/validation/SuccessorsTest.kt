package dev.wasmo.brevity.ir.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrCase
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrField
import dev.wasmo.brevity.ir.IrFlag
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.ir.TypeNameDeclared
import kotlin.test.Test

class SuccessorsTest {
  @Test
  fun successors() {
    assertSuccessors(
      type = IrRecord(
        serviceName = "wit:cli/console", name = "record",
        fields = listOf(
          IrField(name = "int", type = TypeName.S32),
          IrField(name = "record", type = TypeNameDeclared("wit:cli/console", "record")),
          IrField(name = "variant", type = TypeNameDeclared("wit:cli/console", "variant")),
          IrField(name = "bool", type = TypeName.Bool),
          IrField(name = "alias", type = TypeNameDeclared("wit:cli/console", "alias")),
          IrField(name = "float", type = TypeName.F32),
        ),
      ),

      expectedSuccessors = listOf(
        TypeNameDeclared("wit:cli/console", "record"),
        TypeNameDeclared("wit:cli/console", "variant"),
        TypeNameDeclared("wit:cli/console", "alias"),
      ),
    )

    assertSuccessors(
      type = IrTypeAlias(serviceName = "wit:cli/console", name = "type-alias",
        target = TypeNameDeclared("wit:cli/console", "some-other-type"),
      ),
      expectedSuccessors = listOf(TypeNameDeclared("wit:cli/console", "some-other-type"))
    )

    assertSuccessors(
      IrVariant(serviceName = "wit:cli/console", name = "variant",
        cases = listOf(
          IrCase(name = "int", type = TypeName.S32),
          IrCase(name = "record", type = TypeNameDeclared("wit:cli/console", "record")),
          IrCase(name = "variant", type = TypeNameDeclared("wit:cli/console", "variant")),
          IrCase(name = "bool", type = TypeName.Bool),
          IrCase(name = "alias", type = TypeNameDeclared("wit:cli/console", "alias")),
          IrCase(name = "float", type = TypeName.F32),
        )),
      expectedSuccessors = listOf(
        TypeNameDeclared("wit:cli/console", "record"),
        TypeNameDeclared("wit:cli/console", "variant"),
        TypeNameDeclared("wit:cli/console", "alias"),
      )
    )

    // Types with no successors
    assertSuccessors(
      IrEnum(
        serviceName = "wit:cli/console", name = "enum",
        cases = listOf(
          IrCase(name = "one", type = null),
          IrCase(name = "two", type = null),
        ),
      ),
    )
    assertSuccessors(
      IrFlags(
        serviceName = "wit:cli/console", name = "flags",
        flags = listOf(
          IrFlag(name = "flag1"),
          IrFlag(name = "flag2"),
        ),
      ),
    )
    assertSuccessors(
      IrResource(
        serviceName = "wit:cli/console", name = "resource",
        functions = listOf(
          IrFunction(name = "f1"),
          IrFunction(name = "f2"),
        ),
      ),
    )
  }

  fun assertSuccessors(
    type: IrTypeDeclaration,
    expectedSuccessors: List<TypeName.Declared> = emptyList(),
  ) {
    val index = DeclarationIndex(mapOf(type.type to type), emptyMap())
    val actual = typeNameGraphSuccessors(index, type.type)

    assertThat(actual.toList()).isEqualTo(expectedSuccessors.toList())
  }
}
