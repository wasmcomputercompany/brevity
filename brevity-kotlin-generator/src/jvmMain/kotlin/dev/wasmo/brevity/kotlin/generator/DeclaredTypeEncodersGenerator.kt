package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.byteCount

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
    val encoder = encoderFactory.getImplementationEncoder(type)
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
  protected val platform: Platform,
  encodeAction: Identifier,
) {
  val className = type.type.kotlinApi
  val memberName = MemberName(
    className.packageName,
    buildString {
      append(encodeAction.lowerCamelCase)
      append("_")
      append(type.type.serviceName.name.upperCamelCase)
      append("_")
      append(type.name.upperCamelCase)
      append("_")
      append(platform.identifier.lowerCamelCase)
    },
  )

  fun generate(): FunSpec {
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
    context(codeBuilder) {
      return generate(bridgeParameter)
    }
  }

  context(codeBuilder: CodeBuilder)
  abstract fun generate(bridgeParameter: ParameterSpec): FunSpec
}

class DeclaredTypeLoadGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("load")) {
  context(codeBuilder: CodeBuilder)
  override fun generate(
    bridgeParameter: ParameterSpec,
  ): FunSpec {
    val addressName = codeBuilder.newName("address")
    return FunSpec.builder(memberName)
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

  context(codeBuilder: CodeBuilder)
  fun call(baseAddress: CodeBlock): CodeBlock {
    return CodeBlock.of(
      "%M(%L, %L)",
      memberName,
      codeBuilder.bridge,
      baseAddress,
    )
  }
}

class DeclaredTypeStoreGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("store")) {
  context(codeBuilder: CodeBuilder)
  override fun generate(
    bridgeParameter: ParameterSpec,
  ): FunSpec {
    val addressName = codeBuilder.newName("address")
    val valueName = codeBuilder.newName("value")
    return FunSpec.builder(memberName)
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

  context(codeBuilder: CodeBuilder)
  fun call(baseAddress: CodeBlock, value: CodeBlock) {
    codeBuilder.addStatement(
      "%M(%L, %L, %L)",
      memberName,
      codeBuilder.bridge,
      baseAddress,
      value,
    )
  }
}

class DeclaredTypeLiftFlatGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("lift-flat")) {
  context(codeBuilder: CodeBuilder)
  override fun generate(bridgeParameter: ParameterSpec): FunSpec {
    return FunSpec.builder(memberName)
      .addParameter(bridgeParameter)
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

  context(codeBuilder: CodeBuilder)
  fun call(transformer: Encoder.Transformer) {
    transformer.put(
      buildCodeBlock {
        add("%M(⇥\n", memberName)
        add("%L,\n", codeBuilder.bridge)
        for (i in encoder.coreTypes.indices) {
          add("%L,\n", transformer.take())
        }
        add("⇤)", memberName)
      },
    )
  }
}

class DeclaredTypeLowerFlatGenerator(
  type: IrTypeDeclaration,
  encoder: Encoder,
  platform: Platform,
) : DeclaredTypeEncoderGenerator(type, encoder, platform, Identifier("lower-flat")) {
  context(codeBuilder: CodeBuilder)
  override fun generate(
    bridgeParameter: ParameterSpec,
  ): FunSpec {
    val valueName = codeBuilder.newName("value")
    return FunSpec.builder(memberName)
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

  context(codeBuilder: CodeBuilder)
  fun call(transformer: Encoder.Transformer) {
    if (encoder.coreTypes.size > 1) {
      val callBuilderName = codeBuilder.newName("callBuilder")
      val i32Count = encoder.coreTypes.count { it.byteCount == 4 }
      val i64Count = encoder.coreTypes.count { it.byteCount == 8 }

      codeBuilder.addStatement(
        "val %N = %T(i32Count = %L, i64Count = %L)",
        callBuilderName,
        Symbols.Brevity.CallBuilder,
        i32Count,
        i64Count,
      )

      codeBuilder.addStatement(
        "%M(%L, %L, %N)",
        memberName,
        codeBuilder.bridge,
        transformer.take(),
        callBuilderName,
      )

      val coreValueNames = allocateNames("value", encoder.coreTypes.size)
      for ((v, coreType) in encoder.coreTypes.withIndex()) {
        val takeFunction = when (coreType) {
          CoreType.F32 -> "takeF32"
          CoreType.F64 -> "takeF64"
          CoreType.I32, CoreType.Pointer -> "takeI32"
          CoreType.I64 -> "takeI64"
        }
        codeBuilder.addStatement(
          "val %N = %N.%N()",
          coreValueNames[v],
          callBuilderName,
          takeFunction,
        )
        transformer.put(CodeBlock.of("%N", coreValueNames[v]))
      }
    } else {
      transformer.put(
        CodeBlock.of(
          "%M(%L, %L)",
          memberName,
          codeBuilder.bridge,
          transformer.take(),
        ),
      )
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
