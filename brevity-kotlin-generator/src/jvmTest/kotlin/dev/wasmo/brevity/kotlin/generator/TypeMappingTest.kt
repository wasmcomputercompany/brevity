package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.U_INT
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.TypeNameDeclared
import dev.wasmo.brevity.kotlin.KotlinMapper
import kotlin.test.Test

class TypeMappingTest {
  private val kotlinMapper = KotlinMapper()

  @Test
  fun `map declared types`() {
    assertThat(kotlinMapper.get(TypeNameDeclared("wasi:clocks/wall-clock", "datetime")))
      .isEqualTo(ClassName("wit.wasi.clocks", "WallClock", "Datetime"))

    assertThat(
      kotlinMapper.get(TypeName.List(TypeNameDeclared("wasi:clocks/wall-clock", "datetime"))),
    ).isEqualTo(
      Symbols.KotlinCollections.List.parameterizedBy(
        ClassName("wit.wasi.clocks", "WallClock", "Datetime"),
      ),
    )
  }

  @Test
  fun `map built in types`() {
    assertThat(kotlinMapper.get(TypeName.U32))
      .isEqualTo(U_INT)
    assertThat(kotlinMapper.get(TypeName.List(TypeName.U32)))
      .isEqualTo(ClassName("kotlin", "UIntArray"))
    assertThat(kotlinMapper.get(TypeName.List(TypeName.String)))
      .isEqualTo(Symbols.KotlinCollections.List.parameterizedBy(STRING))
  }

  /**
   * WASI uses types like `tuple<u16, u16, u16, u16, u16, u16, u16, u16>`. We want this to be
   * represented as a `List<UShort>` and not a `List<*>`.
   */
  @Test
  fun `map large homogenous tuples`() {
    val types = listOf(TypeName.S32, TypeName.S32, TypeName.S32, TypeName.S32, TypeName.S32)
    assertThat(kotlinMapper.get(TypeName.Tuple(types)))
      .isEqualTo(Symbols.KotlinCollections.List.parameterizedBy(INT))
  }

  @Test
  fun `map large heterogeneous tuples`() {
    val types = listOf(TypeName.S32, TypeName.S32, TypeName.S32, TypeName.S32, TypeName.U32)
    assertThat(kotlinMapper.get(TypeName.Tuple(types)))
      .isEqualTo(Symbols.KotlinCollections.List.parameterizedBy(STAR))
  }
}
