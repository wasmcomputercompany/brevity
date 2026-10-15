package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
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
  val resources: List<AbiResource>
  val functions: List<AbiFunction>
  val apiClassName: ClassName
  val types: List<IrTypeDeclaration>

  val documentation: Documentation?
    get() = irDeclaration.documentation
  val location: Location
    get() = irDeclaration.location

  fun apiInterfaceName(orientation: Orientation): ClassName = apiClassName
}

data class AbiWorld(
  override val irDeclaration: IrWorld,
  val interfaces: List<AbiInterface>,
  override val resources: List<AbiResource>,
  override val functions: List<AbiFunction>,
) : AbiService {
  override val apiClassName: ClassName
    get() = irDeclaration.serviceName.kotlinApi
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
    private val abiInterfaceFactory: AbiInterface.Factory,
    private val abiResourceFactory: AbiResource.Factory,
    private val abiFunctionFactory: AbiFunction.Factory,
  ) {
    fun createAll(packages: List<IrWitPackage>): List<AbiWorld> {
      return packages.flatMap { it.services }
        .filterIsInstance<IrWorld>()
        .map { createWorld(it) }
    }

    private fun createWorld(irWorld: IrWorld): AbiWorld {
      val abiInterfaces = mutableListOf<AbiInterface>()
      val abiFunctions = mutableListOf<AbiFunction>()

      for (api in irWorld.exports) {
        when (api) {
          is IrExternalApi -> abiInterfaces += abiInterfaceFactory.create(
            worldServiceName = irWorld.serviceName,
            externalApi = api,
            orientation = Export,
          )

          is IrFunction -> abiFunctionFactory.collectAll(
            listBuilder = abiFunctions,
            irFunction = api,
            orientation = Export,
            isResource = false,
          )
        }
      }
      for (api in irWorld.imports) {
        when (api) {
          is IrExternalApi -> abiInterfaces += abiInterfaceFactory.create(
            worldServiceName = irWorld.serviceName,
            externalApi = api,
            orientation = Import,
          )

          is IrFunction -> abiFunctionFactory.collectAll(
            listBuilder = abiFunctions,
            irFunction = api,
            orientation = Import,
            isResource = false,
          )
        }
      }

      return AbiWorld(
        irDeclaration = irWorld,
        interfaces = abiInterfaces,
        resources = abiResourceFactory.createAll(
          types = irWorld.types,
          orientation = Import, // World member resources are imported.
        ),
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
  override val resources: List<AbiResource>,
  override val functions: List<AbiFunction>,
) : AbiService {
  val serviceName: ServiceName
    get() = irDeclaration.serviceName
  override val apiClassName: ClassName
    get() = serviceName.kotlinApi
  override val types: List<IrTypeDeclaration>
    get() = irDeclaration.types

  class Factory(
    private val declarationIndex: DeclarationIndex,
    private val abiResourceFactory: AbiResource.Factory,
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
        resources = abiResourceFactory.createAll(irInterface.types, orientation),
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
      resources = abiResourceFactory.createAll(
        types = irInterface.types,
        orientation = Import, // Arbitrary.
      ),
      functions = abiFunctionFactory.createAll(
        functions = irInterface.functions,
        orientation = Import,
        includeSyntheticFunctions = false,
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
  override val types: List<IrTypeDeclaration>
    get() = listOf()
  override val resources: List<AbiResource>
    get() = listOf()

  class Factory(
    private val kotlinMapper: KotlinMapper,
    private val abiFunctionFactory: AbiFunction.Factory,
  ) {
    fun createForApiOnly(
      packages: List<IrWitPackage>,
    ) = createAll(
      types = packages.flatMap { it.services }.flatMap { it.types },
      orientation = Import, // Arbitrary.
    )

    fun createAll(
      types: List<IrTypeDeclaration>,
      orientation: Orientation,
    ) = buildList {
      val irResources = types.filterIsInstance<IrResource>()
      for (resource in irResources) {
        add(create(resource, orientation))
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
