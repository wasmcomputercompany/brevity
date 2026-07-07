package dev.wasmo.brevity

import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrInterface
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrTypeName
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld

/**
 * For each declared type used by a [IrWorld], this tracks how that type is used.
 *
 * This allows us to generate the minimal set of encoder and decoder functions.
 *
 * For value types, we generate an encoder, a decoder, or both, depending on how that type is used.
 * This follows function declarations to see how values are actually used.
 *
 * TODO: resources are currently always imported. This is inconsistent with wit-bindgen, but
 *    produces the behavior we want. We need something more sophisticated here!
 */
class RoleTracker(
  val types: Map<IrTypeName.Declared, Entry>,
  val interfaces: Map<ServiceName, Entry>,
) {
  operator fun get(typeName: IrTypeName.Declared): Entry? = types[typeName]

  operator fun get(typeName: ServiceName): Entry? = interfaces[typeName]

  data class Entry(
    /** True if values of this type are imported to the guest. */
    val host: Boolean,

    /** True if values of this type are exported by the guest. */
    val guest: Boolean,
  )

  companion object {
    operator fun invoke(
      declarationIndex: DeclarationIndex,
      witPackages: List<IrWitPackage>,
    ): RoleTracker {
      val traverser = TypeTraverser(declarationIndex)
      for (witPackage in witPackages) {
        traverser.collectAll(witPackage.services)
      }

      return RoleTracker(
        types = buildMap {
          for (type in traverser.allTypes) {
            put(
              type,
              Entry(
                host = type in traverser.hostTypes,
                guest = type in traverser.guestTypes,
              ),
            )
          }
        },
        interfaces = buildMap {
          for (type in traverser.guestInterfaces + traverser.hostInterfaces) {
            put(
              type,
              Entry(
                host = type in traverser.hostInterfaces,
                guest = type in traverser.guestInterfaces,
              ),
            )
          }
        },
      )
    }
  }
}


/**
 * Walk the graph of functions and types, and collect which types are used in which roles.
 */
