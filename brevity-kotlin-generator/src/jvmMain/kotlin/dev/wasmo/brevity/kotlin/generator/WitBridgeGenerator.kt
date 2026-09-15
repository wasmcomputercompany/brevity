package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.io.validation.buildSymbolTable
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
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
        }
      )
      val guestPlatform = GuestPlatform(kotlinMapper)
      val hostPlatform = HostPlatform(kotlinMapper)

      validations.forEach { it.validate(declarationIndex) }

      val guestEncoderFactory = EncoderFactory(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        platform = guestPlatform,
      )
      val guestGenerator = GuestGenerator(
        kotlinMapper = kotlinMapper,
        guestPlatform = guestPlatform,
        encoderFactory = guestEncoderFactory,
        declarationIndex = declarationIndex,
        declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
          encoderFactory = guestEncoderFactory,
          platform = guestPlatform,
        ),
        roleTracker = roleTracker,
        packages = irPackages,
      )

      val hostEncoderFactory = EncoderFactory(
        kotlinMapper = kotlinMapper,
        declarationIndex = declarationIndex,
        platform = hostPlatform,
      )
      val hostGenerator = HostGenerator(
        kotlinMapper = kotlinMapper,
        hostPlatform = hostPlatform,
        encoderFactory = hostEncoderFactory,
        declarationIndex = declarationIndex,
        declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
          encoderFactory = hostEncoderFactory,
          platform = hostPlatform,
        ),
        roleTracker = roleTracker,
        packages = irPackages,
      )
      val apiGenerator = ApiGenerator(
        kotlinMapper = kotlinMapper,
        packages = irPackages,
      )

      WitBridgeGenerator(
        guest = guestGenerator,
        host = hostGenerator,
        api = apiGenerator,
        roleTracker = roleTracker,
      )
    }
  }
}
