package dev.wasmo.brevity.ir

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.io.IoCase
import dev.wasmo.brevity.io.IoEnum
import dev.wasmo.brevity.io.IoExternalApi
import dev.wasmo.brevity.io.IoField
import dev.wasmo.brevity.io.IoFlag
import dev.wasmo.brevity.io.IoFlags
import dev.wasmo.brevity.io.IoFunction
import dev.wasmo.brevity.io.IoInclude
import dev.wasmo.brevity.io.IoInlinePackage
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoParameter
import dev.wasmo.brevity.io.IoRecord
import dev.wasmo.brevity.io.IoResource
import dev.wasmo.brevity.io.IoTopLevelUse
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoTypeAlias
import dev.wasmo.brevity.io.IoTypeDeclaration
import dev.wasmo.brevity.io.IoTypeName
import dev.wasmo.brevity.io.IoUse
import dev.wasmo.brevity.io.IoVariant
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWitPackage
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.io.UsePath
import dev.wasmo.brevity.io.validation.IoSymbolTable
import dev.wasmo.brevity.io.validation.buildSymbolTable

class IrMapper(
  private val packages: List<IoToplevelWitPackage>,
  private val ioSymbolTable: IoSymbolTable,
) {
  private val irPackages = mutableMapOf<PackageName, PackageBuilder>()

  internal class PackageBuilder {
    val documentation = mutableListOf<Documentation>()
    val services = mutableListOf<IrWitPackage.Service>()
  }

  context(issueCollector: IssueCollector)
  fun map(): List<IrWitPackage> {
    check(irPackages.isEmpty())

    for (ioPackage in packages) {
      addPackage(ioPackage)
    }

    return irPackages.map { (name, builder) ->
      IrWitPackage(
        packageDocumentation = when {
          builder.documentation.isEmpty() -> null
          else -> Documentation(builder.documentation.joinToString("\n") { it.content })
        },
        packageName = name,
        services = builder.services,
      )
    }
  }

  context(issueCollector: IssueCollector)
  private fun addPackage(ioPackage: IoWitPackage) {
    val builder = irPackages.getOrPut(ioPackage.packageName) { PackageBuilder() }

    context(builder) {
      for (item in ioPackage.items) {
        when (item) {
          is IoInterface -> item.interfaceToIr(ioPackage.packageName)
          is IoWorld -> item.worldToIr(ioPackage.packageName)
          is IoTopLevelUse -> {}
          is IoInlinePackage -> addPackage(item)
        }
      }
    }
  }

  context(builder: PackageBuilder, issueCollector: IssueCollector)
  private fun IoInterface.interfaceToIr(packageName: PackageName) {
    val serviceName = ServiceName(packageName, name)
    context(Context(serviceName)) {
      builder.services += IrInterface(
        documentation = documentation,
        gate = gate,
        location = location,
        serviceName = serviceName,
        items = items.mapNotNull { item ->
          item.interfaceItemToIrOrNull()
        },
      )
    }
  }

  context(context: Context, issueCollector: IssueCollector)
  private fun IoInterface.Item.interfaceItemToIrOrNull(): IrInterface.Item? {
    return when (this) {
      is IoFunction -> functionToIr()
      is IoEnum -> enumToIr()
      is IoFlags -> flagsToIr()
      is IoRecord -> recordToIr()
      is IoResource -> resourceToIr()
      is IoTypeAlias -> typeAliasToIr()
      is IoVariant -> variantToIr()
      is IoUse -> null
    }
  }

  context(context: Context, issueCollector: IssueCollector)
  private fun IoCase.caseToIr() = IrCase(
    documentation = documentation,
    gate = gate,
    location = location,
    name = name,
    type = type?.typeNameToIr(location),
  )

  context(context: Context, issueCollector: IssueCollector)
  private fun IoField.fieldToIr() = type.typeNameToIr(location)?.let { resolved ->
    IrField(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      type = resolved,
    )
  }

  context(context: Context)
  private fun IoFlag.flagToIr() = IrFlag(
    documentation = documentation,
    gate = gate,
    location = location,
    name = name,
  )

  context(context: Context, issueCollector: IssueCollector)
  private fun IoFunction.functionToIr(
    worldFunction: Boolean = false,
    resourceName: Identifier? = null,
  ) = IrFunction(
    documentation = documentation,
    gate = gate,
    location = location,
    async = async,
    parameters = parameters.mapNotNull { it.parameterToIr() },
    returnType = returnType?.typeNameToIr(location),
    functionName = when {
      worldFunction -> FunctionName.World(
        name = name,
      )

      constructor && resourceName != null -> FunctionName.Constructor(
        serviceName = context.serviceName,
        name = resourceName,
      )

      static && resourceName != null -> FunctionName.Static(
        serviceName = context.serviceName,
        resourceName = resourceName,
        name = name,
      )

      resourceName != null -> FunctionName.Method(
        serviceName = context.serviceName,
        resourceName = resourceName,
        name = name,
      )

      else -> FunctionName.Interface(
        serviceName = context.serviceName,
        name = name,
      )
    },
  )

  context(context: Context)
  private fun IoResource.dropFunction(): IrFunction {
    return IrFunction(
      location = location,
      functionName = FunctionName.ResourceDrop(
        serviceName = context.serviceName,
        resourceName = name,
      ),
    )
  }

  context(context: Context, issueCollector: IssueCollector)
  private fun IoParameter.parameterToIr() = type.typeNameToIr(location)?.let { resolved ->
    IrParameter(
      documentation = documentation,
      location = location,
      name = name,
      type = resolved,
    )
  }

  context(context: Context, issueCollector: IssueCollector)
  private fun IoEnum.enumToIr() = IrEnum(
    documentation = documentation,
    gate = gate,
    location = location,
    type = TypeName.Declared(context.serviceName, name),
    cases = cases.map { it.caseToIr() },
  )

  context(context: Context)
  private fun IoFlags.flagsToIr() = IrFlags(
    documentation = documentation,
    gate = gate,
    location = location,
    type = TypeName.Declared(context.serviceName, name),
    flags = flags.map { it.flagToIr() },
  )

  context(context: Context, issueCollector: IssueCollector)
  private fun IoRecord.recordToIr() = IrRecord(
    documentation = documentation,
    gate = gate,
    location = location,
    type = TypeName.Declared(context.serviceName, name),
    fields = fields.mapNotNull { it.fieldToIr() },
  )

  context(context: Context, issueCollector: IssueCollector)
  private fun IoResource.resourceToIr() = IrResource(
    documentation = documentation,
    gate = gate,
    location = location,
    type = TypeName.Declared(context.serviceName, name),
    functions = buildList {
      addAll(
        functions.map {
          it.functionToIr(resourceName = name)
        },
      )
      add(dropFunction())
    },
  )

  context(context: Context, issueCollector: IssueCollector)
  private fun IoTypeAlias.typeAliasToIr() = target.typeNameToIr(location)?.let { resolvedTarget ->
    IrTypeAlias(
      documentation = documentation,
      gate = gate,
      location = location,
      type = TypeName.Declared(context.serviceName, name),
      target = resolvedTarget,
    )
  }

  context(context: Context, issueCollector: IssueCollector)
  private fun IoVariant.variantToIr() = IrVariant(
    documentation = documentation,
    gate = gate,
    location = location,
    type = TypeName.Declared(context.serviceName, name),
    cases = cases.map { it.caseToIr() },
  )

  context(context: Context)
  private fun IoExternalApi.externalUsePathToIr(): IrExternalApi {
    val serviceName = path.usePathToIr()
    return IrExternalApi(
      documentation = documentation,
      gate = gate,
      location = location,
      plainName = plainName,
      serviceName = serviceName,
    )
  }

  context(context: Context)
  private fun UsePath.usePathToIr() = ServiceName(
    packageName = packageName ?: context.serviceName.packageName,
    name = name,
  )

  /**
   * Resolve an [IoTypeName] to an IR [TypeName].
   *
   * Typename resolution can fail, yielding null and raising an issue. It's still possible to
   * build an IR tree under these circumstances by propagating nulls and dropping values from lists,
   * but obviously that IR tree is no longer an accurate representation of the source.
   */
  context(context: Context, issueCollector: IssueCollector)
  internal fun IoTypeName.typeNameToIr(referenceSite: Location): TypeName? {
    return when (this) {
      IoTypeName.Bool -> TypeName.Bool
      IoTypeName.S8 -> TypeName.S8
      IoTypeName.S16 -> TypeName.S16
      IoTypeName.S32 -> TypeName.S32
      IoTypeName.S64 -> TypeName.S64
      IoTypeName.U8 -> TypeName.U8
      IoTypeName.U16 -> TypeName.U16
      IoTypeName.U32 -> TypeName.U32
      IoTypeName.U64 -> TypeName.U64
      IoTypeName.F32 -> TypeName.F32
      IoTypeName.F64 -> TypeName.F64
      IoTypeName.Char -> TypeName.Char
      IoTypeName.String -> TypeName.String
      is IoTypeName.Borrow -> type.typeNameToIr(referenceSite)?.let { TypeName.Borrow(it) }
      is IoTypeName.Declared -> declaredTypeToIr(referenceSite)
      is IoTypeName.Future -> type?.typeNameToIr(referenceSite)?.let { TypeName.Future(it) }
      is IoTypeName.List -> type.typeNameToIr(referenceSite)?.let { TypeName.List(it, size) }
      is IoTypeName.Map -> (key.typeNameToIr(referenceSite) to value.typeNameToIr(referenceSite)).let { (resolvedKey, resolvedValue) ->
        if (resolvedKey != null && resolvedValue != null) {
          TypeName.Map(resolvedKey, resolvedValue)
        } else {
          null
        }
      }
      is IoTypeName.Option -> type.typeNameToIr(referenceSite)?.let { TypeName.Option(it) }
      is IoTypeName.Result -> (ok?.typeNameToIr(referenceSite) to error?.typeNameToIr(referenceSite)).let { (resolvedOk, resolvedError) ->
        TypeName.Result(resolvedOk, resolvedError)
      }
      is IoTypeName.Stream -> TypeName.Stream(type?.typeNameToIr(referenceSite))
      is IoTypeName.Tuple -> TypeName.Tuple(types.mapNotNull { it.typeNameToIr(referenceSite) })
    }
  }

  context(context: Context, issueCollector: IssueCollector)
  internal fun IoTypeName.Declared.declaredTypeToIr(referenceSite: Location): TypeName? {
    fun reportIssue() {
      issueCollector.report(Issue(
        "unable to find ${this@declaredTypeToIr} in ${context.serviceName}",
        referenceSite,
      ))
    }
    val witPackage = ioSymbolTable[context.serviceName.packageName] ?: run {
      reportIssue()
      return null
    }
    val declarations = sequence {
      ioSymbolTable[context.serviceName]?.let { service ->
        when (service) {
          is IoInterface -> yieldAll(service.items)
          is IoWorld -> yieldAll(service.items)
        }
      }
    }

    for (declaration in declarations) {
      when (declaration) {
        is IoTypeDeclaration -> {
          // Direct match.
          if (declaration.name == name) {
            return TypeName.Declared(
              serviceName = ServiceName(witPackage.packageName, context.serviceName.name),
              name = declaration.name,
            )
          }
        }

        is IoUse -> {
          // Matched a 'use' statement that refers to another symbol.
          val itemMatch = declaration.items.firstOrNull { it.matches(this) }
          if (itemMatch != null) {
            val useContext = Context(
              ServiceName(
                packageName = declaration.path.packageName ?: context.serviceName.packageName,
                name = declaration.path.name,
              ),
            )
            context(useContext) {
              return itemMatch.type.declaredTypeToIr(itemMatch.location)
            }
          }
        }

        else -> {}
      }
    }

    reportIssue()

    return null
  }

  /** Collect includes recursively. */
  context(builder: PackageBuilder, issueCollector: IssueCollector)
  private fun IoWorld.worldToIr(packageName: PackageName) {
    val seed = IncludedWorld(
      packageName = packageName,
      world = this,
    )

    val set = LinkedHashSet<IncludedWorld>()
    seed.collectIncludesRecursively(set)

    builder.services += IrWorld(
      documentation = documentation,
      gate = gate,
      location = location,
      serviceName = ServiceName(packageName, name),
      types = set.flatMap { included ->
        context(included.context) {
          included.world.items.mapNotNull { it.worldItemToIrTypeDeclarationOrNull() }
        }
      },
      imports = set.flatMap { included ->
        context(included.context) {
          included.world.imports.mapNotNull { it.worldApiToIr() }
        }
      },
      exports = set.flatMap { included ->
        context(included.context) {
          included.world.exports.mapNotNull { it.worldApiToIr() }
        }
      },
    )
  }

  context(context: Context, issueCollector: IssueCollector)
  private fun IoWorld.Item.worldItemToIrTypeDeclarationOrNull(): IrTypeDeclaration? {
    return when (this) {
      is IoInclude -> null
      is IoEnum -> enumToIr()
      is IoFlags -> flagsToIr()
      is IoRecord -> recordToIr()
      is IoResource -> resourceToIr()
      is IoTypeAlias -> typeAliasToIr()
      is IoVariant -> variantToIr()
      is IoUse -> null
    }
  }

  /**
   * Returns null if the external API doesn't declare any functions. This is perfectly reasonable
   * to express in WIT, but not useful for generating bindings. (It also triggers name collisions
   * because some WASI worlds import multiple 'types' interfaces.)
   */
  context(context: Context, builder: PackageBuilder, issueCollector: IssueCollector)
  private fun IoWorld.Api.worldApiToIr(): IrWorld.Api? {
    return when (this) {
      is IoExternalApi -> externalUsePathToIr()
        .takeIf { getInterfaceOrNull(it.serviceName.usePath)?.declaresApis() ?: false }

      is IoFunction -> functionToIr(worldFunction = true)
      is IoInterface -> {
        if (!declaresApis()) return null
        interfaceToIr(context.serviceName.packageName)
        IrExternalApi(
          location = location,
          plainName = name,
          serviceName = ServiceName(context.serviceName.packageName, name),
        )
      }
    }
  }

  context(issueCollector: IssueCollector)
  private fun IncludedWorld.collectIncludesRecursively(
    set: MutableSet<IncludedWorld>,
  ) {
    if (!set.add(this)) return // Duplicate.

    for (include in world.items.filterIsInstance<IoInclude>()) {
      val packageName = include.path.packageName ?: packageName
      val lookupPath = include.path.copy(
        packageName = packageName,
      )

      val world = getWorldOrNull(lookupPath)

      if (world == null) {
        issueCollector.report(
          Issue(
            "Unable to find world $lookupPath included by ${this@collectIncludesRecursively}",
            include.location,
          )
        )
      } else {
        IncludedWorld(
          packageName = packageName,
          world = world,
        ).collectIncludesRecursively(
          set = set,
        )
      }
    }
  }

  internal fun getWorldOrNull(path: UsePath): IoWorld? {
    val witPackage = ioSymbolTable[path.packageName] ?: return null
    return witPackage.items
      .filterIsInstance<IoWorld>()
      .singleOrNull { it.name == path.name }
  }

  internal fun getInterfaceOrNull(path: UsePath): IoInterface? {
    val witPackage = ioSymbolTable[path.packageName] ?: return null
    return witPackage.items
      .filterIsInstance<IoInterface>()
      .singleOrNull { it.name == path.name }
  }

  internal class Context(
    val serviceName: ServiceName,
  ) {
    override fun toString() = serviceName.toString()
  }

  private data class IncludedWorld(
    val packageName: PackageName,
    val world: IoWorld,
  ) {
    val context: Context
      get() = Context(ServiceName(packageName, world.name))

    override fun toString() = context.toString()
  }
}

private fun IoUse.Item.matches(typeName: IoTypeName.Declared): Boolean {
  return when {
    alias != null -> alias == typeName.name
    else -> type == typeName
  }
}

private val IoWitPackage.items: List<IoWitFile.Item>
  get() = when (this) {
    is IoToplevelWitPackage -> files.flatMap { it.items }
    is IoInlinePackage -> declarations
  }

/**
 * Returns true if this interface declares functions to be imported or exported.
 *
 * TODO: probably also return true if any [IoResource] member has a static function or constructor.
 */
private fun IoInterface.declaresApis(): Boolean =
  items.any { it is IoFunction }
