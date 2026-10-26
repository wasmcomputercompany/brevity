package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.CodeBuilder
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

    val offsetValue = when {
      offset == 0 -> baseAddress
      else -> CodeBlock.of("%L + %L", baseAddress, offset)
    }

    return when (qualifiedSpecParent) {
      is QualifiedSpec.Parent.File -> CodeBlock.of(
        "%M(%L)",
        MemberName(qualifiedSpecParent.packageName, name),
        offsetValue,
      )

      is QualifiedSpec.Parent.Type -> CodeBlock.of(
        "%N(%L)",
        name,
        offsetValue,
      )
    }
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
      .addParameter(ParameterSpec.builder(addressName, platform.addressType).build())
      .returns(typeMapper.type)
      .apply {
        val baseAddress = CodeBlock.of("%N", addressName)
        context(codeBuilder, encoderContext) {
          codeBuilder.addStatement(
            "return %L",
            typeMapper.fromWit(encoder.load(baseAddress)),
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
}
