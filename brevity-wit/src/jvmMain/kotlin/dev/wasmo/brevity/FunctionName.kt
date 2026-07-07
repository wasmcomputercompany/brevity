package dev.wasmo.brevity

/**
 * An external function name, used as a unique identifier in a .wasm files.
 */
data class FunctionName(
  val serviceName: ServiceName? = null,
  val name: Identifier,
  val resourceName: Identifier? = null,
  val annotation: Annotation? = null,
) {
  val moduleName: String?
    get() = when {
      serviceName != null -> serviceName.toString()
      else -> null
    }

  val abiName: String
    get() = buildString {
      if (annotation != null) {
        append(annotation.value)
      }
      if (resourceName != null) {
        append(resourceName.name)
        append('.')
      }
      append(name.name)
    }

  override fun toString(): String {
    val moduleName = this.moduleName
    return when {
      moduleName != null -> "$moduleName#$abiName"
      else -> abiName
    }
  }
}

enum class Annotation(val value: String) {
  Constructor("[constructor]"),
  Method("[method]"),
  ResourceDrop("[resource-drop]"),
  Static("[static]"),
}
