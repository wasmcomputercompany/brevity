package dev.wasmo.brevity.ir

import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName

/**
 * Compute the streams from a world.
 *
 * We compute these _after_ building an initial incomplete [IrWorld] model. This is simpler than
 * building them on the less-capable models.
 *
 * As implemented in wit-bindgen's `find_futures_and_streams`, we traverse all parameter types
 * and any return type, recursively, collecting types that are either `stream<T>` or
 * `<future<T>`. Streams and futures are numbered in depth-first order.
 */
internal class IrStreamMapper(
  services: List<IrWitPackage.Service>,
) {
  private val typeIndex = services.flatMap { it.types }.associateBy { it.type }
  private val serviceIndex = services.associateBy { it.serviceName }

  fun create(irWorld: IrWorld): List<IrStream> = sequence { collect(irWorld) }.toList()

  context(sequenceScope: SequenceScope<IrStream>)
  private suspend fun collect(irWorld: IrWorld) {
    for (api in irWorld.exports) {
      when (api) {
        is IrExternalApi -> collect(
          orientation = Orientation.Export,
          irInterface = serviceIndex[api.serviceName] as IrInterface,
        )

        is IrFunction -> collect(
          orientation = Orientation.Export,
          irFunction = api,
        )
      }
    }

    for (api in irWorld.imports) {
      when (api) {
        is IrExternalApi -> collect(
          orientation = Orientation.Import,
          irInterface = serviceIndex[api.serviceName] as IrInterface,
        )

        is IrFunction -> collect(
          orientation = Orientation.Import,
          irFunction = api,
        )
      }
    }

    for (irResource in irWorld.types.filterIsInstance<IrResource>()) {
      collect(
        orientation = Orientation.Import, // World member resources are imported.
        irInterface = irResource,
      )
    }
  }

  context(sequenceScope: SequenceScope<IrStream>)
  private suspend fun collect(
    orientation: Orientation,
    irInterface: IrInterface,
  ) {
    for (irFunction in irInterface.functions) {
      collect(orientation, irFunction)
    }
    for (irResource in irInterface.types.filterIsInstance<IrResource>()) {
      collect(orientation, irResource)
    }
  }

  context(sequenceScope: SequenceScope<IrStream>)
  private suspend fun collect(
    orientation: Orientation,
    irInterface: IrResource,
  ) {
    for (irFunction in irInterface.functions) {
      collect(orientation, irFunction)
    }
  }

  context(sequenceScope: SequenceScope<IrStream>)
  private suspend fun collect(
    orientation: Orientation,
    irFunction: IrFunction,
  ) {
    val types = sequence {
      for (parameter in irFunction.parameters) {
        collectStreamTypesDepthFirst(parameter.type)
      }
      irFunction.returnType?.let { collectStreamTypesDepthFirst(it) }
    }

    for ((i, type) in types.withIndex()) {
      sequenceScope.yield(
        IrStream(
          functionName = irFunction.functionName,
          orientation = orientation,
          type = type,
          id = i,
        ),
      )
    }
  }

  context(sequenceScope: SequenceScope<TypeName>)
  private suspend fun collectStreamTypesDepthFirst(type: TypeName) {
    when (type) {
      is TypeName.Borrow -> {
        collectStreamTypesDepthFirst(type.type)
      }

      is TypeName.Declared -> {
        collectStreamTypesDepthFirst(typeIndex[type]!!)
      }

      is TypeName.Future -> {
        type.type?.let { collectStreamTypesDepthFirst(it) }
        sequenceScope.yield(type)
      }

      is TypeName.List -> {
        collectStreamTypesDepthFirst(type.type)
      }

      is TypeName.Map -> {
        collectStreamTypesDepthFirst(type.key)
        collectStreamTypesDepthFirst(type.value)
      }

      is TypeName.Option -> {
        collectStreamTypesDepthFirst(type.type)
      }

      is TypeName.Result -> {
        type.ok?.let { collectStreamTypesDepthFirst(it) }
        type.error?.let { collectStreamTypesDepthFirst(it) }
      }

      is TypeName.Stream -> {
        type.type?.let { collectStreamTypesDepthFirst(it) }
        sequenceScope.yield(type)
      }

      is TypeName.Tuple -> {
        for (element in type.types) {
          collectStreamTypesDepthFirst(element)
        }
      }

      else -> {}
    }
  }

  context(sequenceScope: SequenceScope<TypeName>)
  private suspend fun collectStreamTypesDepthFirst(type: IrTypeDeclaration) {
    when (type) {
      is IrRecord -> {
        for (field in type.fields) {
          collectStreamTypesDepthFirst(field.type)
        }
      }

      is IrTypeAlias -> collectStreamTypesDepthFirst(type.target)

      is IrVariant -> {
        for (case in type.cases) {
          case.type?.let { collectStreamTypesDepthFirst(it) }
        }
      }

      else -> {}
    }
  }
}
