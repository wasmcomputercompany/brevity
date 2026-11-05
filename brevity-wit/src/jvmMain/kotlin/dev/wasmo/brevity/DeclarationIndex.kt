package dev.wasmo.brevity

import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrVariant
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

  context(sequenceScope: SequenceScope<TypeName>)
  suspend fun collectTypesDepthFirst(type: TypeName) {
    // Children.
    when (type) {
      is TypeName.Borrow -> {
        collectTypesDepthFirst(type.type)
      }

      is TypeName.Declared -> {
        collectTypesDepthFirst(get(type)!!)
      }

      is TypeName.Future -> {
        type.type?.let { collectTypesDepthFirst(it) }
      }

      is TypeName.List -> {
        collectTypesDepthFirst(type.type)
      }

      is TypeName.Map -> {
        collectTypesDepthFirst(type.key)
        collectTypesDepthFirst(type.value)
      }

      is TypeName.Option -> {
        collectTypesDepthFirst(type.type)
      }

      is TypeName.Result -> {
        type.ok?.let { collectTypesDepthFirst(it) }
        type.error?.let { collectTypesDepthFirst(it) }
      }

      is TypeName.Stream -> {
        type.type?.let { collectTypesDepthFirst(it) }
      }

      is TypeName.Tuple -> {
        for (element in type.types) {
          collectTypesDepthFirst(element)
        }
      }

      else -> {}
    }

    // The type itself.
    sequenceScope.yield(type)
  }

  context(sequenceScope: SequenceScope<TypeName>)
  private suspend fun collectTypesDepthFirst(type: IrTypeDeclaration) {
    // Children.
    when (type) {
      is IrRecord -> {
        for (field in type.fields) {
          collectTypesDepthFirst(field.type)
        }
      }

      is IrTypeAlias -> collectTypesDepthFirst(type.target)

      is IrVariant -> {
        for (case in type.cases) {
          case.type?.let { collectTypesDepthFirst(it) }
        }
      }

      else -> {}
    }

    // The type itself.
    sequenceScope.yield(type.type)
  }

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
