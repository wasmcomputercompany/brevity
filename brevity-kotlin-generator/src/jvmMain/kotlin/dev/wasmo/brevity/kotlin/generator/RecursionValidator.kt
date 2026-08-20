package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.validation.findRecursiveTypeSets

class RecursionValidator(
  private val findRecursiveTypeSets: (DeclarationIndex)->List<Set<TypeName.Declared>> = ::findRecursiveTypeSets,
) : WitBridgeGenerator.Validation {
  context(issueCollector: IssueCollector)
  override fun validate(declarationIndex: DeclarationIndex) {
    findRecursiveTypeSets(declarationIndex)
      .map { set -> set.map { type -> declarationIndex[type]!! } }
      .forEach { declarations ->
        val recursionType = if (declarations.size > 1) {
          "mutual recursion"
        } else {
          "recursion"
        }
        issueCollector.report(
          Issue(
            "Invalid $recursionType",
            declarations.map { it.location },
          ),
        )
      }
  }
}
