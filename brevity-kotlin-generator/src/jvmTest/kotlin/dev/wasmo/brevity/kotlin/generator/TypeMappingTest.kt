package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.U_INT
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.TypeNameDeclared
import kotlin.test.Test

class TypeMappingTest {
  @Test
  fun `map declared types`() {
    assertThat(TypeNameDeclared("wasi:clocks/wall-clock", "datetime").kotlinApi)
      .isEqualTo(ClassName("wit.wasi.clocks", "WallClock", "Datetime"))

    assertThat(TypeName.List(TypeNameDeclared("wasi:clocks/wall-clock", "datetime")).kotlinApi)
      .isEqualTo(
        Symbols.KotlinCollections.List.parameterizedBy(
          ClassName("wit.wasi.clocks", "WallClock", "Datetime"),
        ),
      )
  }

  @Test
  fun `map built in types`() {
    assertThat(TypeName.U32.kotlinApi)
      .isEqualTo(U_INT)
    assertThat(TypeName.List(TypeName.U32).kotlinApi)
      .isEqualTo(ClassName("kotlin", "UIntArray"))
    assertThat(TypeName.List(TypeName.String).kotlinApi)
      .isEqualTo(Symbols.KotlinCollections.List.parameterizedBy(STRING))
  }
}
