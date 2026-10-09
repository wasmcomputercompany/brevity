package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.generator.guestApis
import dev.wasmo.brevity.kotlin.generator.handleName
import dev.wasmo.brevity.kotlin.generator.hostApis
import dev.wasmo.brevity.kotlin.generator.instanceName
import dev.wasmo.brevity.kotlin.generator.kotlinApi

data class AbiWorld(
  val irWorld: IrWorld,
  val interfaces: List<AbiInterface>,
  override val functions: List<AbiFunction>,
) : AbiService {
  override val apiClassName: ClassName
    get() = irWorld.serviceName.kotlinApi
  override val location: Location
    get() = irWorld.location
  override val guestServiceClassName: ClassName
    get() = apiClassName.peerClass("Guest${apiClassName.simpleName}")

  override fun interfaceName(orientation: Orientation): ClassName =
    when (orientation) {
      Import -> irWorld.hostApis.type
      Export -> irWorld.guestApis.type
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
      check(world.types.filterIsInstance<IrResource>().isEmpty()) {
        "not implemented: worlds with resource members - is this allowed?!"
      }

      val abiInterfaces = mutableListOf<AbiInterface>()
      val abiFunctions = mutableListOf<AbiFunction>()

      val exportParent = FunctionParent.World(Export, world.serviceName)
      for (api in world.exports) {
        when (api) {
          is IrExternalApi -> abiInterfaces.add(
            abiInterfaceFactory.create(world.serviceName, api, Export),
          )

          is IrFunction -> abiFunctions.add(abiFunctionFactory.create(exportParent, api))
        }
      }
      val importParent = FunctionParent.World(Import, world.serviceName)
      for (api in world.imports) {
        when (api) {
          is IrExternalApi -> abiInterfaces.add(
            abiInterfaceFactory.create(world.serviceName, api, Import),
          )

          is IrFunction -> abiFunctions.add(abiFunctionFactory.create(importParent, api))
        }
      }

      return AbiWorld(
        irWorld = world,
        interfaces = abiInterfaces,
        functions = abiFunctions,
      )
    }
  }
}

data class AbiInterface(
  val irInterface: IrInterface,
  val orientation: Orientation,
  val worldServiceName: ServiceName,
  val instanceName: String,
  override val functions: List<AbiFunction>,
) : AbiService {
  val serviceName: ServiceName
    get() = irInterface.serviceName
  override val apiClassName: ClassName
    get() = serviceName.kotlinApi
  override val location: Location
    get() = irInterface.location
  override val guestServiceClassName: ClassName
    get() = ClassName(worldServiceName.kotlinApi.packageName, "Guest${apiClassName.simpleName}")

  override fun interfaceName(orientation: Orientation) = apiClassName

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
      val parent = FunctionParent.Interface(
        orientation = orientation,
        serviceName = externalApi.serviceName,
        instanceName = externalApi.instanceName,
      )
      return AbiInterface(
        irInterface = irInterface,
        orientation = parent.orientation,
        worldServiceName = worldServiceName,
        instanceName = parent.instanceName,
        functions = abiFunctionFactory.createAll(parent, irInterface.functions),
      )
    }
  }
}

data class AbiResource(
  val irResource: IrResource,
  val orientation: Orientation,
  val type: TypeName.Declared,
  override val apiClassName: ClassName,
  val handleType: ClassName,
  override val functions: List<AbiFunction>,
) : AbiService {
  override val location: Location
    get() = irResource.location
  override val guestServiceClassName: ClassName
    get() = type.handleName

  override fun interfaceName(orientation: Orientation) = apiClassName

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

    private fun create(resource: IrResource, orientation: Orientation): AbiResource {
      val parent = FunctionParent.Resource(
        orientation = orientation,
        type = resource.type,
        kotlinType = kotlinMapper.getAbiClassName(resource.type),
        handleType = resource.type.handleName,
      )
      return AbiResource(
        irResource = resource,
        orientation = parent.orientation,
        type = parent.type,
        apiClassName = parent.kotlinType,
        handleType = parent.handleType,
        functions = abiFunctionFactory.createAll(
          parent = parent,
          functions = resource.functions,
        ),
      )
    }
  }
}

sealed interface AbiService {
  val functions: List<AbiFunction>
  val apiClassName: ClassName
  val location: Location
  val guestServiceClassName: ClassName

  fun memberFunctions(orientation: Orientation) = functions
    .filter { it.orientation == orientation }

  fun interfaceName(orientation: Orientation): ClassName
}
