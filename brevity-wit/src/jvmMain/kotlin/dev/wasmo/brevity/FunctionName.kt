package dev.wasmo.brevity

import dev.wasmo.brevity.Orientation.Export

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

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#canon-resourcenew
   */
  data class ResourceNew(
    val resourceType: TypeName.Declared,
  ) : FunctionName() {
    override val moduleName: String
      get() = "[export]${resourceType.serviceName}"

    override val abiName: String
      get() = "[resource-new]${resourceType.name}"

    override fun toString() = super.toString()
  }

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#canon-resourcedrop
   */
  data class ResourceDrop(
    val orientation: Orientation,
    val resourceType: TypeName.Declared,
  ) : FunctionName() {
    override val moduleName: String
      get() = when (orientation) {
        Export -> "[export]${resourceType.serviceName}"
        else -> resourceType.serviceName.toString()
      }

    override val abiName: String
      get() = "[resource-drop]${resourceType.name}"

    override fun toString() = super.toString()
  }

  /** Destructs a resource. */
  data class ResourceClose(
    val resourceType: TypeName.Declared,
  ) : FunctionName() {
    override val moduleName: String
      get() = resourceType.serviceName.toString()

    override val abiName: String
      get() = "[dtor]${resourceType.name}"

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
        Export -> null
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
