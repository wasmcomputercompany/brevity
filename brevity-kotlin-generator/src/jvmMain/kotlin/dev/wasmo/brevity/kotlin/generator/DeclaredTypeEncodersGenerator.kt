package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class DeclaredTypeEncodersGenerator(
  private val encoderFactory: EncoderFactory,
  private val platform: Platform,
) {
  fun generate(
    type: IrTypeDeclaration,
    roles: RoleTracker.Entry,
  ): List<FunSpec> {
    val isHost = platform == HostPlatform
    val isGuest = platform == GuestPlatform
    return generate(
      type = type,
      encode = isHost && roles.host || isGuest && roles.guest,
      decode = isHost && roles.guest || isGuest && roles.host,
    )
  }

  fun generate(
    type: IrTypeDeclaration,
    encode: Boolean,
    decode: Boolean,
  ): List<FunSpec> {
    val encoder = encoderFactory.get(type.type)
    return buildList {
      if (encode) {
        add(DeclaredTypeStoreGenerator(type, encoder, platform).generate())
        add(DeclaredTypeLowerFlatGenerator(type, encoder, platform).generate())
      }
      if (decode) {
        add(DeclaredTypeLoadGenerator(type, encoder, platform).generate())
        add(DeclaredTypeLiftFlatGenerator(type, encoder, platform).generate())
      }
    }
  }
}

abstract class DeclaredTypeEncoderGenerator(
  protected val type: IrTypeDeclaration,
  protected val encoder: Encoder,
  platform: Platform,
  encodeAction: Identifier,
) {
  val nameAllocator = NameAllocator()
  val bridgeParameter = ParameterSpec.builder(
    nameAllocator.newName("bridge"),
    platform.bridgeType,
  ).build()
  val codeBuilder = CodeBuilder(
    bridge = CodeBlock.of("%N", bridgeParameter.name),
    platform = platform,
    nameAllocator = nameAllocator,
  )

  val className = type.type.kotlinApi
  val name = buildString {
    append(encodeAction.toCamelCase(upperCamel = false))
    append("_")
    append(type.type.serviceName.name.toCamelCase(upperCamel = true))
    append("_")
    append(type.name.toCamelCase(upperCamel = true))
    append("_")
    append(codeBuilder.platform.identifier.toCamelCase(upperCamel = false))
  }

  abstract fun generate(): FunSpec
}

class DeclaredTypeLoadGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("load")) {
  override fun generate(): FunSpec {
    val addressName = codeBuilder.newName("address")
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(addressName, codeBuilder.platform.addressType)
      .returns(className)
      .apply {
        context(codeBuilder) {
          codeBuilder.addStatement(
            "return %L",
            encoder.load(CodeBlock.of("%N", addressName)),
          )
        }
        addCode(codeBuilder.build())
      }
      .build()
  }
}

class DeclaredTypeStoreGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("store")) {
  override fun generate(): FunSpec {
    val addressName = codeBuilder.newName("address")
    val valueName = codeBuilder.newName("value")
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(addressName, codeBuilder.platform.addressType)
      .addParameter(valueName, className)
      .apply {
        context(codeBuilder) {
          encoder.store(
            baseAddress = CodeBlock.of("%L", addressName),
            value = CodeBlock.of("%N", valueName),
          )
        }
        addCode(codeBuilder.build())
      }
      .build()
  }
}

class DeclaredTypeLiftFlatGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("lift-flat")) {
  override fun generate(): FunSpec {
    val addressName = codeBuilder.newName("address")
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(addressName, codeBuilder.platform.addressType)
      .returns(className)
      .apply {
        context(codeBuilder) {
          val coreValueNames = allocateNames("value", encoder.coreTypes.size)
          for ((v, coreType) in encoder.coreTypes.withIndex()) {
            addParameter(coreValueNames[v], coreType.kotlinCoreType)
          }
          codeBuilder.addStatement(
            "return %L",
            encoder.liftFlat(coreValueNames.map { CodeBlock.of("%N", it) }),
          )
        }
        addCode(codeBuilder.build())
      }
      .build()
  }
}

class DeclaredTypeLowerFlatGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("lower-flat")) {
  override fun generate(): FunSpec {
    val valueName = codeBuilder.newName("value")
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(valueName, className)
      .apply {
        context(codeBuilder) {
          if (encoder.coreTypes.size > 1) {
            val callBuilderName = codeBuilder.newName("callBuilder")
            addParameter(callBuilderName, Symbols.Brevity.CallBuilder)
            val codeBlocks = encoder.lowerFlat(CodeBlock.of("%N", valueName))
            for (coreValue in codeBlocks) {
              codeBuilder.addStatement("%N.put(%L)", callBuilderName, coreValue)
            }
          } else {
            returns(encoder.coreTypes.single().kotlinCoreType)
            codeBuilder.addStatement(
              "return %L",
              encoder.lowerFlat(CodeBlock.of("%N", valueName)).single(),
            )
          }
        }
        addCode(codeBuilder.build())
      }
      .build()
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
