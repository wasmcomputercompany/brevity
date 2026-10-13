@file:Suppress("EnumEntryName")

package dev.wasmo.brevity

sealed interface IdentifierOrKeyword

/**
 * A well-formed WIT identifier like 'command' or 'get-insecure-random-u64'.
 */
@JvmInline
value class Identifier(
  val name: String,
): IdentifierOrKeyword {
  init {
    check(identifierRegex.matches(name))
  }

  fun normalized() = Identifier(name.lowercase())

  override fun toString() = name

  companion object {
    private val identifierRegex = Regex("^([a-z][a-z0-9]*|[A-Z][A-Z0-9]*)(-[a-z0-9]+|-[A-Z0-9]+)*$")

    /** Returns an identifier for this name, or null if it isn't a valid identifier. */
    fun String.toIdentifierOrNull(): Identifier? {
      val name = removePrefix("%")
      return try {
        Identifier(name)
      } catch (_: IllegalStateException) {
        null
      }
    }

    // The following identifiers are all defined in the spec, but don't qualify as keywords: you
    // can define a type named version, for example
    val feature = Identifier("feature")
    val version = Identifier("version")
    val since = Identifier("since")
    val deprecated = Identifier("deprecated")
    val unstable = Identifier("unstable")
  }
}



enum class Keyword: IdentifierOrKeyword {
  `as`,
  from,
  own,
  with,
  `interface`,
  `package`,
  async,
  bool,
  borrow,
  char,
  `constructor`,
  `enum`,
  export,
  f32,
  f64,
  flags,
  func,
  future,
  import,
  include,
  list,
  map,
  option,
  record,
  resource,
  result,
  s16,
  s32,
  s64,
  s8,
  static,
  stream,
  string,
  tuple,
  type,
  u16,
  u32,
  u64,
  u8,
  use,
  variant,
  world,
  ;

  override fun toString() = name

  companion object {
    private val keywordMap = Keyword.values().associateBy { it.name }

    /**
     * Produce the Keyword instance for a given name, or nothing if there is none.
     */
    fun keywordFor(name: String): Keyword? = keywordMap[name]

    /**
     * Set of type name keywords as a set of strings for validation
     */
    val typeNames = listOf(
      bool,
      borrow,
      char,
      f32,
      f64,
      future,
      list,
      map,
      option,
      result,
      s16,
      s32,
      s64,
      s8,
      stream,
      string,
      tuple,
      u16,
      u32,
      u64,
      u8,
    )

    /**
     * Set of interface item keywords as a set of strings for validation
     */
    val interfaceItems = listOf(
      enum,
      flags,
      record,
      resource,
      variant,
      type,
      use,
    )

    /**
     * Set of all keywords as a set of strings for validation
     */
    val all = typeNames + listOf(
      `as`,
      from,
      own,
      with,
      `interface`,
      `package`,
      async,
      constructor,
      enum,
      export,
      flags,
      func,
      import,
      include,
      record,
      resource,
      static,
      type,
      use,
      variant,
      world,
    )
  }
}
