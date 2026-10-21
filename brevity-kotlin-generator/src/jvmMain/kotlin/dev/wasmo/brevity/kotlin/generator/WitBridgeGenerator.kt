package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.io.validation.buildSymbolTable
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiInterface
import dev.wasmo.brevity.kotlin.abi.AbiResource
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.mapAsync
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okio.FileSystem
import okio.Path

/**
 * A precompiled set of source, analyzed and ready to emit code.
 */
class WitBridgeGenerator private constructor(
  val guest: GuestGenerator,
  val host: HostGenerator,
  val api: ApiGenerator,
) {
  suspend fun generate(): ProjectSpec {
    return coroutineScope {
      val apiSpecsDeferred = async { api.generate() }
      val guestSpecsDeferred = async { guest.generate() }
      val hostSpecsDeferred = async { host.generate() }
      ProjectSpec(
        apiSpecsDeferred.await() + guestSpecsDeferred.await() + hostSpecsDeferred.await(),
      )
    }
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
    suspend fun precompile(
      fileSystem: FileSystem,
      packageDirectories: Collection<Path>,
      irFilter: (List<IrWitPackage>) -> List<IrWitPackage> = { it },
      validations: List<Validation> = listOf(RecursionValidator()),
      customTypeMappings: Map<String, String> = mapOf(),
    ): WitBridgeGenerator? = with(issueCollector) {

      val packageReader = collectNoIssuesOrThrow { IoWitPackageReader(fileSystem) }

      val ioToplevelPackages = packageDirectories.mapAsync { directory ->
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
        irPackages = irPackages,
        apiFunctionFactory = apiFunctionFactory,
      )
      val hostGenerator = hostGenerator(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        irPackages = irPackages,
        apiFunctionFactory = apiFunctionFactory,
      )
      val apiGenerator = apiGenerator(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        irPackages = irPackages,
        apiFunctionFactory = apiFunctionFactory,
      )
      WitBridgeGenerator(
        guest = guestGenerator,
        host = hostGenerator,
        api = apiGenerator,
      )
    }

    private fun hostGenerator(
      kotlinMapper: KotlinMapper,
      declarationIndex: DeclarationIndex,
      irPackages: List<IrWitPackage>,
      apiFunctionFactory: ApiFunctionFactory,
    ): HostGenerator {
      val platform = HostPlatform
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
      )
      val abiInterfaceFactory = AbiInterface.Factory(
        declarationIndex = declarationIndex,
        abiResourceFactory = abiResourceFactory,
        abiFunctionFactory = abiFunctionFactory,
      )
      val abiWorldFactory = AbiWorld.Factory(
        abiInterfaceFactory = abiInterfaceFactory,
        abiResourceFactory = abiResourceFactory,
        abiFunctionFactory = abiFunctionFactory,
      )
      val worlds = abiWorldFactory.createAll(irPackages)
      val platformFunctionFactory = PlatformFunctionFactory(
        apiFunctionFactory = apiFunctionFactory,
        platform = platform,
      )
      val declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
        encoderFactory = encoderFactory,
      )
      return HostGenerator(
        platformFunctionFactory = platformFunctionFactory,
        hostPlatform = platform,
        declaredTypeEncodersGenerator = declaredTypeEncodersGenerator,
        packages = irPackages,
        worlds = worlds,
      )
    }

    private fun guestGenerator(
      kotlinMapper: KotlinMapper,
      declarationIndex: DeclarationIndex,
      irPackages: List<IrWitPackage>,
      apiFunctionFactory: ApiFunctionFactory,
    ): GuestGenerator {
      val platform = GuestPlatform
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
      )
      val abiInterfaceFactory = AbiInterface.Factory(
        declarationIndex = declarationIndex,
        abiResourceFactory = abiResourceFactory,
        abiFunctionFactory = abiFunctionFactory,
      )
      val abiWorldFactory = AbiWorld.Factory(
        abiInterfaceFactory = abiInterfaceFactory,
        abiResourceFactory = abiResourceFactory,
        abiFunctionFactory = abiFunctionFactory,
      )
      val worlds = abiWorldFactory.createAll(irPackages)
      val platformFunctionFactory = PlatformFunctionFactory(
        apiFunctionFactory = apiFunctionFactory,
        platform = platform,
      )
      val declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
        encoderFactory = encoderFactory,
      )
      return GuestGenerator(
        platform = platform,
        platformFunctionFactory = platformFunctionFactory,
        declaredTypeEncodersGenerator = declaredTypeEncodersGenerator,
        packages = irPackages,
        worlds = worlds,
      )
    }

    private fun apiGenerator(
      kotlinMapper: KotlinMapper,
      declarationIndex: DeclarationIndex,
      irPackages: List<IrWitPackage>,
      apiFunctionFactory: ApiFunctionFactory,
    ): ApiGenerator {
      val platform = HostPlatform
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
      )
      val abiInterfaceFactory = AbiInterface.Factory(
        declarationIndex = declarationIndex,
        abiFunctionFactory = abiFunctionFactory,
        abiResourceFactory = abiResourceFactory,
      )
      val abiWorldFactory = AbiWorld.Factory(
        abiInterfaceFactory = abiInterfaceFactory,
        abiResourceFactory = abiResourceFactory,
        abiFunctionFactory = abiFunctionFactory,
      )
      val worlds = abiWorldFactory.createAll(irPackages)
      val interfaces = irPackages.flatMap { it.services }
        .filterIsInstance<IrInterface>()
        .map { abiInterfaceFactory.createForApiOnly(it) }
      val resources = abiResourceFactory.createForApiOnly(irPackages)
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
