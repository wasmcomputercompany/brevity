package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class DeclaredTypeEncodersGenerator(
  private val encoderFactory: EncoderFactory,
  private val platform: Platform,
) {
  context(collector: QualifiedSpecCollector)
  fun generate(
    type: IrTypeDeclaration,
    roles: RoleTracker.Entry,
  ) {
    val isHost = platform is HostPlatform
    val isGuest = platform is GuestPlatform
    generate(
      type = type,
      encode = isHost && roles.host || isGuest && roles.guest,
      decode = isHost && roles.guest || isGuest && roles.host,
    )
  }

  context(collector: QualifiedSpecCollector)
  fun generate(
    type: IrTypeDeclaration,
    encode: Boolean,
    decode: Boolean,
  ) {
    if (encode) {
      collector += encoderFactory.store(type).generate()
      collector += encoderFactory.lowerFlat(type).generate()
    }
    if (decode) {
      collector += encoderFactory.load(type).generate()
      collector += encoderFactory.liftFlat(type).generate()
    }
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
