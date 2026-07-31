package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.FunSpec
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.RoleTracker
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

class EncodersGenerator(
  private val encoderFactory: EncoderFactory,
  private val declarationIndex: DeclarationIndex,
  private val roleTracker: RoleTracker,
  private val packages: List<IrWitPackage>,
) {
  fun guestEncoders(type: IrTypeDeclaration, roleTrackerEntry: RoleTracker.Entry): List<FunSpec> {
    return listOf()
  }
  fun hostEncoders(type: IrTypeDeclaration, roleTrackerEntry: RoleTracker.Entry): List<FunSpec> {
    return listOf()
  }
}
