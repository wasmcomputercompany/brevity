package dev.wasmo.brevity

/**
 * An external function name, used as a unique identifier in a .wasm files.
 */
sealed class FunctionName {
  abstract val moduleName: String?
  abstract val abiName: String

  override fun toString(): String {
    val moduleName = this.moduleName
    return when {
      moduleName != null -> "$moduleName#$abiName"
      else -> abiName
    }
  }

  data class ResourceDrop(
    val serviceName: ServiceName,
    val resourceName: Identifier,
  ) : FunctionName() {
    override val moduleName: String
      get() = serviceName.toString()

    override val abiName: String
      get() = "[resource-drop]${resourceName.name}"

    override fun toString() = super.toString()
  }

  data class Constructor(
    val serviceName: ServiceName,
    val name: Identifier,
  ) : FunctionName() {
    override val moduleName: String
      get() = serviceName.toString()

    override val abiName: String
      get() = "[constructor]${name.name}"

    override fun toString() = super.toString()
  }

  data class Method(
    val serviceName: ServiceName,
    val name: Identifier,
    val resourceName: Identifier,
  ) : FunctionName() {
    override val moduleName: String
      get() = serviceName.toString()

    override val abiName: String
      get() = "[method]${resourceName.name}.${name.name}"

    override fun toString() = super.toString()
  }

  data class Static(
    val serviceName: ServiceName,
    val name: Identifier,
    val resourceName: Identifier,
  ) : FunctionName() {
    override val moduleName: String
      get() = serviceName.toString()

    override val abiName: String
      get() = "[static]${resourceName.name}.${name.name}"

    override fun toString() = super.toString()
  }

  data class World(
    val name: Identifier,
  ) : FunctionName() {
    override val moduleName: String?
      get() = null

    override val abiName: String
      get() = name.name

    override fun toString() = super.toString()
  }

  data class Interface(
    val serviceName: ServiceName,
    val name: Identifier,
  ) : FunctionName() {
    override val moduleName: String
      get() = serviceName.toString()

    override val abiName: String
      get() = name.name

    override fun toString() = super.toString()
  }
}
