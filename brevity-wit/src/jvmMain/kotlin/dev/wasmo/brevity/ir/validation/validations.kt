package dev.wasmo.brevity.ir.validation

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant

/**
 * Returns a list of sets of [TypeName]s which contain simple or mutual recursion.
 *
 * This is a modified implementation of [Tarjan's SCC algorithm](https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm).
 *
 * Strongly connected components include solitary nodes, though, whether they're recursive or not.
 * So this implementation has been modified to figure out when a single node SCC is recursive,
 * and filter the other ones out of the output.
 */
fun findRecursiveTypeSets(index: DeclarationIndex): List<Set<TypeName.Declared>> {
  return findRecursiveGroups(
    successors = { typeName -> typeNameGraphSuccessors(index, typeName) },
    nodes = index.types.keys,
  )
}

internal fun typeNameGraphSuccessors(index: DeclarationIndex, node: TypeName.Declared): Sequence<TypeName.Declared> = sequence {
  when (val decl = index[node]!!) {
    is IrEnum, // has IrCase children, but these are guaranteed not to be successor types
    is IrFlags, is IrResource,
      -> {
    }

    is IrTypeAlias -> if (decl.target is TypeName.Declared) {
      yield(decl.target)
    }

    is IrRecord -> yieldAll(
      decl.fields.mapNotNull { field ->
        field.type as? TypeName.Declared
      },
    )

    is IrVariant -> yieldAll(
      decl.cases.mapNotNull { case ->
        case.type as? TypeName.Declared
      },
    )
  }
}

/**
 * Finds groups in a graph that contain recursive loops. Trees (including solitary non-recursive
 * nodes) are omitted.
 */
internal fun <V> findRecursiveGroups(
  nodes: Iterable<V>,
  successors: (V)->Sequence<V>,
) = RecursiveTypeSetFinder(
  successors = successors,
  nodes = nodes,
).find()

private class RecursiveTypeSetFinder<V>(
  private val successors: (V)->Sequence<V>,
  nodes: Iterable<V>,
) {
  /**
   * Bookkeeping information for the algorithm.
   */
  private data class Record(
    var depthIndex: Int? = null,
    var lowlink: Int? = null,
    var onStack: Boolean = false,
    var isSimplyRecursive: Boolean = false,
  )

  private val recordMap: Map<V, Record> = nodes
    .associateWith { Record() }

  private var depthIndex = 0

  private val stack = ArrayDeque<V>()

  fun find(): List<Set<V>> = recordMap.entries.flatMap { (typeName, record) ->
    if (record.depthIndex == null) {
      find(typeName, record)
    } else {
      // Already visited
      emptyList()
    }
  }

  private fun find(
    currNode: V,
    curr: Record,
  ): List<Set<V>> {
    val components = mutableListOf<Set<V>>()
    curr.depthIndex = depthIndex++
    curr.lowlink = curr.depthIndex
    stack += currNode
    curr.onStack = true

    val successors = successors(currNode)

    for (succNode in successors) {
      val succ =
        recordMap[succNode] ?: error("No record for type: $succNode")
      if (succ.depthIndex == null) {
        components.addAll(find(succNode, succ))
        curr.lowlink = minOf(curr.lowlink!!, succ.lowlink!!)
      } else if (succ.onStack) {
        curr.lowlink = minOf(curr.lowlink!!, succ.depthIndex!!)
        if (succ == curr) {
          // Modification: detect simple recursion for this node
          curr.isSimplyRecursive = true
        }
      }
    }

    if (curr.lowlink == curr.depthIndex) {
      val component = mutableSetOf<V>()
      while (true) {
        val typeName = stack.removeLastOrNull()
        recordMap[typeName]?.onStack = false
        if (typeName != null) {
          component += typeName
        }

        if (typeName == currNode) break
      }
      // Modification: only add components if they're nontrivial
      // or have simple recursion
      if (component.size > 1 || curr.isSimplyRecursive) {
        components += component
      }
    }

    return components
  }
}
