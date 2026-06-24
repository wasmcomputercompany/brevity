package dev.wasmo.brevity.kotlin.generator

internal val List<KtWitPackage>.typeDeclarations: Sequence<KtTypeDeclaration>
  get() = sequence {
    for (ktPackage in this@typeDeclarations) {
      ktPackage.collectTypeDeclarations()
    }
  }

context(scope: SequenceScope<KtTypeDeclaration>)
private suspend fun KtWitPackage.collectTypeDeclarations() {
  for (item in items) {
    when (item) {
      is KtInterface -> item.collectTypeDeclarations()
      is KtWorld -> item.collectTypeDeclarations()
    }
  }
}

context(scope: SequenceScope<KtTypeDeclaration>)
private suspend fun KtInterface.collectTypeDeclarations() {
  for (item in items) {
    if (item is KtTypeDeclaration) scope.yield(item)
  }
}

context(scope: SequenceScope<KtTypeDeclaration>)
private suspend fun KtWorld.collectTypeDeclarations() {
  for (item in items) {
    if (item is KtTypeDeclaration) scope.yield(item)
  }
}
