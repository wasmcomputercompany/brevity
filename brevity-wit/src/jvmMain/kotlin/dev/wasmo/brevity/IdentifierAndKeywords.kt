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



@JvmInline
value class Keyword private constructor(
  val name: String,
): IdentifierOrKeyword {
  override fun toString() = name

  companion object {
    // Taken from:
    // https://github.com/WebAssembly/component-model/blob/d6a17ea9f828f44c4659d70c35822ab53d2a8a1d/design/mvp/WIT.md?plain=1#L1058C1-L1099C20
    private val keywordEbnf = """
      ||keyword ::= 'as'
      ||           | 'async'
      ||           | 'bool'
      ||           | 'borrow'
      ||           | 'char'
      ||           | 'constructor'
      ||           | 'enum'
      ||           | 'export'
      ||           | 'f32'
      ||           | 'f64'
      ||           | 'flags'
      ||           | 'from'
      ||           | 'func'
      ||           | 'future'
      ||           | 'import'
      ||           | 'include'
      ||           | 'interface'
      ||           | 'list'
      ||           | 'map'
      ||           | 'option'
      ||           | 'own'
      ||           | 'package'
      ||           | 'record'
      ||           | 'resource'
      ||           | 'result'
      ||           | 's16'
      ||           | 's32'
      ||           | 's64'
      ||           | 's8'
      ||           | 'static'
      ||           | 'stream'
      ||           | 'string'
      ||           | 'tuple'
      ||           | 'type'
      ||           | 'u16'
      ||           | 'u32'
      ||           | 'u64'
      ||           | 'u8'
      ||           | 'use'
      ||           | 'variant'
      ||           | 'with'
      ||           | 'world'
    """.trimMargin(marginPrefix = "||")
    private val masterKeywordMap = run {
      keywordEbnf.replace(Regex("keyword ::= | |'|[|]"), "")
        .split("\n")
        .associateWith { Keyword(it) }
    }

    /**
     * Produce the Keyword instance for a given name, or nothing if there is none.
     */
    fun keywordFor(name: String): Keyword? = masterKeywordMap[name]

    // Text taken from:
    // https://github.com/WebAssembly/component-model/blob/d6a17ea9f828f44c4659d70c35822ab53d2a8a1d/design/mvp/WIT.md?plain=1#L1058
    val `as` = keywordFor("as")!!
    val from = keywordFor("from")!!
    val own = keywordFor("own")!!
    val with = keywordFor("with")!!
    val `interface` = keywordFor("interface")!!
    val `package` = keywordFor("package")!!
    val async = keywordFor("async")!!
    val bool = keywordFor("bool")!!
    val borrow = keywordFor("borrow")!!
    val char = keywordFor("char")!!
    val constructor = keywordFor("constructor")!!
    val enum = keywordFor("enum")!!
    val export = keywordFor("export")!!
    val f32 = keywordFor("f32")!!
    val f64 = keywordFor("f64")!!
    val flags = keywordFor("flags")!!
    val func = keywordFor("func")!!
    val future = keywordFor("future")!!
    val import = keywordFor("import")!!
    val include = keywordFor("include")!!
    val list = keywordFor("list")!!
    val map = keywordFor("map")!!
    val option = keywordFor("option")!!
    val record = keywordFor("record")!!
    val resource = keywordFor("resource")!!
    val result = keywordFor("result")!!
    val s16 = keywordFor("s16")!!
    val s32 = keywordFor("s32")!!
    val s64 = keywordFor("s64")!!
    val s8 = keywordFor("s8")!!
    val static = keywordFor("static")!!
    val stream = keywordFor("stream")!!
    val string = keywordFor("string")!!
    val tuple = keywordFor("tuple")!!
    val type = keywordFor("type")!!
    val u16 = keywordFor("u16")!!
    val u32 = keywordFor("u32")!!
    val u64 = keywordFor("u64")!!
    val u8 = keywordFor("u8")!!
    val use = keywordFor("use")!!
    val variant = keywordFor("variant")!!
    val world = keywordFor("world")!!

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
