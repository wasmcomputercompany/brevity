package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.io.IoFunction
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld

/**
 * Directly converts WIT model types ([IoWorld], [IoFunction], etc.) to a Kotlin equivalents
 * ([KtWorld], [KtFunction], etc.).
 */
class KtMapper {
  fun map(witPackages: List<IrWitPackage>): List<KtService> {
    return witPackages.flatMap { witPackage ->
      witPackage.services.mapNotNull { declaration ->
        declaration.toServiceNoCodecs()
      }
    }
  }

  internal fun IrWitPackage.Service.toServiceNoCodecs(): KtService? {
    return when (this) {
      is IrInterface -> interfaceToKt()
      is IrWorld -> worldToKt()
    }
  }

  internal fun IrTypeDeclaration.typeDeclarationToKt(): KtTypeDeclaration {
    return when (this) {
      is IrEnum -> enumToKt()
      is IrFlags -> flagsToKt()
      is IrRecord -> recordToKt()
      is IrResource -> resourceToKt()
      is IrTypeAlias -> typeAliasToKt()
      is IrVariant -> variantToKt()
    }
  }

  internal fun IrInterface.interfaceToKt() = KtInterface(
    documentation = documentation?.content?.trimIndent(),
    type = serviceName.kotlinApi,
    serviceName = serviceName,
    functions = items.filterIsInstance<IrFunction>().map { it.functionToKt() },
    types = items.filterIsInstance<IrTypeDeclaration>().map { it.typeDeclarationToKt() },
  )

  internal fun IrRecord.recordToKt() = KtRecord(
    documentation = documentation?.content,
    type = type,
    fields = fields.map { field ->
      KtRecord.Field(
        documentation = field.documentation?.content,
        name = field.name.toCamelCase(upperCamel = false),
        irType = field.type,
      )
    },
  )

  internal fun IrResource.resourceToKt() = KtResource(
    documentation = documentation?.content,
    type = type,
    functions = functions.map { it.functionToKt() },
  )

  internal fun IrTypeAlias.typeAliasToKt() = KtTypeAlias(
    documentation = documentation?.content,
    type = type,
    irTarget = target,
  )

  internal fun IrVariant.variantToKt() = KtVariant(
    documentation = documentation?.content,
    type = type,
    cases = cases.map { case ->
      KtVariant.Case(
        documentation = case.documentation?.content,
        name = case.name.toCamelCase(upperCamel = true),
        irType = case.type,
      )
    },
  )

  internal fun IrEnum.enumToKt() = KtEnum(
    documentation = documentation?.content,
    type = type,
    cases = cases.map {
      check(it.type == null)
      KtEnum.Case(
        documentation = it.documentation?.content,
        name = it.name.toCamelCase(upperCamel = true),
      )
    },
  )

  internal fun IrFlags.flagsToKt() = KtFlags(
    documentation = documentation?.content,
    type = type,
    flags = flags.map { flag ->
      KtFlags.Flag(
        documentation = flag.documentation?.content,
        name = flag.name.toCamelCase(upperCamel = false),
      )
    },
  )

  internal fun IrFunction.functionToKt() = KtFunction(
    documentation = documentation?.content,
    ktName = functionName.name.toCamelCase(upperCamel = false),
    name = functionName,
    parameters = parameters.map { parameter ->
      KtFunction.Parameter(
        documentation = parameter.documentation?.content,
        name = parameter.name.toCamelCase(upperCamel = false),
        irType = parameter.type,
      )
    },
    returnType = returnType,
  )

  internal fun IrWorld.worldToKt(): KtService {
    val kotlinName = serviceName.kotlinApi
    val guestApis = KtWorld.ExternalApis(
      instanceName = "guest",
      type = kotlinName.nestedClass("Guest"),
      items = exports.map { it.worldApiToKt() },
    ).takeIf { it.items.isNotEmpty() }

    val hostApis = KtWorld.ExternalApis(
      instanceName = "host",
      type = kotlinName.nestedClass("Host"),
      items = imports.map { it.worldApiToKt() },
    ).takeIf { it.items.isNotEmpty() }

    return KtWorld(
      documentation = documentation?.content?.trimIndent(),
      type = kotlinName,
      serviceName = serviceName,
      guestApis = guestApis,
      hostApis = hostApis,
      types = types.map { it.typeDeclarationToKt() },
    )
  }

  private fun IrWorld.Api.worldApiToKt(): KtWorld.ExternalApis.Item {
    return when (this) {
      is IrExternalApi -> KtWorld.ExternalApis.InterfaceProperty(
        documentation = documentation?.content?.trimIndent(),
        instanceName = (plainName ?: path.name).toCamelCase(upperCamel = false),
        type = path.kotlinApi,
        serviceName = path,
      )

      is IrFunction -> functionToKt()
    }
  }
}
