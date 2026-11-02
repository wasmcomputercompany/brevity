package dev.wasmo.brevity

/**
 * An external function name, used as a unique identifier in a .wasm files.
 *
 * Composing function names is a bit awkward because there's various modifiers `[export]`,
 * `[callback]`, etc., that need to be in the correct place.
 *
 * Regular user-defined functions are [Standard]. Users also define [Constructor], [Static] and
 * [ResourceClose] functions for resource management. Other subtypes of this class are all defined
 * by Brevity itself, in service of bridging.
 *
 * Functions may be transformed to be lifted or lowered for async. Lowering a single function may
 * yield multiple output functions.
 */
sealed class FunctionName {
  abstract val parent: Parent

  open val name: Identifier?
    get() = null

  open val transform: Transform?
    get() = null

  private val exportPrefix: Boolean
    get() {
      if (transform == Transform.TaskReturn) return true
      if (this is ResourceNew) return true
      if (this is ResourceDropExport) return true
      return false
    }

  /** Returns null for exported world functions that don't have an `[export]` prefix. */
  val moduleName: String?
    get() {
      if (parent is Parent.WorldExport && !exportPrefix) return null

      return buildString {
        if (exportPrefix) {
          append("[export]")
        }

        when (transform) {
          Transform.AsyncExport -> append("[async-lift]")
          Transform.AsyncExportCallback -> append("[callback][async-lift]")
          else -> {}
        }

        when (val parent = parent) {
          is Parent.Interface -> append(parent.serviceName)
          is Parent.Resource -> append(parent.type.serviceName)
          else -> append($$"$root")
        }
      }
    }

  val abiName: String
    get() = buildString {
      val parent = parent
      if (parent is Parent.WorldExport) {
        when (transform) {
          Transform.AsyncExport -> append("[async-lift]")
          Transform.AsyncExportCallback -> append("[callback][async-lift]")
          else -> {}
        }
      }

      when (transform) {
        Transform.TaskReturn -> append("[task-return]")
        Transform.AsyncImport -> append("[async-lower]")
        else -> {}
      }

      if (parent is Parent.Resource) {
        when (this@FunctionName) {
          is Constructor -> append("[constructor]")
          is Static -> append("[static]")
          is ResourceNew -> append("[resource-new]")
          is ResourceClose -> append("[dtor]")
          is ResourceDropExport -> append("[resource-drop]")
          is ResourceDropImport -> append("[resource-drop]")
          else -> append("[method]")
        }

        append(parent.type.name)
        if (name != null) {
          append(".")
        }
      }

      name?.let { append(it) }
    }

  open fun transform(transform: Transform): FunctionName {
    error("unexpected call to transform on $this")
  }

  override fun toString(): String {
    val moduleName = this.moduleName
    return when {
      moduleName != null -> "$moduleName#$abiName"
      else -> abiName
    }
  }

  enum class Transform {
    /** Implements async-lift. */
    AsyncExport,
    AsyncExportCallback,
    TaskReturn,

    /** Implements async-lower. */
    AsyncImport,
  }

  data class Standard(
    override val parent: Parent,
    override val name: Identifier,
    override val transform: Transform? = null,
  ) : FunctionName() {
    override fun transform(transform: Transform) = copy(transform = transform)
    override fun toString() = super.toString()
  }

  data class Static(
    val type: TypeName.Declared,
    override val name: Identifier,
    override val transform: Transform? = null,
  ) : FunctionName() {
    override val parent: Parent.Resource
      get() = Parent.Resource(type)

    override fun transform(transform: Transform) = copy(transform = transform)
    override fun toString() = super.toString()
  }

  data class Constructor(
    val type: TypeName.Declared,
  ) : FunctionName() {
    override val parent: Parent.Resource
      get() = Parent.Resource(type)
    override fun toString() = super.toString()
  }

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#canon-resourcenew
   */
  data class ResourceNew(
    val type: TypeName.Declared,
  ) : FunctionName() {
    override val parent: Parent.Resource
      get() = Parent.Resource(type)
    override fun toString() = super.toString()
  }

  data class ResourceClose(
    val type: TypeName.Declared,
  ) : FunctionName() {
    override val parent: Parent.Resource
      get() = Parent.Resource(type)
    override fun toString() = super.toString()
  }

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/CanonicalABI.md#canon-resourcedrop
   */
  data class ResourceDropExport(
    val type: TypeName.Declared,
  ) : FunctionName() {
    override val parent: Parent.Resource
      get() = Parent.Resource(type)
    override fun toString() = super.toString()
  }

  data class ResourceDropImport(
    val type: TypeName.Declared,
  ) : FunctionName() {
    override val parent: Parent.Resource
      get() = Parent.Resource(type)
    override fun toString() = super.toString()
  }

  sealed interface Parent {
    val serviceName: ServiceName

    data class WorldExport(
      override val serviceName: ServiceName,
    ) : Parent

    data class WorldImport(
      override val serviceName: ServiceName,
    ) : Parent

    data class Interface(
      override val serviceName: ServiceName,
    ) : Parent

    data class Resource(val type: TypeName.Declared) : Parent {
      override val serviceName: ServiceName
        get() = type.serviceName
    }
  }
}
