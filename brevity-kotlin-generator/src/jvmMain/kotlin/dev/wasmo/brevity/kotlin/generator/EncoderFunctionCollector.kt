package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.CallBuilderLowerFlatIo
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.code.localVariablesLowerFlatIo
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
  fun callDeclaredLiftFlatFunction(
    encoderContext: Encoder.Context,
    encoder: Encoder,
    typeMapper: TypeMapper,
    orientation: Orientation,
    values: List<CodeBlock>,
  ): CodeBlock {
    val type = encoder.type as TypeName.Declared
    val name = "lift${type.serviceName.name.upperCamelCase}${type.name.upperCamelCase}$orientation"

    if (declaredFunctionNames.add(name)) {
      declareLiftFlatFunction(codeBuilder.bridge, encoderContext, encoder, typeMapper, name)
    }

    return CodeBlock.of("%L(%L)", functionNameAsCodeBlock(name), values.joinToCode())
  }

  context(codeBuilder: CodeBuilder)
  fun callDeclaredLowerFlatFunction(
    encoderContext: Encoder.Context,
    encoder: Encoder,
    typeMapper: TypeMapper,
    orientation: Orientation,
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    val type = encoder.type as TypeName.Declared
    val name = "lower${type.serviceName.name.upperCamelCase}${type.name.upperCamelCase}$orientation"

    if (declaredFunctionNames.add(name)) {
      declareLowerFlatFunction(codeBuilder.bridge, encoderContext, encoder, typeMapper, name)
    }

    val callLowerFlat = buildCodeBlock {
      add("%L(", functionNameAsCodeBlock(name))
      if (encoder.lowerAllocates) {
        add("%N, ", memoryAllocator!!.name)
      }
      if (encoder.coreTypes.size != 1) {
        add("%L, ", (lowerFlatIo as CallBuilderLowerFlatIo).name)
      }
      add("%L)", value)
    }

    if (encoder.coreTypes.size == 1) {
      lowerFlatIo.put(callLowerFlat)
    } else {
      codeBuilder.addStatement("%L", callLowerFlat)
    }
  }

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
      declareLoadFunction(codeBuilder.bridge, encoderContext, encoder, typeMapper, name)
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
      declareStoreFunction(codeBuilder.bridge, encoderContext, encoder, typeMapper, name)
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

  private fun declareLiftFlatFunction(
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

    val function = FunSpec.builder(name)
      .returns(typeMapper.type)
      .apply {
        val nameHints = encoder.nameHints
        val parameterValues = mutableListOf<CodeBlock>()

        for ((i, coreType) in encoder.coreTypes.withIndex()) {
          val parameterName = codeBuilder.newName(nameHints?.get(i)?.lowerCamelCase ?: "v$i")
          addParameter(parameterName, coreType.kotlinCoreType)
          parameterValues.add(CodeBlock.of("%N", parameterName))
        }

        context(codeBuilder, encoderContext) {
          codeBuilder.addStatement(
            "return %L",
            typeMapper.fromWit(encoder.liftFlat(parameterValues)),
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

  private fun declareLoadFunction(
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

  private fun declareStoreFunction(
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

  private fun declareLowerFlatFunction(
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

    val function = FunSpec.builder(name)
      .apply {
        context(codeBuilder, encoderContext) {
          val memoryAllocator = when {
            encoder.lowerAllocates -> {
              platform.getMemoryAllocator(codeBuilder.nameAllocator.newName("memoryAllocator"))
                .also { addParameter(it.name, it.type) }
            }

            else -> null
          }

          val callBuilder = when {
            encoder.coreTypes.size == 1 -> {
              localVariablesLowerFlatIo(listOf("result" to encoder.coreTypes.single()))
            }

            else -> CallBuilderLowerFlatIo(
              codeBuilder.nameAllocator.newName("callBuilder"),
              encoder.coreTypes,
            ).also {
              addParameter(it.name, it.type)
            }
          }

          val valueName = codeBuilder.nameAllocator.newName("value")
          addParameter(valueName, typeMapper.type)

          encoder.lowerFlat(
            memoryAllocator = memoryAllocator,
            lowerFlatIo = callBuilder,
            value = typeMapper.toWit(CodeBlock.of("%N", valueName)),
          )

          if (encoder.coreTypes.size == 1) {
            returns(encoder.coreTypes.single().kotlinCoreType)
            codeBuilder.addStatement("return %L", callBuilder.take())
          }

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
