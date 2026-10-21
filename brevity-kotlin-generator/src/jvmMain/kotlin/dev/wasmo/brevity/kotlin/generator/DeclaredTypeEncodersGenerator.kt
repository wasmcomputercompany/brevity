package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class DeclaredTypeEncodersGenerator(
  private val encoderFactory: EncoderFactory,
) {
  context(collector: QualifiedSpecCollector)
  fun generate(type: IrTypeDeclaration) {
    collector += encoderFactory.store(type).generate()
    collector += encoderFactory.lowerFlat(type).generate()
    collector += encoderFactory.load(type).generate()
    collector += encoderFactory.liftFlat(type).generate()
  }
}

context(codeBuilder: CodeBuilder)
fun allocateNames(prefix: String, count: Int) = List(count) { index ->
  codeBuilder.newName(
    when (index) {
      0 -> prefix
      else -> "$prefix$index"
    },
  )
}
