package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class EncodersGenerator(
  private val encoderFactory: EncoderFactory,
  private val declarationIndex: DeclarationIndex,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
) {
  fun createEncoders(
    type: IrTypeDeclaration,
    roleTrackerEntry: RoleTracker.Entry,
    platform: Platform,
  ): List<FunSpec> {
    val isHost = platform == HostPlatform
    val isGuest = platform == GuestPlatform

    return buildList {
      if (isHost && roleTrackerEntry.host || isGuest && roleTrackerEntry.guest) {
        add(create(platform, EncodeAction.Store, type))
        add(create(platform, EncodeAction.LowerFlat, type))
      }
      if (isHost && roleTrackerEntry.guest || isGuest && roleTrackerEntry.host) {
        add(create(platform, EncodeAction.Load, type))
        add(create(platform, EncodeAction.LiftFlat, type))
      }
    }
  }

  private fun create(
    platform: Platform,
    action: EncodeAction,
    type: IrTypeDeclaration,
  ): FunSpec {
    val encoder = encoderFactory.get(type.type)
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
      return when (action) {
        EncodeAction.Load -> createLoad(type, bridgeParameter, encoder)
        EncodeAction.Store -> createStore(type, bridgeParameter, encoder)
        EncodeAction.LiftFlat -> createLiftFlat(type, bridgeParameter, encoder)
        EncodeAction.LowerFlat -> createLowerFlat(type, bridgeParameter, encoder)
      }
    }
  }

  context(codeBuilder: CodeBuilder)
  private fun createLoad(
    type: IrTypeDeclaration,
    bridgeParameter: ParameterSpec,
    encoder: Encoder,
  ): FunSpec {
    val name = encodeFunctionName(type.type, EncodeAction.Load, codeBuilder.platform)
    val addressName = codeBuilder.newName("address")
    val className = type.type.kotlinApi
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(addressName, codeBuilder.platform.addressType)
      .returns(className)
      .apply {
        codeBuilder.addStatement(
          "return %L",
          encoder.load(CodeBlock.of("%N", addressName)),
        )
        addCode(codeBuilder.build())
      }
      .build()
  }

  context(codeBuilder: CodeBuilder)
  private fun createStore(
    type: IrTypeDeclaration,
    bridgeParameter: ParameterSpec,
    encoder: Encoder,
  ): FunSpec {
    val name = encodeFunctionName(type.type, EncodeAction.Store, codeBuilder.platform)
    val addressName = codeBuilder.newName("address")
    val valueName = codeBuilder.newName("value")
    val className = type.type.kotlinApi
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(addressName, codeBuilder.platform.addressType)
      .addParameter(valueName, className)
      .apply {
        encoder.store(
          baseAddress = CodeBlock.of("%L", addressName),
          value = CodeBlock.of("%N", valueName)
        )
        addCode(codeBuilder.build())
      }
      .build()
  }

  context(codeBuilder: CodeBuilder)
  private fun createLiftFlat(
    type: IrTypeDeclaration,
    bridgeParameter: ParameterSpec,
    encoder: Encoder,
  ): FunSpec {
    val name = encodeFunctionName(type.type, EncodeAction.LiftFlat, codeBuilder.platform)
    val addressName = codeBuilder.newName("address")
    val className = type.type.kotlinApi
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(addressName, codeBuilder.platform.addressType)
      .returns(className)
      .apply {
        val coreValueNames = allocateNames("value", encoder.coreTypes.size)
        for ((v, coreType) in encoder.coreTypes.withIndex()) {
          addParameter(coreValueNames[v], coreType.kotlinCoreType)
        }
        codeBuilder.addStatement(
          "return %L",
          encoder.liftFlat(coreValueNames.map { CodeBlock.of("%N", it) }),
        )
        addCode(codeBuilder.build())
      }
      .build()
  }

  context(codeBuilder: CodeBuilder)
  private fun createLowerFlat(
    type: IrTypeDeclaration,
    bridgeParameter: ParameterSpec,
    encoder: Encoder,
  ): FunSpec {
    val name = encodeFunctionName(type.type, EncodeAction.LowerFlat, codeBuilder.platform)
    val valueName = codeBuilder.newName("value")
    val flatSinkName = codeBuilder.newName("flatSink")
    val className = type.type.kotlinApi
    return FunSpec.builder(name)
      .addParameter(bridgeParameter)
      .addParameter(flatSinkName, Symbols.Brevity.FlatSink)
      .addParameter(valueName, className)
      .apply {
        val codeBlocks = encoder.lowerFlat(CodeBlock.of("%N", valueName))
        for (coreValue in codeBlocks) {
          codeBuilder.addStatement("%N.put(%L)", flatSinkName, coreValue)
        }
        addCode(codeBuilder.build())
      }
      .build()
  }
}

fun encodeFunctionName(
  type: TypeName.Declared,
  action: EncodeAction,
  platform: Platform,
) = buildString {
  append(action.identifier.toCamelCase(upperCamel = false))
  append("_")
  append(type.serviceName.name.toCamelCase(upperCamel = true))
  append("_")
  append(type.name.toCamelCase(upperCamel = true))
  append("_")
  append(platform.identifier.toCamelCase(upperCamel = false))
}

context(codeBuilder: CodeBuilder)
private fun allocateNames(prefix: String, count: Int) = List(count) { index ->
  codeBuilder.newName(
    when (index) {
      0 -> prefix
      else -> "$prefix$index"
    },
  )
}

enum class EncodeAction(val identifier: Identifier) {
  Load(Identifier("load")),
  Store(Identifier("store")),
  LiftFlat(Identifier("lift-flat")),
  LowerFlat(Identifier("lower-flat")),
}
