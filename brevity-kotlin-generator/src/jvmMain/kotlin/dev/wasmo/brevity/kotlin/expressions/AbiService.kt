package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrDeclaration
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.generator.instanceName
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase

sealed interface AbiService {
  val irDeclaration: IrDeclaration
  val functions: List<AbiFunction>
  val apiClassName: ClassName
  val hasInstanceMembers: Boolean
  val types: List<IrTypeDeclaration>

  val documentation: Documentation?
    get() = irDeclaration.documentation
  val location: Location
    get() = irDeclaration.location

  fun memberFunctions(orientation: Orientation) = functions
    .filter { it.orientation == orientation }

  fun apiInterfaceName(orientation: Orientation): ClassName = apiClassName
}

data class AbiWorld(
  override val irDeclaration: IrWorld,
  val interfaces: List<AbiInterface>,
  override val functions: List<AbiFunction>,
) : AbiService {
  override val apiClassName: ClassName
    get() = irDeclaration.serviceName.kotlinApi
  override val hasInstanceMembers: Boolean
    get() = interfaces.isNotEmpty() || functions.isNotEmpty()
  override val types: List<IrTypeDeclaration>
    get() = irDeclaration.types

  override fun apiInterfaceName(orientation: Orientation): ClassName =
    when (orientation) {
      Import -> apiClassName.nestedClass("Host")
      Export -> apiClassName.nestedClass("Guest")
    }

  fun interfaces(orientation: Orientation): List<AbiInterface> =
    interfaces.filter { it.orientation == orientation }

  class Factory(
    private val abiFunctionFactory: AbiFunction.Factory,
    private val abiInterfaceFactory: AbiInterface.Factory,
  ) {
    fun createAll(packages: List<IrWitPackage>): List<AbiWorld> {
      return packages.flatMap { it.services }
        .filterIsInstance<IrWorld>()
        .map { createWorld(it) }
    }

    private fun createWorld(world: IrWorld): AbiWorld {
      val abiInterfaces = mutableListOf<AbiInterface>()
      val abiFunctions = mutableListOf<AbiFunction>()

      for (api in world.exports) {
        when (api) {
          is IrExternalApi -> abiInterfaces += abiInterfaceFactory.create(
            worldServiceName = world.serviceName,
            externalApi = api,
            orientation = Export,
          )

          is IrFunction -> abiFunctions += abiFunctionFactory.create(
            irFunction = api,
            orientation = Export,
            isResource = false,
          )
        }
      }
      for (api in world.imports) {
        when (api) {
          is IrExternalApi -> abiInterfaces += abiInterfaceFactory.create(
            worldServiceName = world.serviceName,
            externalApi = api,
            orientation = Import,
          )
          is IrFunction -> abiFunctions += abiFunctionFactory.create(
            irFunction = api,
            orientation = Import,
            isResource = false,
          )
        }
      }

      return AbiWorld(
        irDeclaration = world,
        interfaces = abiInterfaces,
        functions = abiFunctions,
      )
    }
  }
}

/**
 * An interface that's imported or exported by a particular world, possibly with a custom plain
 * name.
 */
data class AbiInterface(
  override val irDeclaration: IrInterface,
  val orientation: Orientation,
  val worldServiceName: ServiceName,
  val instanceName: String,
  override val functions: List<AbiFunction>,
) : AbiService {
  val serviceName: ServiceName
    get() = irDeclaration.serviceName
  override val apiClassName: ClassName
    get() = serviceName.kotlinApi
  override val hasInstanceMembers: Boolean
    get() = functions.isNotEmpty()
  override val types: List<IrTypeDeclaration>
    get() = irDeclaration.types

  class Factory(
    private val declarationIndex: DeclarationIndex,
    private val abiFunctionFactory: AbiFunction.Factory,
  ) {
    fun create(
      worldServiceName: ServiceName,
      externalApi: IrExternalApi,
      orientation: Orientation,
    ): AbiInterface {
      val irInterface = declarationIndex[externalApi.serviceName] as IrInterface
      return AbiInterface(
        irDeclaration = irInterface,
        orientation = orientation,
        worldServiceName = worldServiceName,
        instanceName = externalApi.instanceName,
        functions = abiFunctionFactory.createAll(
          functions = irInterface.functions,
          orientation = orientation,
        ),
      )
    }

    /** Creates an interface that doesn't know if it's imported or exported, or by which world. */
    fun createForApiOnly(irInterface: IrInterface) = AbiInterface(
      irDeclaration = irInterface,
      orientation = Import, // Arbitrary.
      worldServiceName = irInterface.serviceName,
      instanceName = irInterface.serviceName.name.lowerCamelCase,
      functions = abiFunctionFactory.createAll(
        functions = irInterface.functions,
        orientation = Import,
      ),
    )
  }
}

data class AbiResource(
  override val irDeclaration: IrResource,
  val orientation: Orientation,
  val type: TypeName.Declared,
  override val apiClassName: ClassName,
  override val functions: List<AbiFunction>,
) : AbiService {
  override val hasInstanceMembers: Boolean
    get() = functions.isNotEmpty()
  override val types: List<IrTypeDeclaration>
    get() = listOf()

  class Factory(
    private val kotlinMapper: KotlinMapper,
    private val abiFunctionFactory: AbiFunction.Factory,
    private val roleTracker: RoleTracker,
  ) {
    fun createAll(packages: List<IrWitPackage>) = buildList {
      val irResources = packages.flatMap { it.services }
        .flatMap { it.types }
        .filterIsInstance<IrResource>()

      for (resource in irResources) {
        // TODO: this is hacked because we don't also prune unreachable callsites.
        val roles = (RoleTracker.Entry(true, true) ?: roleTracker[resource.type])!!
        if (roles.guest) {
          add(create(resource, Export))
        }
        if (roles.host) {
          add(create(resource, Import))
        }
      }
    }

    private fun create(resource: IrResource, orientation: Orientation) = AbiResource(
      irDeclaration = resource,
      orientation = orientation,
      type = resource.type,
      apiClassName = kotlinMapper.getAbiClassName(resource.type),
      functions = abiFunctionFactory.createAll(
        functions = resource.functions,
        orientation = orientation,
        isResource = true,
      ),
    )
  }
}
