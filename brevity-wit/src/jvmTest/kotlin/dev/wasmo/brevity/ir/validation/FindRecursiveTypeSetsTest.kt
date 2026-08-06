package dev.wasmo.brevity.ir.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrField
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrWitPackage
import dev.wasmo.brevity.ir.IrWorld
import dev.wasmo.brevity.ir.TypeNameDeclared
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test

class FindRecursiveTypeSetsTest {
  @Test
  fun `finds mutual recursion`() {
    val mutuallyRecursive1 = "mutuallyRecursive1"
    val mutuallyRecursive2 = "mutuallyRecursive2"
    val result = testFinder(
      mutuallyRecursive1 to mutuallyRecursive2,
      mutuallyRecursive2 to mutuallyRecursive1,
      "nonRecursive" to mutuallyRecursive1,
    )

    assertThat(result).isEqualTo(
      listOf(setOf(mutuallyRecursive1, mutuallyRecursive2)),
    )
  }

  @Test
  fun `finds simple recursion, but not single types`() {
    val simpleRecursion = "simpleRecursion"
    val result = testFinder(
      simpleRecursion to simpleRecursion,
      "nonRecursive" to simpleRecursion,
    )

    assertThat(result).isEqualTo(
      listOf(setOf(simpleRecursion)),
    )

    // Repeat with nodes in opposite order
    val result2 = testFinder(
      "nonRecursive" to simpleRecursion,
      simpleRecursion to simpleRecursion,
    )

    assertThat(result2).isEqualTo(
      listOf(setOf(simpleRecursion)),
    )
  }
}

/**
 * Nodes are always in the following order:
 *
 * * Predecessors from edges
 * * Successors from edges
 *
 * If you need standalone nodes, use edges with zero successors.
 */
fun <V> testFinder(
  vararg edges: Pair<V, V>,
): List<Set<V>> {
  val graph = buildMap {
    for ((prev, next) in edges) {
      getOrPut(prev) { mutableListOf() }.add(next)
    }
  }
  val nodes = buildList {
    for ((prev, _) in edges) {
      add(prev)
    }
    for ((_, next) in edges) {
      add(next)
    }
  }.distinct()

  return findRecursiveGroups(nodes) { graph[it]!!.asSequence() }
}
