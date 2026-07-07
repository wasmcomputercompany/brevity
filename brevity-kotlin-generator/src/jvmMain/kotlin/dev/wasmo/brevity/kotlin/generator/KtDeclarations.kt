package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.ServiceName

sealed interface KtDeclaration {
  val documentation: String?
}

sealed interface KtTypeDeclaration : KtDeclaration {
  val ktType: KtTypeName.Declared
  val type: ClassName
    get() = ktType.apiType
}

sealed interface KtService : KtDeclaration {
  val type: ClassName
  val types: List<KtTypeDeclaration>
  val hasInstanceMembers: Boolean
  val serviceName: ServiceName

  val instanceName: String
    get() = serviceName.name.toCamelCase(upperCamel = false)
}

data class KtInterface(
  override val documentation: String? = null,
  override val type: ClassName,
  override val serviceName: ServiceName,
  val functions: List<KtFunction> = listOf(),
  override val types: List<KtTypeDeclaration> = listOf(),
) : KtService {
  override val hasInstanceMembers: Boolean
    get() = functions.isNotEmpty()
}

data class KtWorld(
  override val documentation: String? = null,
  override val type: ClassName,
  override val serviceName: ServiceName,
  val guestApis: ExternalApis? = null,
  val hostApis: ExternalApis? = null,
  override val types: List<KtTypeDeclaration> = listOf(),
) : KtService {
  data class ExternalApis(
    val instanceName: String,
    val type: ClassName,
    val items: List<Item>,
  ) {
    sealed interface Item

    data class InterfaceProperty(
      val instanceName: String,
      val documentation: String? = null,
      val type: ClassName,
      val serviceName: ServiceName,
    ) : Item
  }

  override val hasInstanceMembers: Boolean
    get() = guestApis != null || hostApis != null
}

data class KtEnum(
  override val documentation: String?,
  override val ktType: KtTypeName.Declared,
  val cases: List<Case>,
) : KtTypeDeclaration {
  data class Case(
    override val documentation: String?,
    val name: String,
  ) : KtDeclaration
}

data class KtRecord(
  override val documentation: String?,
  override val ktType: KtTypeName.Declared,
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
  override val ktType: KtTypeName.Declared,
  val functions: List<KtFunction>,
) : KtTypeDeclaration

data class KtTypeAlias(
  override val documentation: String?,
  override val ktType: KtTypeName.Declared,
  val target: KtTypeName,
) : KtTypeDeclaration

data class KtVariant(
  override val documentation: String?,
  override val ktType: KtTypeName.Declared,
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
  override val ktType: KtTypeName.Declared,
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
) : KtDeclaration, KtWorld.ExternalApis.Item {
  data class Parameter(
    override val documentation: String?,
    val name: String,
    val type: KtTypeName,
  ) : KtDeclaration
}
