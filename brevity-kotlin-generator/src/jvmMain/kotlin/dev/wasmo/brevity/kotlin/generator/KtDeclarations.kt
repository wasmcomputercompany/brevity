package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.generator.KtService.Kind

sealed interface KtDeclaration {
  val documentation: String?
}

sealed interface KtTypeDeclaration : KtDeclaration {
  val type: ClassName
}

sealed interface KtService : KtTypeDeclaration, Comparable<KtService> {
  val kind: Kind
  val instanceName: String
  override val documentation: String?
  val functions: List<KtFunction>
  val types: List<KtTypeDeclaration>

  val services: List<KtService>
    get() = types.filterIsInstance<KtService>()

  val hasInstanceMembers: Boolean
    get() = functions.isNotEmpty() || types.any { it is KtService }

  override fun compareTo(other: KtService) = type.compareTo(other.type)

  enum class Kind {
    World,
    Guest,
    Host,
    Interface,
    Resource
  }
}

data class KtInterface(
  override val kind: Kind,
  override val instanceName: String,
  override val documentation: String? = null,
  override val type: ClassName,
  override val functions: List<KtFunction> = listOf(),
  override val types: List<KtTypeDeclaration> = listOf(),
) : KtService

data class KtWorld(
  override val instanceName: String,
  override val documentation: String? = null,
  override val type: ClassName,
  override val functions: List<KtFunction> = listOf(),
  val guest: KtService?,
  val host: KtService?,
  override val types: List<KtTypeDeclaration> = listOf(),
) : KtService {
  init {
    require(guest == null || guest in types)
    require(host == null || host in types)
  }

  override val kind: Kind
    get() = Kind.World
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
