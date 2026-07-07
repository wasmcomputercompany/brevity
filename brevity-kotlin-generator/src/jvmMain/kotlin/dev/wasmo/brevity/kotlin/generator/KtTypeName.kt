package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.ir.IrTypeName

sealed class KtTypeName {
  abstract val witType: IrTypeName
  abstract val apiType: TypeName
  abstract val abiType: TypeName

  data class Simple(
    override val witType: IrTypeName,
    override val apiType: TypeName,
    override val abiType: TypeName,
  ) : KtTypeName()

  data class Declared(
    override val witType: IrTypeName.Declared,
    override val apiType: ClassName,
  ) : KtTypeName() {
    override val abiType: TypeName
      get() = INT
  }

  data class Tuple(
    override val witType: IrTypeName.Tuple,
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
    override val witType: IrTypeName.List,
    val type: KtTypeName,
    val size: UInt? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = LIST.parameterizedBy(type.apiType)
    override val abiType: TypeName
      get() = INT
  }

  data class Option(
    override val witType: IrTypeName.Option,
    val type: KtTypeName,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = type.apiType.copy(nullable = true)
    override val abiType: TypeName
      get() = INT
  }

  data class Borrow(
    override val witType: IrTypeName.Borrow,
    val type: KtTypeName,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = type.apiType
    override val abiType: TypeName
      get() = type.abiType
  }

  data class Result(
    override val witType: IrTypeName.Result,
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
    override val witType: IrTypeName.Map,
    val key: KtTypeName,
    val value: KtTypeName,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.KotlinCollections.Map.parameterizedBy(key.apiType, value.apiType)
    override val abiType: TypeName
      get() = INT
  }

  data class Future(
    override val witType: IrTypeName.Future,
    val type: KtTypeName? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.KotlinCoroutines.Deferred.parameterizedBy(type?.apiType ?: STAR)
    override val abiType: TypeName
      get() = INT
  }

  data class Stream(
    override val witType: IrTypeName.Stream,
    val type: KtTypeName? = null,
  ) : KtTypeName() {
    override val apiType: TypeName
      get() = Symbols.Brevity.Stream.parameterizedBy(type?.apiType ?: STAR)
    override val abiType: TypeName
      get() = INT
  }
}
