package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.FunctionName

sealed interface KtDeclaration {
  val documentation: String?
}

data class KtService(
  val kind: Kind,
  val instanceName: String,
  val documentation: String? = null,
  val type: ClassName,
  val functions: List<KtFunction> = listOf(),
  val services: List<KtService> = listOf(),
  val types: List<KtTypeDeclaration> = listOf(),
  val codecs: List<KtCodec> = listOf(),
) : Comparable<KtService> {
  override fun compareTo(other: KtService) = type.compareTo(other.type)

  enum class Kind {
    World,
    Guest,
    Host,
    Interface,
    Resource
  }

  data class KtCodec(
    val declaration: KtTypeDeclaration,
    val hostToGuest: Boolean,
    val guestToHost: Boolean,
  )
}

sealed interface KtTypeDeclaration : KtDeclaration {
  val type: ClassName
}

data class KtEnum(
  override val documentation: String?,
  override val type: ClassName,
  val cases: List<Case>,
) : KtTypeDeclaration {
  data class Case(
    override val documentation: String?,
    val name: String,
  ) : KtDeclaration
}

data class KtRecord(
  override val documentation: String?,
  override val type: ClassName,
  val fields: List<Field>,
) : KtTypeDeclaration {
  data class Field(
    override val documentation: String?,
    val name: String,
    val type: KtTypeName,
  ) : KtDeclaration
}

data class KtResource(
  override val documentation: String?,
  override val type: ClassName,
  val functions: List<KtFunction>,
) : KtTypeDeclaration

data class KtTypeAlias(
  override val documentation: String?,
  override val type: ClassName,
  val target: KtTypeName,
) : KtTypeDeclaration

data class KtVariant(
  override val documentation: String?,
  override val type: ClassName,
  val cases: List<Case>,
) : KtTypeDeclaration {
  data class Case(
    override val documentation: String?,
    val name: String,
    val type: KtTypeName?,
  ) : KtDeclaration
}

data class KtFlags(
  override val documentation: String?,
  override val type: ClassName,
  val flags: List<Flag>,
) : KtTypeDeclaration {
  data class Flag(
    override val documentation: String?,
    val name: String,
  ) : KtDeclaration
}

data class KtFunction(
  override val documentation: String?,
  val ktName: String,
  val name: FunctionName,
  val parameters: List<Parameter>,
  val returnType: KtTypeName?,
) : KtDeclaration {
  data class Parameter(
    override val documentation: String?,
    val name: String,
    val type: KtTypeName,
  ) : KtDeclaration
}