internal class TypeTraverser(
  private val index: DeclarationIndex,
) {
  /** Types produced by the guest. These must be encoded on the guest and decoded on the host. */
  val guestTypes = mutableSetOf<IrTypeName.Declared>()

  /** Types produced by the host. These must be encoded on the host and decoded on the guest. */
  val hostTypes = mutableSetOf<IrTypeName.Declared>()

  /** This is the union of [guestTypes] and [hostTypes]. */
  val allTypes = mutableSetOf<IrTypeName.Declared>()

  /** Interfaces exported by the guest. */
  val guestInterfaces = mutableSetOf<ServiceName>()

  /** Interfaces imported to the guest. */
  val hostInterfaces = mutableSetOf<ServiceName>()

  /** Graph traversal queues. */
  private val guestQueue = ArrayDeque<IrTypeDeclaration>()
  private val hostQueue = ArrayDeque<IrTypeDeclaration>()

  private val guest = Collector(
    valueQueue = guestQueue,
    valueTypes = guestTypes,
    peerValueQueue = hostQueue,
    peerValueTypes = hostTypes,
    resourcesQueue = hostQueue,
    resourcesTypes = hostTypes,
    services = guestInterfaces,
  )

  private val host = Collector(
    valueQueue = hostQueue,
    valueTypes = hostTypes,
    peerValueQueue = guestQueue,
    peerValueTypes = guestTypes,
    resourcesQueue = hostQueue,
    resourcesTypes = hostTypes,
    services = hostInterfaces,
  )

  fun collectAll(services: List<IrWitPackage.Service>) {
    val worlds = services.filterIsInstance<IrWorld>()

    // Seed the traversal with the world's hosts and guests.
    for (world in worlds) {
      for (api in world.imports) {
        host.collectExternalApis(api)
      }
      for (api in world.exports) {
        guest.collectExternalApis(api)
      }
    }

    // Collect everything by recursively following all type references.
    while (true) {
      if (guest.processOne()) continue
      if (host.processOne()) continue
      break // Both queues are exhausted; traversal is done.
    }
  }

  inner class Collector(
    private val valueQueue: ArrayDeque<IrTypeDeclaration>,
    private val valueTypes: MutableSet<IrTypeName.Declared>,
    private val peerValueQueue: ArrayDeque<IrTypeDeclaration>,
    private val peerValueTypes: MutableSet<IrTypeName.Declared>,
    private val resourcesQueue: ArrayDeque<IrTypeDeclaration>,
    private val resourcesTypes: MutableSet<IrTypeName.Declared>,
    private val services: MutableSet<ServiceName>,
  ) {
    /**
     * When collecting parameter types, we flip the guest/host role because parameter values are
     * produced by the peer.
     *
     * We do not flip the role on [IrResource] types, because they're always produced by the
     * function's receiver, regardless of whether they're a parameter or a return value.
     */
    private val parametersCollector: Collector
      get() = Collector(
        valueQueue = peerValueQueue,
        valueTypes = peerValueTypes,
        peerValueQueue = valueQueue,
        peerValueTypes = valueTypes,
        resourcesQueue = resourcesQueue,
        resourcesTypes = resourcesTypes,
        services = services,
      )

    /** Returns true if a type was processed. */
    fun processOne(): Boolean {
      val typeDeclaration = valueQueue.removeFirstOrNull() ?: return false
      collectTypeDeclaration(typeDeclaration)
      return true
    }

    fun collectExternalApis(value: IrWorld.Api) {
      when (value) {
        is IrExternalApi -> collectExternalApi(value)
        is IrFunction -> collectFunction(value)
      }
    }

    private fun collectExternalApi(value: IrExternalApi) {
      services += value.serviceName

      val service = index[value.serviceName] as IrInterface
      for (item in service.items) {
        if (item is IrFunction) {
          collectFunction(item)
        }
      }
    }

    private fun collectTypeDeclaration(value: IrTypeDeclaration) {
      when (value) {
        is IrEnum -> {} // No members.
        is IrFlags -> {} // No members.

        is IrRecord -> {
          for (field in value.fields) {
            collectTypeName(field.type)
          }
        }

        is IrResource -> {
          for (function in value.functions) {
            collectFunction(function)
          }
        }

        is IrTypeAlias -> {
          collectTypeName(value.target)
        }

        is IrVariant -> {
          for (case in value.cases) {
            collectTypeName(case.type)
          }
        }
      }
    }

    private fun collectFunction(value: IrFunction) {
      collectTypeName(value.returnType)
      for (parameter in value.parameters) {
        parametersCollector.collectTypeName(parameter.type)
      }
    }

    private fun collectTypeName(value: IrTypeName?) {
      when (value) {
        is IrTypeName.Borrow -> collectTypeName(value.type)
        is IrTypeName.Declared -> this += value
        is IrTypeName.Future -> collectTypeName(value.type)

        is IrTypeName.List -> collectTypeName(value.type)
        is IrTypeName.Map -> {
          collectTypeName(value.key)
          collectTypeName(value.value)
        }

        is IrTypeName.Option -> collectTypeName(value.type)

        is IrTypeName.Result -> {
          collectTypeName(value.ok)
          collectTypeName(value.err)
        }

        is IrTypeName.Stream -> collectTypeName(value.type)

        is IrTypeName.Tuple -> {
          for (name in value.types) {
            collectTypeName(name)
          }
        }

        // No declarations.
        IrTypeName.Bool -> {}
        IrTypeName.S8 -> {}
        IrTypeName.S16 -> {}
        IrTypeName.S32 -> {}
        IrTypeName.S64 -> {}
        IrTypeName.U8 -> {}
        IrTypeName.U16 -> {}
        IrTypeName.U32 -> {}
        IrTypeName.U64 -> {}
        IrTypeName.F32 -> {}
        IrTypeName.F64 -> {}
        IrTypeName.Char -> {}
        IrTypeName.String -> {}
        null -> {}
      }
    }

    operator fun plusAssign(type: IrTypeName.Declared) {
      val declaration = index[type]

      val (types, queue) = when (declaration) {
        is IrResource -> resourcesTypes to resourcesQueue
        else -> valueTypes to valueQueue
      }

      if (!types.add(type)) return // Already added.

      allTypes += type

      if (declaration != null) {
        queue += declaration
      }
    }
  }
}
