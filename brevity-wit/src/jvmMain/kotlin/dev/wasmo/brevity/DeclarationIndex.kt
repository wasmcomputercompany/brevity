package dev.wasmo.brevity

import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrTypeName
import dev.wasmo.brevity.ir.IrWitPackage

/** Associate types with their declarations. */
class DeclarationIndex(
  private val types: Map<IrTypeName.Declared, IrTypeDeclaration>,
  private val services: Map<ServiceName, IrWitPackage.Service>,
) {
  operator fun get(typeName: IrTypeName): IrTypeDeclaration? = types[typeName]

  operator fun get(typeName: ServiceName): IrWitPackage.Service? = services[typeName]

  companion object {
    operator fun invoke(irPackages: List<IrWitPackage>) = DeclarationIndex(
      types = buildMap {
        for (witPackage in irPackages) {
          for (service in witPackage.services) {
            for (typeDeclaration in service.types) {
              put(typeDeclaration.type, typeDeclaration)
            }
          }
        }
      },
      services = buildMap {
        for (witPackage in irPackages) {
          for (service in witPackage.services) {
            put(service.serviceName, service)
          }
        }
      },
    )
  }
}
