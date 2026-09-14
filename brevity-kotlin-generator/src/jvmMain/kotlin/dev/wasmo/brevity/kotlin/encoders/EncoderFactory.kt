package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.allocateNames
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase

val MAX_FLAT_PARAMS = 16
val MAX_FLAT_RESULTS = 1

class EncoderFactory(
  private val kotlinMapper: KotlinMapper,
  private val declarationIndex: DeclarationIndex,
  private val platform: Platform,
) {
  fun get(typeName: TypeName): Encoder {
    return when (typeName) {
      TypeName.Bool -> BooleanEncoder
      TypeName.S8 -> ByteEncoder
      TypeName.S16 -> ShortEncoder
      TypeName.S32 -> IntEncoder
      TypeName.S64 -> LongEncoder
      TypeName.U8 -> UByteEncoder
      TypeName.U16 -> UShortEncoder
      TypeName.U32 -> UIntEncoder
      TypeName.U64 -> ULongEncoder
      TypeName.F32 -> FloatEncoder
      TypeName.F64 -> DoubleEncoder
      TypeName.Char -> CharEncoder
      TypeName.String -> StringEncoder

      is TypeName.Stream -> FallbackEncoder(kotlinMapper, typeName, CoreType.I32)
      is TypeName.Tuple -> {
        val fieldEncoders = typeName.types.map { element -> get(element) }
        when (fieldEncoders.size) {
          2 -> PairEncoder(fieldEncoders)
          3 -> TripleEncoder(fieldEncoders)
          4 -> QuadEncoder(fieldEncoders)
          else -> {
            val onlyTypeOrNull = typeName.types.toSet().singleOrNull()
            when {
              onlyTypeOrNull != null -> StaticListEncoder(
                size = typeName.types.size,
                elementType = kotlinMapper.get(onlyTypeOrNull),
                elementEncoder = get(onlyTypeOrNull),
              )

              else -> LargeTupleEncoder(
                typeName.types.map { kotlinMapper.get(it) },
                fieldEncoders,
              )
            }
          }
        }
      }

      // TODO: runtime support for borrow.
      is TypeName.Borrow -> get(typeName.type)
      is TypeName.Declared -> {
        val declaredType = declarationIndex[typeName]
          ?: error("unexpected type: $typeName")
        CallDeclaredTypeEncoder(
          encoderFactory = this,
          type = declaredType,
          typeEncoder = getImplementationEncoder(declaredType),
        )
      }

      is TypeName.Future -> FallbackEncoder(kotlinMapper, typeName, CoreType.I32)
      is TypeName.List -> {
        val staticSize = typeName.size
        when {
          staticSize != null -> StaticListEncoder(
            size = staticSize.toInt(),
            elementType = kotlinMapper.get(typeName.type),
            elementEncoder = get(typeName.type),
          )

          typeName.type == TypeName.S8 || typeName.type == TypeName.U8 -> ByteStringEncoder(
            DynamicListEncoder(
              elementEncoder = ByteEncoder,
              listType = Symbols.Kotlin.ByteArray,
            )
          )

          else -> DynamicListEncoder(
            elementEncoder = get(typeName.type),
            listType = kotlinMapper.get(typeName),
          )
        }
      }

      is TypeName.Map -> FallbackEncoder(kotlinMapper, typeName, CoreType.I32) // TODO: List<Tuple>.
      is TypeName.Option -> OptionalEncoder(
        some = get(typeName.type),
        instanceNameHint = when (val element = typeName.type) {
          is TypeName.Declared -> element.name.lowerCamelCase
          else -> "optional"
        },
      )

      is TypeName.Result -> ResultEncoder(
        ok = typeName.ok?.let { kotlinMapper.get(it) to get(it) },
        error = typeName.error?.let { kotlinMapper.get(it) to get(it) },
      )
    }
  }

  /** Returns an encoder that is used to implement [CallDeclaredTypeEncoder]. */
  private fun getImplementationEncoder(type: IrTypeDeclaration): Encoder {
    val kotlinType = kotlinMapper.get(type.type)
    return when (type) {
      is IrEnum -> EnumEncoder(
        kotlinType = kotlinType,
        instanceNameHint = type.type.name.lowerCamelCase,
        cases = type.cases,
      )

      is IrFlags -> {
        when {
          type.flags.size <= 8 -> FlagsEncoder(
            kotlinType = kotlinType,
            flags = type.flags,
            packedFlagEncoder = ByteEncoder,
          )
          type.flags.size <= 16 -> FlagsEncoder(
            kotlinType = kotlinType,
            flags = type.flags,
            packedFlagEncoder = ShortEncoder,
          )
          type.flags.size <= 32 -> FlagsEncoder(
            kotlinType = kotlinType,
            flags = type.flags,
            packedFlagEncoder = IntEncoder,
          )
          else -> FallbackEncoder(kotlinMapper, type.type, CoreType.I32)
        }
      }
      is IrRecord -> RecordEncoder(
        kotlinType = kotlinType,
        instanceNameHint = type.name.lowerCamelCase,
        fields = type.fields,
        fieldEncoders = type.fields.map { get(it.type) },
      )

      is IrResource -> ResourceEncoder(type.type)
      is IrTypeAlias -> TypeAliasEncoder(kotlinType, get(type.target))
      is IrVariant -> VariantEncoder(
        kotlinType = kotlinType,
        instanceNameHint = type.type.name.lowerCamelCase,
        cases = type.cases,
        caseEncoders = type.cases.map { case ->
          case.type?.let { get(it) }
        },
      )
    }
  }

  fun load(type: IrTypeDeclaration) =
    Load(type, getImplementationEncoder(type))

  fun store(type: IrTypeDeclaration) =
    Store(type, getImplementationEncoder(type))

  fun liftFlat(type: IrTypeDeclaration) =
    LiftFlat(type, getImplementationEncoder(type))

  fun lowerFlat(type: IrTypeDeclaration) =
    LowerFlat(type, getImplementationEncoder(type))

  /** One of 4 encode operations on a declared type. */
  abstract inner class EncodeStrategy(
    protected val type: IrTypeDeclaration,
    protected val encoder: Encoder,
    encodeAction: Identifier,
  ) {
    val className = kotlinMapper.get(type.type)
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

  inner class Load(
    type: IrTypeDeclaration,
    encoder: Encoder,
  ) : EncodeStrategy(type, encoder, Identifier("load")) {
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

  inner class Store(
    type: IrTypeDeclaration,
    encoder: Encoder,
  ) : EncodeStrategy(type, encoder, Identifier("store")) {
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

  inner class LiftFlat(
    type: IrTypeDeclaration,
    encoder: Encoder,
  ) : EncodeStrategy(type, encoder, Identifier("lift-flat")) {
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

  inner class LowerFlat(
    type: IrTypeDeclaration,
    encoder: Encoder,
  ) : EncodeStrategy(type, encoder, Identifier("lower-flat")) {
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
}
