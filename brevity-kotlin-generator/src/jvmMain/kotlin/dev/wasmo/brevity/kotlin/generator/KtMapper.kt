package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Identifier
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
class KtMapper(
  private val kotlinPackagePrefix: String = "wit",
) {
  private val typeMapper = TypeMapper(kotlinPackagePrefix)

  fun map(witPackages: List<IrWitPackage>): List<KtService> {
    return witPackages.flatMap { witPackage ->
      val kotlinName = witPackage.packageName.toKotlin(kotlinPackagePrefix)
      context(Context(kotlinName, NameAllocator())) {
        witPackage.items.mapNotNull { declaration ->
          declaration.toServiceNoCodecs()
        }
      }
    }
  }

  context(context: Context)
  internal fun IrWitPackage.Item.toServiceNoCodecs(): KtService? {
    return when (this) {
      is IrInterface -> interfaceToKt()
      is IrWorld -> worldToKt()
    }
  }

  context(context: Context)
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

  context(context: Context)
  internal fun IrInterface.interfaceToKt(): KtService {
    val kotlinName = context.kotlinName + name.name
    context(context.copy(kotlinName = kotlinName)) {
      return KtInterface(
        documentation = documentation?.content?.trimIndent(),
        type = kotlinName.name,
        instanceName = name.name.toCamelCase(upperCamel = false),
        functions = items.filterIsInstance<IrFunction>().map { it.functionToKt() },
        types = items.filterIsInstance<IrTypeDeclaration>().map { it.typeDeclarationToKt() },
      )
    }
  }

  context(context: Context)
  internal fun IrRecord.recordToKt() = KtRecord(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    fields = fields.map { field ->
      KtRecord.Field(
        documentation = field.documentation?.content,
        name = field.name.toCamelCase(upperCamel = false),
        type = typeMapper.map(field.type),
      )
    },
  )

  context(context: Context)
  internal fun IrResource.resourceToKt() = KtResource(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    functions = functions.map { it.functionToKt() },
  )

  context(context: Context)
  internal fun IrTypeAlias.typeAliasToKt() = KtTypeAlias(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    target = typeMapper.map(target),
  )

  context(context: Context)
  internal fun IrVariant.variantToKt() = KtVariant(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    cases = cases.map { case ->
      KtVariant.Case(
        documentation = case.documentation?.content,
        name = case.name.toCamelCase(upperCamel = true),
        type = case.type?.let { typeMapper.map(it) },
      )
    },
  )

  context(context: Context)
  internal fun IrEnum.enumToKt() = KtEnum(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    cases = cases.map {
      check(it.type == null)
      KtEnum.Case(
        documentation = it.documentation?.content,
        name = it.name.toCamelCase(upperCamel = true),
      )
    },
  )

  context(context: Context)
  internal fun IrFlags.flagsToKt() = KtFlags(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    flags = flags.map { flag ->
      KtFlags.Flag(
        documentation = flag.documentation?.content,
        name = flag.name.toCamelCase(upperCamel = false),
      )
    },
  )

  context(context: Context)
  internal fun IrFunction.functionToKt() = KtFunction(
    documentation = documentation?.content,
    ktName = functionName.name.toCamelCase(upperCamel = false),
    name = functionName,
    parameters = parameters.map { parameter ->
      KtFunction.Parameter(
        documentation = parameter.documentation?.content,
        name = parameter.name.toCamelCase(upperCamel = false),
        type = typeMapper.map(parameter.type),
      )
    },
    returnType = returnType?.let { typeMapper.map(it) },
  )

  context(context: Context)
  internal fun IrWorld.worldToKt(): KtService {
    val kotlinName = context.kotlinName + name.name

    val guestName = kotlinName + Identifier("Guest")
    val guestApis = context(context.copy(kotlinName = guestName)) {
      KtWorld.ExternalApis(
        instanceName = "guest",
        type = guestName.name,
        items = exports.map { it.worldApiToKt() },
      ).takeIf { it.items.isNotEmpty() }
    }

    val hostName = kotlinName + Identifier("Host")
    val hostApis = context(context.copy(kotlinName = hostName)) {
      KtWorld.ExternalApis(
        instanceName = "host",
        type = hostName.name,
        items = imports.map { it.worldApiToKt() },
      ).takeIf { it.items.isNotEmpty() }
    }

    return KtWorld(
      documentation = documentation?.content?.trimIndent(),
      instanceName = name.name.toCamelCase(upperCamel = false),
      type = kotlinName.name,
      guestApis = guestApis,
      hostApis = hostApis,
      types = buildList {
        addAll(
          context(context.copy(kotlinName = kotlinName)) {
            items.filterIsInstance<IrTypeDeclaration>().map { it.typeDeclarationToKt() }
          },
        )
      },
    )
  }

  context(context: Context)
  private fun IrWorld.Api.worldApiToKt(): KtWorld.ExternalApis.Item {
    return when (this) {
      is IrExternalApi -> KtWorld.ExternalApis.InterfaceProperty(
        documentation = documentation?.content?.trimIndent(),
        instanceName = (plainName ?: path.name).toCamelCase(upperCamel = false),
        type = typeMapper.map(path),
      )

      is IrFunction -> functionToKt()
    }
  }

  internal data class Context(
    val kotlinName: KotlinName,
    val nameAllocator: NameAllocator,
  )
}
