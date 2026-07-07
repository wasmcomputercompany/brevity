package dev.wasmo.brevity.ir

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName

data class IrWitPackage(
  val packageDocumentation: Documentation? = null,
  val packageName: PackageName,
  val services: List<Service> = listOf(),
) {
  sealed interface Service : IrDeclaration {
    val serviceName: ServiceName
    val types: List<IrTypeDeclaration>
    val hasInstanceMembers: Boolean
  }
}

sealed interface IrDeclaration {
  val documentation: Documentation?
  val gate: Gate?
  val offset: Offset
}

sealed interface IrTypeDeclaration : IrDeclaration, IrInterface.Item, IrWorld.Item {
  val type: IrTypeName.Declared
  val name: Identifier
    get() = type.name
}

data class IrInterface(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val serviceName: ServiceName,
  val items: List<Item>,
) : IrDeclaration, IrWitPackage.Service {
  override val types: List<IrTypeDeclaration>
    get() = items.filterIsInstance<IrTypeDeclaration>()

  val functions: List<IrFunction>
    get() = items.filterIsInstance<IrFunction>()

  override val hasInstanceMembers: Boolean
    get() = items.any { it is IrFunction }

  sealed interface Item : IrDeclaration
}

data class IrWorld(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val serviceName: ServiceName,
  override val types: List<IrTypeDeclaration>,
  val imports: List<Api>,
  val exports: List<Api>,
) : IrDeclaration, IrWitPackage.Service {
  sealed interface Api : IrDeclaration
  sealed interface Item : IrDeclaration

  override val hasInstanceMembers: Boolean
    get() = imports.isNotEmpty() || exports.isNotEmpty()
}

data class IrResource(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val type: IrTypeName.Declared,
  val functions: List<IrFunction>,
) : IrTypeDeclaration

data class IrRecord(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val type: IrTypeName.Declared,
  val fields: List<IrField>,
) : IrTypeDeclaration

data class IrField(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  val name: Identifier,
  val type: IrTypeName,
) : IrDeclaration

data class IrFunction(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  val async: Boolean = false,
  val parameters: List<IrParameter>,
  val returnType: IrTypeName? = null,
  val functionName: FunctionName,
) : IrDeclaration, IrWorld.Api, IrInterface.Item

data class IrVariant(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val type: IrTypeName.Declared,
  val cases: List<IrCase>,
) : IrTypeDeclaration

data class IrEnum(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val type: IrTypeName.Declared,
  val cases: List<IrCase>,
) : IrTypeDeclaration

data class IrCase(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  val name: Identifier,
  val type: IrTypeName? = null,
) : IrDeclaration

data class IrParameter(
  val documentation: Documentation? = null,
  val offset: Offset,
  val name: Identifier,
  val type: IrTypeName,
)

data class IrFlags(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val type: IrTypeName.Declared,
  val flags: List<IrFlag>,
) : IrTypeDeclaration

data class IrFlag(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  val name: Identifier,
) : IrDeclaration

data class IrTypeAlias(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  override val type: IrTypeName.Declared,
  val target: IrTypeName,
) : IrTypeDeclaration, IrInterface.Item

data class IrExternalApi(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val offset: Offset,
  val plainName: Identifier? = null,
  val serviceName: ServiceName,
) : IrDeclaration, IrWorld.Api
