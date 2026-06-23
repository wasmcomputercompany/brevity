package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName

sealed class KtTypeName {
  abstract val apiType: TypeName
  abstract val abiType: TypeName

  data class Simple(
    override val apiType: TypeName,
    override val abiType: TypeName,
  ) : KtTypeName()

  data class Declared(
    override val apiType: ClassName,
    val codec: Codec,
  ) : KtTypeName() {
    override val abiType: TypeName
      get() = when (codec) {
        is Codec.Alias -> codec.target.abiType
        else -> INT
      }

    sealed class Codec {
      data class Alias(val target: KtTypeName) : Codec()
      data object Enum : Codec()
      data object Flags : Codec()
      data object Record : Codec()
      data object Resource : Codec()
      data object Variant : Codec()
    }
  }

  data class Tuple(
    val types: kotlin.collections.List<KtTypeName>,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() {
        val typeArguments = types.map { it.apiType }
        return when (typeArguments.size) {
          2 -> Symbols.Kotlin.Pair.parameterizedBy(typeArguments)
          3 -> Symbols.Kotlin.Triple.parameterizedBy(typeArguments)
          4 -> Symbols.Brevity.Quad.parameterizedBy(typeArguments)
          else -> Symbols.KotlinCollections.List.parameterizedBy(STAR)
        }
      }

    override val abiType: TypeName
      get() = INT
  }

  data class List(
    val type: KtTypeName,
    val size: UInt? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = LIST.parameterizedBy(type.apiType)
    override val abiType: TypeName
      get() = INT
  }

  data class Option(
    val type: KtTypeName,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = type.apiType.copy(nullable = true)
    override val abiType: TypeName
      get() = INT
  }

  data class Borrow(
    val type: KtTypeName,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = type.apiType
    override val abiType: TypeName
      get() = type.abiType
  }

  data class Result(
    val ok: KtTypeName? = null,
    val err: KtTypeName? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.Kotlin.Pair.parameterizedBy(
        ok?.apiType ?: STAR,
        err?.apiType ?: STAR,
      )
    override val abiType: TypeName
      get() = INT
  }

  data class Map(
    val key: KtTypeName,
    val value: KtTypeName,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.KotlinCollections.Map.parameterizedBy(key.apiType, value.apiType)
    override val abiType: TypeName
      get() = INT
  }

  data class Future(
    val type: KtTypeName? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.KotlinCoroutines.Deferred.parameterizedBy(type?.apiType ?: STAR)
    override val abiType: TypeName
      get() = INT
  }

  data class Stream(
    val type: KtTypeName? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.Brevity.Stream.parameterizedBy(type?.apiType ?: STAR)
    override val abiType: TypeName
      get() = INT
  }
}
