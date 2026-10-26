package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.Encoder

class EncoderFunctionCollector(
  val platform: Platform,
  val kotlinMapper: KotlinMapper,
  val qualifiedSpecs: MutableList<QualifiedSpec>,
  val qualifiedSpecParent: QualifiedSpec.Parent,
  val qualifiedSpecOptIns: Set<ClassName>,
  val world: AbiWorld,
  val typeNamer: TypeNamer,
) {
  private val declaredFunctionNames = mutableSetOf<String>()

  context(codeBuilder: CodeBuilder)
  fun callDeclaredLoadFunction(
    encoderContext: Encoder.Context,
    encoder: Encoder,
    typeMapper: TypeMapper,
    orientation: Orientation,
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val type = encoder.type as TypeName.Declared
    val name = "load${type.serviceName.name.upperCamelCase}${type.name.upperCamelCase}$orientation"

    if (declaredFunctionNames.add(name)) {
      declaredLoadFunction(codeBuilder.bridge, encoderContext, encoder, typeMapper, name)
    }

    return CodeBlock.of("%L(%L)", functionNameAsCodeBlock(name), sum(baseAddress, offset))
  }

  context(codeBuilder: CodeBuilder)
  fun callDeclaredStoreFunction(
    encoderContext: Encoder.Context,
    encoder: Encoder,
    typeMapper: TypeMapper,
    orientation: Orientation,
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val type = encoder.type as TypeName.Declared
    val name = "store${type.serviceName.name.upperCamelCase}${type.name.upperCamelCase}$orientation"

    if (declaredFunctionNames.add(name)) {
      declaredStoreFunction(codeBuilder.bridge, encoderContext, encoder, typeMapper, name)
    }

    codeBuilder.add("%L(", functionNameAsCodeBlock(name))
    if (encoder.lowerAllocates) {
      codeBuilder.add("%N, ", memoryAllocator!!.name)
    }
    codeBuilder.add(
      "%L, %L)\n",
      sum(baseAddress, offset),
      value,
    )
  }

  private fun declaredLoadFunction(
    bridge: CodeBlock,
    encoderContext: Encoder.Context,
    encoder: Encoder,
    typeMapper: TypeMapper,
    name: String,
  ) {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = platform,
      nameAllocator = NameAllocator(),
    )
    val addressName = codeBuilder.nameAllocator.newName("address")

    val function = FunSpec.builder(name)
      .addParameter(addressName, platform.addressType)
      .returns(typeMapper.type)
      .apply {
        val addressValue = CodeBlock.of("%N", addressName)
        context(codeBuilder, encoderContext) {
          codeBuilder.addStatement(
            "return %L",
            typeMapper.fromWit(encoder.load(addressValue)),
          )
          addCode(codeBuilder.build())
        }
      }
      .build()

    qualifiedSpecs += QualifiedSpec.Function(
      parent = qualifiedSpecParent,
      optIns = qualifiedSpecOptIns,
      function = function,
    )
  }

  private fun declaredStoreFunction(
    bridge: CodeBlock,
    encoderContext: Encoder.Context,
    encoder: Encoder,
    typeMapper: TypeMapper,
    name: String,
  ) {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = platform,
      nameAllocator = NameAllocator(),
    )
    val addressName = codeBuilder.nameAllocator.newName("address")
    val valueName = codeBuilder.nameAllocator.newName("value")
    val memoryAllocator = when {
      encoder.lowerAllocates -> platform.getMemoryAllocator(
        codeBuilder.nameAllocator.newName("memoryAllocator"),
      )

      else -> null
    }

    val function = FunSpec.builder(name)
      .apply {
        if (memoryAllocator != null) {
          addParameter(memoryAllocator.name, memoryAllocator.type)
        }
      }
      .addParameter(addressName, platform.addressType)
      .addParameter(valueName, typeMapper.type)
      .apply {
        context(codeBuilder, encoderContext) {
          encoder.store(
            memoryAllocator = memoryAllocator,
            baseAddress = CodeBlock.of("%N", addressName),
            value = typeMapper.toWit(CodeBlock.of("%N", valueName)),
          )
          addCode(codeBuilder.build())
        }
      }
      .build()

    qualifiedSpecs += QualifiedSpec.Function(
      parent = qualifiedSpecParent,
      optIns = qualifiedSpecOptIns,
      function = function,
    )
  }

  private fun sum(baseAddress: CodeBlock, offset: Int): CodeBlock {
    return when {
      offset == 0 -> baseAddress
      else -> CodeBlock.of("%L + %L", baseAddress, offset)
    }
  }

  private fun functionNameAsCodeBlock(name: String): CodeBlock {
    return when (qualifiedSpecParent) {
      is QualifiedSpec.Parent.File -> CodeBlock.of(
        "%M",
        MemberName(qualifiedSpecParent.packageName, name),
      )

      is QualifiedSpec.Parent.Type -> CodeBlock.of(
        "%N",
        name,
      )
    }
  }
}
