package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.io.validation.buildSymbolTable
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.expressions.AbiFunction
import dev.wasmo.brevity.kotlin.expressions.AbiInterface
import dev.wasmo.brevity.kotlin.expressions.AbiResource
import dev.wasmo.brevity.kotlin.expressions.AbiWorld
import dev.wasmo.brevity.kotlin.expressions.ApiFunctionFactory
import dev.wasmo.brevity.kotlin.expressions.PlatformFunctionFactory
import okio.FileSystem
import okio.Path

/**
 * A precompiled set of source, analyzed and ready to emit code.
 */
class WitBridgeGenerator private constructor(
  val guest: GuestGenerator,
  val host: HostGenerator,
  val api: ApiGenerator,
  val roleTracker: RoleTracker,
) {
  fun generate(): ProjectSpec {
    val specs = buildList {
      addAll(host.generate())
      addAll(guest.generate())
      addAll(api.generate())
    }

    return ProjectSpec(specs)
  }

  interface Validation {
    context(issueCollector: IssueCollector)
    fun validate(declarationIndex: DeclarationIndex)
  }

  companion object {
    /**
     * Perform all necessary precompilation analysis and validation necessary to generate code.
     */
    context(issueCollector: IssueCollector)
    fun precompile(
      fileSystem: FileSystem,
      packageDirectories: Collection<Path>,
      irFilter: (List<IrWitPackage>) -> List<IrWitPackage> = { it },
      validations: List<Validation> = listOf(RecursionValidator()),
      customTypeMappings: Map<String, String> = mapOf(),
      supportAsync: Boolean = true,
    ): WitBridgeGenerator? = with(issueCollector) {

      val packageReader = collectNoIssuesOrThrow { IoWitPackageReader(fileSystem) }

      val ioToplevelPackages = packageDirectories.map { directory ->
        packageReader.read(directory)
      }

      // Abort if any errors were reported during parsing. We must not proceed to linking as the
      // top-level symbols may contain placeholders.
      throwIfNotEmpty()

      val symbolTable = ioToplevelPackages.buildSymbolTable()
        ?: return null

      val irMapper = IrMapper(ioToplevelPackages, symbolTable)

      val irPackages = irFilter(irMapper.map())

      val declarationIndex = DeclarationIndex(irPackages)
      val roleTracker = RoleTracker(declarationIndex, irPackages)

      val kotlinMapper = KotlinMapper(
        customTypeMappings = customTypeMappings.entries.associate { (key, value) ->
          val typeName = declarationIndex.getDeclaredType(key)
            ?: error("WIT type not found: '$key'")
          val ktTypeName = ClassName.bestGuess(value)
          typeName to ktTypeName
        },
      )
      validations.forEach { it.validate(declarationIndex) }
      val apiFunctionFactory = ApiFunctionFactory()

      val guestGenerator = guestGenerator(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        roleTracker = roleTracker,
        irPackages = irPackages,
        apiFunctionFactory = apiFunctionFactory,
      )
      val hostGenerator = hostGenerator(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        roleTracker = roleTracker,
        irPackages = irPackages,
        apiFunctionFactory = apiFunctionFactory,
      )
      val apiGenerator = apiGenerator(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        roleTracker = roleTracker,
        irPackages = irPackages,
        apiFunctionFactory = apiFunctionFactory,
      )
      WitBridgeGenerator(
        guest = guestGenerator,
        host = hostGenerator,
        api = apiGenerator,
        roleTracker = roleTracker,
      )
    }

    private fun hostGenerator(
      kotlinMapper: KotlinMapper,
      declarationIndex: DeclarationIndex,
      roleTracker: RoleTracker,
      irPackages: List<IrWitPackage>,
      apiFunctionFactory: ApiFunctionFactory,
    ): HostGenerator {
      val platform = HostPlatform(kotlinMapper)
      val encoderFactory = EncoderFactory(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        platform = platform,
      )
      val abiFunctionFactory = AbiFunction.Factory(
        kotlinMapper = kotlinMapper,
        encoderFactory = encoderFactory,
      )
      val abiInterfaceFactory = AbiInterface.Factory(
        declarationIndex = declarationIndex,
        abiFunctionFactory = abiFunctionFactory,
      )
      val abiWorldFactory = AbiWorld.Factory(
        abiFunctionFactory = abiFunctionFactory,
        abiInterfaceFactory = abiInterfaceFactory,
      )
      val abiResourceFactory = AbiResource.Factory(
        kotlinMapper = kotlinMapper,
        abiFunctionFactory = abiFunctionFactory,
        roleTracker = roleTracker,
      )
      val worlds = abiWorldFactory.createAll(irPackages)
      val resources = abiResourceFactory.createAll(irPackages)
      val platformFunctionFactory = PlatformFunctionFactory(
        apiFunctionFactory = apiFunctionFactory,
        platform = platform,
      )
      val declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
        encoderFactory = encoderFactory,
        platform = platform,
      )
      return HostGenerator(
        platformFunctionFactory = platformFunctionFactory,
        hostPlatform = platform,
        declaredTypeEncodersGenerator = declaredTypeEncodersGenerator,
        roleTracker = roleTracker,
        packages = irPackages,
        worlds = worlds,
        resources = resources,
      )
    }

    private fun guestGenerator(
      kotlinMapper: KotlinMapper,
      declarationIndex: DeclarationIndex,
      roleTracker: RoleTracker,
      irPackages: List<IrWitPackage>,
      apiFunctionFactory: ApiFunctionFactory,
    ): GuestGenerator {
      val platform = GuestPlatform(kotlinMapper)
      val encoderFactory = EncoderFactory(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        platform = platform,
      )
      val abiFunctionFactory = AbiFunction.Factory(
        kotlinMapper = kotlinMapper,
        encoderFactory = encoderFactory,
      )
      val abiInterfaceFactory = AbiInterface.Factory(
        declarationIndex = declarationIndex,
        abiFunctionFactory = abiFunctionFactory,
      )
      val abiWorldFactory = AbiWorld.Factory(
        abiFunctionFactory = abiFunctionFactory,
        abiInterfaceFactory = abiInterfaceFactory,
      )
      val abiResourceFactory = AbiResource.Factory(
        kotlinMapper = kotlinMapper,
        abiFunctionFactory = abiFunctionFactory,
        roleTracker = roleTracker,
      )
      val worlds = abiWorldFactory.createAll(irPackages)
      val resources = abiResourceFactory.createAll(irPackages)
      val platformFunctionFactory = PlatformFunctionFactory(
        apiFunctionFactory = apiFunctionFactory,
        platform = platform,
      )
      val declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
        encoderFactory = encoderFactory,
        platform = platform,
      )
      return GuestGenerator(
        platform = platform,
        platformFunctionFactory = platformFunctionFactory,
        declaredTypeEncodersGenerator = declaredTypeEncodersGenerator,
        roleTracker = roleTracker,
        packages = irPackages,
        worlds = worlds,
        resources = resources,
      )
    }

    private fun apiGenerator(
      kotlinMapper: KotlinMapper,
      declarationIndex: DeclarationIndex,
      roleTracker: RoleTracker,
      irPackages: List<IrWitPackage>,
      apiFunctionFactory: ApiFunctionFactory,
    ): ApiGenerator {
      val platform = HostPlatform(kotlinMapper)
      val encoderFactory = EncoderFactory(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        platform = platform,
      )
      val abiFunctionFactory = AbiFunction.Factory(
        kotlinMapper = kotlinMapper,
        encoderFactory = encoderFactory,
      )
      val abiResourceFactory = AbiResource.Factory(
        kotlinMapper = kotlinMapper,
        abiFunctionFactory = abiFunctionFactory,
        roleTracker = roleTracker,
      )
      val abiInterfaceFactory = AbiInterface.Factory(
        declarationIndex = declarationIndex,
        abiFunctionFactory = abiFunctionFactory,
      )
      val abiWorldFactory = AbiWorld.Factory(
        abiFunctionFactory = abiFunctionFactory,
        abiInterfaceFactory = abiInterfaceFactory,
      )
      val worlds = abiWorldFactory.createAll(irPackages)
      val interfaces = irPackages.flatMap { it.services }
        .filterIsInstance<IrInterface>()
        .map { abiInterfaceFactory.createForApiOnly(it) }
      val resources = abiResourceFactory.createAll(irPackages)
      return ApiGenerator(
        kotlinMapper = kotlinMapper,
        apiFunctionFactory = apiFunctionFactory,
        worlds = worlds,
        interfaces = interfaces,
        resources = resources,
      )
    }
  }
}
