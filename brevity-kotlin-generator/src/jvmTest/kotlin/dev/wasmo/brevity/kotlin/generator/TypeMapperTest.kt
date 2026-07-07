package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.U_INT
import dev.wasmo.brevity.ir.IrTypeName
import dev.wasmo.brevity.ir.IrTypeNameDeclared
import kotlin.test.Test

class TypeMapperTest {
  @Test
  fun `map declared types`() {
    val typeMapper = TypeMapper(kotlinPackagePrefix = "wit")

    assertThat(
      typeMapper.map(IrTypeNameDeclared("wasi:clocks/wall-clock", "datetime")),
    ).isEqualTo(
      KtTypeName.Declared(
        ClassName("wit.wasi.clocks", "WallClock", "Datetime"),
      ),
    )

    assertThat(
      typeMapper.map(IrTypeName.List(IrTypeNameDeclared("wasi:clocks/wall-clock", "datetime"))),
    ).isEqualTo(
      KtTypeName.List(
        KtTypeName.Declared(
          apiType = ClassName("wit.wasi.clocks", "WallClock", "Datetime"),
        ),
      ),
    )
  }

  @Test
  fun `map built in types`() {
    val typeMapper = TypeMapper(kotlinPackagePrefix = "wit")

    assertThat(typeMapper.map(IrTypeName.U32)).isEqualTo(KtTypeName.Simple(U_INT, INT))
    assertThat(typeMapper.map(IrTypeName.List(IrTypeName.U32)))
      .isEqualTo(
        KtTypeName.Simple(
          ClassName("kotlin", "UIntArray"),
          INT,
        ),
      )
    assertThat(typeMapper.map(IrTypeName.List(IrTypeName.String)))
      .isEqualTo(KtTypeName.List(KtTypeName.Simple(STRING, INT)))
  }
}
