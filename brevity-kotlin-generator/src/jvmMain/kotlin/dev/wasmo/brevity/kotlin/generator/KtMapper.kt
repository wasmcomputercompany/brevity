package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
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
 * ([KtService], [KtFunction], etc.).
 */
class KtMapper(
  private val kotlinPackagePrefix: String = "wit",
) {
  private val typeMapper = TypeMapper(kotlinPackagePrefix)

  fun map(witPackages: List<IrWitPackage>): List<KtService> {
    val servicesNoCodecs = witPackages.flatMap { witPackage ->
      val kotlinName = witPackage.packageName.toKotlin(kotlinPackagePrefix)
      context(Context(kotlinName, NameAllocator())) {
        witPackage.items.mapNotNull { declaration ->
          declaration.toServiceNoCodecs()
        }
      }
    }

    val typeIndex = sequence { servicesNoCodecs.yieldTypeDeclarations() }
      .associateBy { it.type }

    return servicesNoCodecs.map { service ->
      service.plusCodecs(typeIndex)
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
  internal fun IrInterface.interfaceToKt(): KtService? {
    val kotlinName = context.kotlinName + name
    context(context.copy(kotlinName = kotlinName)) {
      return KtService(
        kind = KtService.Kind.Interface,
        documentation = documentation?.content?.trimIndent(),
        type = kotlinName.name,
        instanceName = name.name.toCamelCase(upperCamel = false),
        functions = items.filterIsInstance<IrFunction>().map { it.functionToKt() },
        types = items.filterIsInstance<IrTypeDeclaration>().map { it.typeDeclarationToKt() },
      ).toNullIfEmpty()
    }
  }

  context(context: Context)
  internal fun IrRecord.recordToKt() = KtRecord(
    documentation = documentation?.content,
    type = (context.kotlinName + name).name,
    fields = fields.map { field ->
      KtRecord.Field(
        documentation = field.documentation?.content,
        name = field.name.name.toCamelCase(upperCamel = false),
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
        name = case.name.name.toCamelCase(upperCamel = true),
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
        name = it.name.name.toCamelCase(upperCamel = true),
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
        name = flag.name.name.toCamelCase(upperCamel = false),
      )
    },
  )

  context(context: Context)
  internal fun IrFunction.functionToKt() = KtFunction(
    documentation = documentation?.content,
    ktName = functionName.name.name.toCamelCase(upperCamel = false),
    name = functionName,
    parameters = parameters.map { parameter ->
      KtFunction.Parameter(
        documentation = parameter.documentation?.content,
        name = parameter.name.name.toCamelCase(upperCamel = false),
        type = typeMapper.map(parameter.type),
      )
    },
    returnType = returnType?.let { typeMapper.map(it) },
  )

  context(context: Context)
  internal fun IrWorld.worldToKt(): KtService? {
    val kotlinName = context.kotlinName + name

    val guestName = kotlinName + Identifier("Guest")
    val guest = context(context.copy(kotlinName = guestName)) {
      KtService(
        kind = KtService.Kind.Guest,
        instanceName = "guest",
        type = guestName.name,
        functions = exports.filterIsInstance<IrFunction>().map { it.functionToKt() },
        services = exports.mapNotNull { it.worldApiToKt() },
      ).toNullIfEmpty()
    }

    val hostName = kotlinName + Identifier("Host")
    val host = context(context.copy(kotlinName = hostName)) {
      KtService(
        kind = KtService.Kind.Host,
        instanceName = "host",
        type = hostName.name,
        functions = imports.filterIsInstance<IrFunction>().map { it.functionToKt() },
        services = imports.mapNotNull { it.worldApiToKt() },
      ).toNullIfEmpty()
    }

    return KtService(
      kind = KtService.Kind.World,
      documentation = documentation?.content?.trimIndent(),
      instanceName = name.name.toCamelCase(upperCamel = false),
      type = kotlinName.name,
      services = listOfNotNull(guest, host),
      types = context(context.copy(kotlinName = kotlinName)) {
        items.filterIsInstance<IrTypeDeclaration>().map { it.typeDeclarationToKt() }
      },
    ).toNullIfEmpty()
  }

  context(context: Context)
  private fun IrWorld.Api.worldApiToKt(): KtService? {
    return when (this) {
      is IrExternalApi -> KtService(
        kind = KtService.Kind.Interface,
        documentation = documentation?.content?.trimIndent(),
        instanceName = (plainName ?: path.name).name.toCamelCase(upperCamel = false),
        type = typeMapper.map(path),
        functions = functions.map { it.functionToKt() },
      ).toNullIfEmpty()

      is IrInterface -> interfaceToKt()
      is IrFunction -> null
    }
  }

  internal data class Context(
    val kotlinName: KotlinName,
    val nameAllocator: NameAllocator,
  )
}

internal fun KtService.toNullIfEmpty(): KtService? {
  return when {
    functions.isEmpty() && services.isEmpty() && types.isEmpty() -> null
    else -> this
  }
}

/**
 * Returns a copy of this service with the [KtService.codecs] field populated. We do this as a
 * follow-up step because we need to index the converted [KtTypeDeclaration]s before we can
 * collect the codecs.
 */
private fun KtService.plusCodecs(
  typeIndex: Map<ClassName, KtTypeDeclaration>,
) = copy(
  codecs = buildList {
    val hostToGuestTypes = hostToGuestTypes.toSet()
    val guestToHostTypes = guestToHostTypes.toSet()
    for (type in (hostToGuestTypes + guestToHostTypes).toSet()) {
      add(
        KtService.KtCodec(
          declaration = typeIndex[type.apiType] ?: continue,
          hostToGuest = type in hostToGuestTypes,
          guestToHost = type in guestToHostTypes,
        ),
      )
    }
  },
)

context(scope: SequenceScope<KtTypeDeclaration>)
private suspend fun Iterable<KtService>.yieldTypeDeclarations() {
  for (service in this) {
    service.yieldTypeDeclarations()
  }
}

context(scope: SequenceScope<KtTypeDeclaration>)
private suspend fun KtService.yieldTypeDeclarations() {
  for (declaration in types) {
    scope.yield(declaration)
  }
  services.yieldTypeDeclarations()
}

private val KtService.guestToHostTypes: Sequence<KtTypeName>
  get() = sequence {
    for (service in services) {
      yieldAll(service.guestToHostTypes)
    }
    when (kind) {
      KtService.Kind.Guest -> yieldAll(parameterTypes)
      KtService.Kind.Host -> yieldAll(returnValueTypes)
      else -> {}
    }
  }

private val KtService.hostToGuestTypes: Sequence<KtTypeName>
  get() = sequence {
    for (service in services) {
      yieldAll(service.hostToGuestTypes)
    }
    when (kind) {
      KtService.Kind.Guest -> yieldAll(returnValueTypes)
      KtService.Kind.Host -> yieldAll(parameterTypes)
      else -> {}
    }
  }

private val KtService.parameterTypes: Sequence<KtTypeName>
  get() = sequence {
    for (function in functions) {
      yieldAll(function.parameters.map { it.type })
    }
    for (spec in services) {
      yieldAll(spec.parameterTypes)
    }
  }

private val KtService.returnValueTypes: Sequence<KtTypeName>
  get() = sequence {
    for (function in functions) {
      val returnType = function.returnType ?: continue
      yield(returnType)
    }
    for (spec in services) {
      yieldAll(spec.returnValueTypes)
    }
  }

private val KtTypeName.declaredTypes: Sequence<KtTypeName.Declared>
  get() = sequence {
    when (this@declaredTypes) {
      is KtTypeName.Borrow -> yieldAll(type.declaredTypes)
      is KtTypeName.Declared -> yield(this@declaredTypes)
      is KtTypeName.Future -> type?.let { yieldAll(it.declaredTypes) }

      is KtTypeName.List -> yieldAll(type.declaredTypes)
      is KtTypeName.Map -> {
        yieldAll(key.declaredTypes)
        yieldAll(value.declaredTypes)
      }

      is KtTypeName.Option -> yieldAll(type.declaredTypes)

      is KtTypeName.Result -> {
        ok?.let { okTypeName -> yieldAll(okTypeName.declaredTypes) }
        err?.let { errTypeName -> yieldAll(errTypeName.declaredTypes) }
      }

      is KtTypeName.Simple -> {}
      is KtTypeName.Stream -> type?.let { yieldAll(it.declaredTypes) }

      is KtTypeName.Tuple -> {
        for (name in types) {
          yieldAll(name.declaredTypes)
        }
      }
    }
  }
