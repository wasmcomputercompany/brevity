package dev.wasmo.brevity

import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrWitPackage

/** Associate types with their declarations. */
class DeclarationIndex(
  val types: Map<TypeName.Declared, IrTypeDeclaration>,
  private val services: Map<ServiceName, IrWitPackage.Service>,
) {
  private val typeToStringToType by lazy {
    types.keys.associateBy(TypeName.Declared::toString)
  }

  operator fun get(typeName: TypeName): IrTypeDeclaration? = types[typeName]

  operator fun get(typeName: ServiceName): IrWitPackage.Service? = services[typeName]

  /** Returns the type or null if it is not found. */
  fun getDeclaredType(typeName: String): TypeName.Declared? = typeToStringToType[typeName]

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
