package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.io.IoWitPackageReader
import dev.wasmo.brevity.ir.IrMapper
import dev.wasmo.brevity.ir.IrWitPackage
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
  companion object {
    /**
     * Perform all necessary precompilation analysis and validation necessary to generate code.
     */
    context(issueCollector: IssueCollector)
    fun precompile(
      fileSystem: FileSystem,
      packageDirectories: Collection<Path>,
      irFilter: (List<IrWitPackage>) -> List<IrWitPackage> = { it },
    ): WitBridgeGenerator? = with(issueCollector) {
      val packageReader = IoWitPackageReader(fileSystem)

      val ioToplevelPackages = packageDirectories.map { directory ->
        packageReader.read(directory)
      }

      val irMapper = IrMapper(ioToplevelPackages) ?: return@with null

      val irPackages = irFilter(irMapper.map())

      val declarationIndex = DeclarationIndex(irPackages)
      val roleTracker = RoleTracker(declarationIndex, irPackages)
      val encoderFactory = EncoderFactory(declarationIndex)
      val guestGenerator = GuestGenerator(
        encoderFactory = encoderFactory,
        declarationIndex = declarationIndex,
        declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
          encoderFactory,
          GuestPlatform,
        ),
        roleTracker = roleTracker,
        packages = irPackages,
      )
      val hostGenerator = HostGenerator(
        encoderFactory = encoderFactory,
        declarationIndex = declarationIndex,
        declaredTypeEncodersGenerator = DeclaredTypeEncodersGenerator(
          encoderFactory,
          HostPlatform,
        ),
        roleTracker = roleTracker,
        packages = irPackages,
      )
      val apiGenerator = ApiGenerator(irPackages)

      WitBridgeGenerator(
        guest = guestGenerator,
        host = hostGenerator,
        api = apiGenerator,
        roleTracker = roleTracker,
      )
    }
  }
}
