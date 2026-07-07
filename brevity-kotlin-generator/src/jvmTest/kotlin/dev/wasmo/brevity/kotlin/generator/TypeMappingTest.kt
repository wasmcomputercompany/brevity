package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.U_INT
import dev.wasmo.brevity.ir.IrTypeName
import dev.wasmo.brevity.ir.IrTypeNameDeclared
import kotlin.test.Test

class TypeMappingTest {
  @Test
  fun `map declared types`() {
    assertThat(IrTypeNameDeclared("wasi:clocks/wall-clock", "datetime").kotlinApi)
      .isEqualTo(ClassName("wit.wasi.clocks", "WallClock", "Datetime"))

    assertThat(IrTypeName.List(IrTypeNameDeclared("wasi:clocks/wall-clock", "datetime")).kotlinApi)
      .isEqualTo(
        Symbols.KotlinCollections.List.parameterizedBy(
          ClassName("wit.wasi.clocks", "WallClock", "Datetime"),
        ),
      )
  }

  @Test
  fun `map built in types`() {
    assertThat(IrTypeName.U32.kotlinApi)
      .isEqualTo(U_INT)
    assertThat(IrTypeName.List(IrTypeName.U32).kotlinApi)
      .isEqualTo(ClassName("kotlin", "UIntArray"))
    assertThat(IrTypeName.List(IrTypeName.String).kotlinApi)
      .isEqualTo(Symbols.KotlinCollections.List.parameterizedBy(STRING))
  }
}
