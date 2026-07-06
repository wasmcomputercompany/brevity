package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * For each declared type used by a [KtWorld], this tracks how that type is used.
 *
 * This allows us to generate the minimal set of encoder and decoder functions.
 *
 * For value types, we generate an encoder, a decoder, or both, depending on how that type is used.
 * This follows function declarations to see how values are actually used.
 *
 * TODO: resources are currently always imported. This is inconsistent with wit-bindgen, but
 *    produces the behavior we want. We need something more sophisticated here!
 */
class WorldIndex(
  val map: Map<ClassName, Entry>,
) {
  operator fun get(typeName: TypeName): Entry? = map[typeName]

  data class Entry(
    val declaration: KtTypeDeclaration,

    /** True if values of this type are imported to the guest. */
    val host: Boolean,

    /** True if values of this type are exported by the guest. */
    val guest: Boolean,
  )

  companion object {
    operator fun invoke(
      declarationIndex: DeclarationIndex,
      services: List<KtNewService>,
    ): WorldIndex {
      val traverser = TypeTraverser(declarationIndex)
      traverser.collectAll(services)

      return WorldIndex(
        map = buildMap {
          for (type in traverser.allTypes) {
            val declaration = declarationIndex[type] ?: continue
            put(
              type,
              Entry(
                declaration = declaration,
                host = type in traverser.hostTypes,
                guest = type in traverser.guestTypes,
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
  val guestTypes = mutableSetOf<ClassName>()

  /** Types produced by the host. These must be encoded on the host and decoded on the guest. */
  val hostTypes = mutableSetOf<ClassName>()

  /** This is the union of [guestTypes] and [hostTypes]. */
  val allTypes = mutableSetOf<ClassName>()

  /** Graph traversal queues. */
  private val guestQueue = ArrayDeque<KtTypeDeclaration>()
  private val hostQueue = ArrayDeque<KtTypeDeclaration>()

  private val guest = Collector(guestQueue, guestTypes, hostQueue, hostTypes, hostQueue, hostTypes)
  private val host = Collector(hostQueue, hostTypes, guestQueue, guestTypes, hostQueue, hostTypes)

  fun collectAll(services: List<KtNewService>) {
    val worlds = services.filterIsInstance<KtWorld>()

    // Seed the traversal with the world's hosts and guests.
    for (world in worlds) {
      world.hostApis?.let { host.collectExternalApis(it) }
      world.guestApis?.let { guest.collectExternalApis(it) }
    }

    // Collect everything by recursively following all type references.
    while (true) {
      if (guest.processOne()) continue
      if (host.processOne()) continue
      break // Both queues are exhausted; traversal is done.
    }
  }

  inner class Collector(
    private val valueQueue: ArrayDeque<KtTypeDeclaration>,
    private val valueTypes: MutableSet<ClassName>,
    private val peerValueQueue: ArrayDeque<KtTypeDeclaration>,
    private val peerValueTypes: MutableSet<ClassName>,
    private val resourcesQueue: ArrayDeque<KtTypeDeclaration>,
    private val resourcesTypes: MutableSet<ClassName>,
  ) {
    /**
     * When collecting parameter types, we flip the guest/host role because parameter values are
     * produced by the peer.
     *
     * We do not flip the role on [KtResource] types, because they're always produced by the
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
      )

    /** Returns true if a type was processed. */
    fun processOne(): Boolean {
      val typeDeclaration = valueQueue.removeFirstOrNull() ?: return false
      collectTypeDeclaration(typeDeclaration)
      return true
    }

    fun collectExternalApis(value: KtWorld.ExternalApis) {
      for (item in value.items) {
        when (item) {
          is KtFunction -> collectFunction(item)
          is KtWorld.ExternalApis.InterfaceProperty -> this += item.type
        }
      }
    }

    fun collectTypeDeclaration(value: KtTypeDeclaration) {
      when (value) {
        is KtEnum -> {} // No members.
        is KtFlags -> {} // No members.

        is KtRecord -> {
          for (field in value.fields) {
            collectTypeName(field.type)
          }
        }

        is KtResource -> {
          for (function in value.functions) {
            collectFunction(function)
          }
        }

        is KtInterface -> {
          for (service in value.types) {
            this += service.type
          }
          for (function in value.functions) {
            collectFunction(function)
          }
        }

        is KtTypeAlias -> {
          collectTypeName(value.target)
        }

        is KtVariant -> {
          for (case in value.cases) {
            collectTypeName(case.type)
          }
        }
      }
    }

    private fun collectFunction(value: KtFunction) {
      collectTypeName(value.returnType)
      for (parameter in value.parameters) {
        parametersCollector.collectTypeName(parameter.type)
      }
    }

    private fun collectTypeName(value: KtTypeName?) {
      when (value) {
        is KtTypeName.Borrow -> collectTypeName(value.type)
        is KtTypeName.Declared -> this += value.apiType
        is KtTypeName.Future -> collectTypeName(value.type)

        is KtTypeName.List -> collectTypeName(value.type)
        is KtTypeName.Map -> {
          collectTypeName(value.key)
          collectTypeName(value.value)
        }

        is KtTypeName.Option -> collectTypeName(value.type)

        is KtTypeName.Result -> {
          collectTypeName(value.ok)
          collectTypeName(value.err)
        }

        is KtTypeName.Simple -> {}
        is KtTypeName.Stream -> collectTypeName(value.type)

        is KtTypeName.Tuple -> {
          for (name in value.types) {
            collectTypeName(name)
          }
        }

        null -> {}
      }
    }

    operator fun plusAssign(type: ClassName) {
      val declaration = index[type]

      val (types, queue) = when (declaration) {
        is KtResource -> resourcesTypes to resourcesQueue
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
