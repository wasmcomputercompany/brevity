package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.containsExactly
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IrMapper
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.ir.IrInterface
import kotlin.test.Test

class StreamsAndFuturesFinderTest {
  @Test
  fun happyPath() = collectNoIssuesOrThrow {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "test:test".toPackageName(),
        files = listOf(
          """
          |package test:test;
          |
          |interface sample {
          |    foo: func(x: future<future<u32>>, y: u32) -> stream<u8>;
          |}
          """.trimMargin().toWitFile(Location("test.wit")),
        ),
      ),
    )
    val irPackages = IrMapper(ioPackages).map()
    val declarationIndex = DeclarationIndex(irPackages)

    val irPackage = irPackages.single()
    val irInterface = irPackage.services.single() as IrInterface
    val foo = irInterface.functions.single()

    val finder = StreamsAndFuturesFinder(declarationIndex)
    assertThat(finder.find(foo)).containsExactly(
      IndexedValue(0, TypeName.Future(TypeName.U32)),
      IndexedValue(1, TypeName.Future(TypeName.Future(TypeName.U32))),
      IndexedValue(2, TypeName.Stream(TypeName.U8)),
    )
  }
}
