package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.io.toServiceName
import dev.wasmo.brevity.ir.TypeNameDeclared
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test

class KotlinNameTest {
  @Test
  fun `package name mapping`() {
    val packageName = "wasi:clocks".toPackageName()
    val serviceName = "wasi:clocks/wall-clock".toServiceName()
    val typeName = TypeNameDeclared("wasi:clocks/wall-clock", "datetime")
    assertThat(packageName.kotlinApi)
      .isEqualTo("wit.wasi.clocks")
    assertThat(serviceName.kotlinApi)
      .isEqualTo(ClassName("wit.wasi.clocks", "WallClock"))
    assertThat(typeName.kotlinApi)
      .isEqualTo(ClassName("wit.wasi.clocks", "WallClock", "Datetime"))
  }

  @Test
  fun `package name mapping with version`() {
    val packageName = "wasi:clocks@0.2.12".toPackageName()
    val serviceName = "wasi:clocks/wall-clock@0.2.12".toServiceName()
    val typeName = TypeNameDeclared("wasi:clocks/wall-clock@0.2.12", "datetime")
    assertThat(packageName.kotlinApi)
      .isEqualTo("wit.wasi.clocks.v0_2_12")
    assertThat(serviceName.kotlinApi)
      .isEqualTo(ClassName("wit.wasi.clocks.v0_2_12", "WallClock"))
    assertThat(typeName.kotlinApi)
      .isEqualTo(ClassName("wit.wasi.clocks.v0_2_12", "WallClock", "Datetime"))
  }

  @Test
  fun `package name mapping with dashes`() {
    val packageName = "wasi:grandfather-clocks".toPackageName()
    val serviceName = "wasi:grandfather-clocks/wall-clock".toServiceName()
    val typeName = TypeNameDeclared("wasi:grandfather-clocks/wall-clock", "datetime")
    assertThat(packageName.kotlinApi)
      .isEqualTo("wit.wasi.grandfatherclocks")
    assertThat(serviceName.kotlinApi)
      .isEqualTo(ClassName("wit.wasi.grandfatherclocks", "WallClock"))
    assertThat(typeName.kotlinApi)
      .isEqualTo(ClassName("wit.wasi.grandfatherclocks", "WallClock", "Datetime"))
  }
}
