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
    val orientation: Orientation,
    val name: Identifier,
  ) : FunctionName() {
    override val moduleName: String?
      get() = when (orientation) {
        Orientation.Export -> null
        Orientation.Import -> $$"$root"
      }

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

  /** The caller's function to accept the lowered result. */
  data class TaskReturn(val original: FunctionName) : FunctionName() {
    override val moduleName: String
      get() = "[export]${(original.moduleName ?: $$"$root")}"
    override val abiName: String
      get() = "[task-return]${original.abiName}"

    override fun toString() = super.toString()
  }

  /** Implements async-lift. */
  data class AsyncExport(val original: FunctionName) : FunctionName() {
    override val moduleName: String?
      get() = original.moduleName?.let { "[async-lift]$it" }
    override val abiName: String
      get() = when {
        original.moduleName != null -> original.abiName
        else -> "[async-lift]${original.abiName}"
      }

    override fun toString() = super.toString()
  }

  data class AsyncExportCallback(val original: FunctionName) : FunctionName() {
    override val moduleName: String?
      get() = original.moduleName?.let { "[callback][async-lift]$it" }
    override val abiName: String
      get() = when {
        original.moduleName != null -> original.abiName
        else -> "[callback][async-lift]${original.abiName}"
      }

    override fun toString() = super.toString()
  }

  /** Implements async-lower. */
  data class AsyncImport(val original: FunctionName) : FunctionName() {
    override val moduleName: String
      get() = original.moduleName ?: $$"$root"
    override val abiName: String
      get() = "[async-lower]${original.abiName}"

    override fun toString() = super.toString()
  }
}
