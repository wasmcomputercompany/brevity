package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/** Associate classes with their declarations. */
internal class DeclarationIndex(
  private val map: Map<ClassName, KtTypeDeclaration>,
) {
  operator fun get(typeName: TypeName): KtTypeDeclaration? = map[typeName]

  companion object {
    operator fun invoke(services: List<KtService>): DeclarationIndex {
      val classToDeclaration = sequence { services.yieldTypeDeclarations() }
        .associateBy { it.type }

      return DeclarationIndex(classToDeclaration)
    }

    context(scope: SequenceScope<KtTypeDeclaration>)
    private suspend fun Iterable<KtTypeDeclaration>.yieldTypeDeclarations() {
      for (declaration in this) {
        scope.yield(declaration)
        if (declaration is KtService) {
          declaration.types.yieldTypeDeclarations()
        }
      }
    }
  }
}
