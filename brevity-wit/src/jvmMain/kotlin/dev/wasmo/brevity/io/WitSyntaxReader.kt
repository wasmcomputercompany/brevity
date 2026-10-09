@file:OptIn(ExperimentalContracts::class)

package dev.wasmo.brevity.io

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Identifier.Companion.toIdentifierOrNull
import dev.wasmo.brevity.IdentifierOrKeyword
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.Keyword
import dev.wasmo.brevity.Keyword.Companion.keywordFor
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.SemVer
import dev.wasmo.brevity.WitCoreInternalApi
import dev.wasmo.brevity.WitException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@WitCoreInternalApi
class WitSyntaxReader(
  private val baseLocation: Location,
  private val chars: CharArray,
) {
  constructor(
    baseLocation: Location,
    string: String,
  ) : this(baseLocation, string.toCharArray())

  private var documentation: StringBuilder? = null
  private var pos = 0
  private var line = 0
  private var lineStart = 0

  val location: Location
    get() = baseLocation.at(line + 1, pos - lineStart + 1)

  val exhausted: Boolean
    get() = pos == chars.size

  /**
   * Attempt each element of [options] until one returns normally. If an options throws a
   * [WitException], it is skipped. If no options return a value, the first option's exception is
   * rethrown.
   *
   * This function is weird because it does speculative execution of parsing. It's likely to lead
   * to extremely badly-performing code if used recursively, so don't do that.
   */
  fun <T> select(
    vararg options: () -> T,
  ): T {
    require(options.isNotEmpty())

    val oldDocumentation = documentation?.let { StringBuilder(it) }
    val oldPos = pos
    val oldLine = line
    val oldLineStart = lineStart

    var failure: WitException? = null

    for (option in options) {
      documentation = oldDocumentation?.let { StringBuilder(it) }
      pos = oldPos
      line = oldLine
      lineStart = oldLineStart

      try {
        return option()
      } catch (e: WitException) {
        if (failure == null) failure = e
        else failure.addSuppressed(e)
      }
    }

    throw failure!!
  }

  fun takeDocumentation(): Documentation? {
    return when {
      documentation != null -> {
        Documentation(documentation.toString())
          .also { documentation = null }
      }

      else -> null
    }
  }

  private fun appendDocumentation(startIndex: Int, endIndex: Int) {
    val documentation = when (val d = this.documentation) {
      null -> StringBuilder().also { this.documentation = it }
      else -> d.also { it.append("\n") }
    }
    documentation.appendRange(chars, startIndex, endIndex)
  }

  /**
   * Reads a keyword and returns it. Keyword validation is performed by callers.
   */
  context(issueCollector: IssueCollector)
  fun readKeyword(): Keyword =
    readIdentifierOrKeywordInternal(Keyword::class.java)


  /**
   * Reads a well-formed identifier and returns it. If the input file contains an identifier that
   * is not well-formed (such as 'incomingHandler' that mixes case in a single segment), this
   * function reports an error and returns a placeholder.
   *
   * If any keyword is read, [readIdentifier] will immediately raise a [WitException] and stop
   * the parse.
   *
   * @param unescaped If set to true, will report an issue if escaping is present.
   */
  context(issueCollector: IssueCollector)
  fun readIdentifier(unescaped: Boolean = false): Identifier =
    readIdentifierOrKeywordInternal(
      expectedType = Identifier::class.java,
      unescaped = unescaped,
    )

  /**
   * Reads a well-formed identifier or keyword instance and returns it. If the input file contains
   * an identifier that is not well-formed (such as 'incomingHandler' that mixes case in a single
   * segment), this function reports an error and returns a placeholder.
   */
  context(issueCollector: IssueCollector)
  fun readIdentifierOrKeyword(): IdentifierOrKeyword =
    readIdentifierOrKeywordInternal(IdentifierOrKeyword::class.java)

  context(issueCollector: IssueCollector)
  private fun <T: IdentifierOrKeyword> readIdentifierOrKeywordInternal(
    expectedType: Class<T>,
    unescaped: Boolean = false,
  ): T {
    val expected = when (expectedType) {
      Identifier::class.java -> "expected an identifier"
      Keyword::class.java -> "expected a keyword"
      else -> "expected an identifier or keyword"
    }

    checkWit(!exhausted) {
      expected
    }

    val wordStart = when (chars[pos]) {
      '%' -> {
        if (unescaped) {
          issueCollector.report(
            Issue("unexpected escape character: '%'", location)
          )
        }
        pos + 1
      }
      else -> pos
    }

    val end = chars.indexOfNextNonMatch(wordStart, Char::isWordCharacter)
    checkWit(wordStart < end) {
      expected
    }

    val resultLocation = location

    val result = String(chars, pos, end - pos)

    val identifier = (result.toIdentifierOrNull()
      ?: run {
        issueCollector.report(
          Issue(
            "malformed identifier: $result",
            resultLocation,
          ),
        )
        Identifier("PLACEHOLDER")
      })


    // Using result to preserve escaping
    val parsed = keywordFor(result) ?: identifier

    checkWit(expectedType.isInstance(parsed)) {
      when (expectedType) {
        Identifier::class.java -> "expected an identifier, but read keyword $result"
        else -> "expected a keyword, but read $result"
      }
    }

    pos = end

    @Suppress("UNCHECKED_CAST")
    return parsed as T
  }

  fun readSemVer(): SemVer {
    var end = chars.indexOfNextNonMatch(pos, Char::isSemverCharacter)

    // Backtrack trailing '.' characters, if any.
    for (i in end - 1 downTo pos) {
      if (chars[i] != '.') break
      end--
    }
    checkWit(pos < end) {
      "expected a semver character"
    }
    val result = String(chars, pos, end - pos)
    pos = end
    return SemVer(result)
  }

  fun readUint(): UInt {
    val string = readToken("semver", Char::isDigit)
    checkWit(string == "0" || !string.startsWith('0')) {
      "integer identifiers must not start with '0'"
    }
    return string.toUInt()
  }

  fun readToken(name: String, match: (Char) -> Boolean): String {
    checkWit(match(peek())) {
      "expected a $name character"
    }

    val end = chars.indexOfNextNonMatch(pos + 1, match)
    val result = String(chars, pos, end - pos)
    pos = end
    return result
  }

  context(issueCollector: IssueCollector)
  fun readAnnotationOrNull(): Identifier? {
    if (peek() != '@') return null
    pos++ // Consume '@'.
    val location = location

    return readIdentifier(unescaped = true).also {
      if (!setOf(Identifier.deprecated, Identifier.since, Identifier.unstable).contains(it)) {
        errorWit(location, "unexpected gate annotation: $it")
      }
    }
  }

  private fun peek(): Char = when {
    exhausted -> '\u0000'
    else -> chars[pos]
  }

  fun tryReadLiteral(literal: Char): Boolean {
    if (exhausted || chars[pos] != literal) return false
    pos++
    return true
  }

  fun tryReadLiteral(literal: String): Boolean {
    if (pos + literal.length > chars.size) return false
    for ((j, c) in literal.withIndex()) {
      if (chars[pos + j] != c) return false
    }
    pos += literal.length
    return true
  }

  fun readLiteral(literal: Char) {
    checkWit(tryReadLiteral(literal)) {
      when {
        exhausted -> "expected '$literal' but was EOF"
        else -> "expected '$literal' but was '${chars[pos]}'"
      }
    }
  }

  fun readLiteral(literal: String) {
    checkWit(tryReadLiteral(literal)) {
      when {
        exhausted -> "expected $literal but was EOF"
        else -> "expected $literal but was '${chars[pos]}'"
      }
    }
  }

  /**
   * ```ebnf
   * ty ::= 'u8' | 'u16' | 'u32' | 'u64'
   *      | 's8' | 's16' | 's32' | 's64'
   *      | 'f32' | 'f64'
   *      | 'char'
   *      | 'bool'
   *      | 'string'
   *      | tuple
   *      | list
   *      | option
   *      | result
   *      | map
   *      | handle
   *      | future
   *      | stream
   *      | id
   *
   * tuple ::= 'tuple' '<' tuple-list '>'
   * tuple-list ::= ty
   *              | ty ',' tuple-list?
   *
   * list ::= 'list' '<' ty '>'
   *        | 'list' '<' ty ',' uint '>'
   *
   * uint ::= [1-9][0-9]*
   *
   * option ::= 'option' '<' ty '>'
   *
   * result ::= 'result' '<' ty ',' ty '>'
   *          | 'result' '<' '_' ',' ty '>'
   *          | 'result' '<' ty '>'
   *          | 'result'
   *
   * map ::= 'map' '<' kt ',' ty '>'
   * kt ::= 'u8' | 'u16' | 'u32' | 'u64'
   *      | 's8' | 's16' | 's32' | 's64'
   *      | 'char' | 'bool' | 'string'
   *
   * future ::= 'future' '<' ty '>'
   *          | 'future'
   *
   * stream ::= 'stream' '<' ty '>'
   *          | 'stream'
   * ```
   */
  context(issueCollector: IssueCollector)
  fun readTypeName(): IoTypeName {
    return when (val identifier = readIdentifierOrKeyword()) {
      Keyword.tuple -> IoTypeName.Tuple(readTypeList("tuple", min = 1, max = Int.MAX_VALUE))
      Keyword.list -> {
        readLiteral('<')
        skipWhitespace()
        val type = readTypeName()
        skipWhitespace()
        val size = when {
          tryReadLiteral(',') -> {
            skipWhitespace()
            readUint()
          }

          else -> null
        }
        skipWhitespace()
        readLiteral('>')
        IoTypeName.List(type, size)
      }

      Keyword.option -> IoTypeName.Option(readTypeList("option", min = 1, max = 1).single())
      Keyword.result -> {
        if (tryReadLiteral('<')) {
          skipWhitespace()
          val ok = when {
            tryReadLiteral('_') -> null
            else -> readTypeName()
          }
          skipWhitespace()
          val error = when {
            tryReadLiteral(',') -> {
              skipWhitespace()
              readTypeName()
            }

            else -> null
          }
          skipWhitespace()
          readLiteral('>')
          IoTypeName.Result(ok, error)
        } else {
          IoTypeName.Result()
        }
      }

      Keyword.map -> {
        val (key, value) = readTypeList("map", min = 2, max = 2)
        IoTypeName.Map(key, value)
      }

      Keyword.borrow -> IoTypeName.Borrow(readTypeList("borrow", min = 1, max = 1).single())
      Keyword.future -> IoTypeName.Future(readTypeList("future", min = 0, max = 1).singleOrNull())
      Keyword.stream -> IoTypeName.Stream(readTypeList("stream", min = 0, max = 1).singleOrNull())
      Keyword.bool -> IoTypeName.Bool
      Keyword.s8 -> IoTypeName.S8
      Keyword.s16 -> IoTypeName.S16
      Keyword.s32 -> IoTypeName.S32
      Keyword.s64 -> IoTypeName.S64
      Keyword.u8 -> IoTypeName.U8
      Keyword.u16 -> IoTypeName.U16
      Keyword.u32 -> IoTypeName.U32
      Keyword.u64 -> IoTypeName.U64
      Keyword.f32 -> IoTypeName.F32
      Keyword.f64 -> IoTypeName.F64
      Keyword.char -> IoTypeName.Char
      Keyword.string -> IoTypeName.String
      is Keyword -> errorWit(location, "unexpected keyword: $identifier")
      is Identifier -> IoTypeName.Declared(identifier)
    }
  }

  context(issueCollector: IssueCollector)
  private fun readTypeList(
    name: String,
    min: Int,
    max: Int,
  ): List<IoTypeName> {
    require(min in 0..max)
    val result = mutableListOf<IoTypeName>()
    if (tryReadLiteral('<')) {
      skipWhitespace()
      result += readTypeName()
      while (true) {
        skipWhitespace()
        if (!tryReadLiteral(',')) break
        skipWhitespace()
        result += readTypeName()
      }
      readLiteral('>')
    }
    checkWit(result.size in min..max) { "unexpected type parameters for $name" }
    return result
  }

  /**
   * ```ebnf
   * package-decl        ::= 'package' ( id ':' )+ id ( '/' id )* ('@' valid-semver)?
   * ```
   */
  context(issueCollector: IssueCollector)
  fun readPackageName(): PackageName {
    val namespaces = mutableListOf<Identifier>()
    val names = mutableListOf<Identifier>()

    var identifier = readIdentifier()
    while (peek() == ':') {
      pos++ // Consume ':'.
      namespaces += identifier
      identifier = readIdentifier()
    }
    checkWit(namespaces.isNotEmpty(), location) {
      "expected package name to contain a ':'"
    }
    while (peek() == '/') {
      pos++ // Consume '/'.
      names += identifier
      identifier = readIdentifier()
    }
    names += identifier

    val version = when {
      peek() == '@' -> {
        pos++ // Consume '@'.
        readSemVer()
      }

      else -> null
    }

    return PackageName(
      namespaces = namespaces,
      names = names,
      version = version,
    )
  }

  /**
   * ```ebnf
   * use-path ::= id
   *            | id ':' id '/' id ('@' valid-semver)?
   *            | ( id ':' )+ id ( '/' id )+ ('@' valid-semver)?
   * ```
   */
  context(issueCollector: IssueCollector)
  fun readUsePath(): UsePath {
    val namespaces = mutableListOf<Identifier>()
    val packageNames = mutableListOf<Identifier>()

    var identifier = readIdentifier()
    while (peek() == ':') {
      pos++ // Consume ':'.
      namespaces += identifier
      identifier = readIdentifier()
    }
    while (peek() == '/') {
      pos++ // Consume '/'.
      packageNames += identifier
      identifier = readIdentifier()
    }

    if (namespaces.isEmpty() && packageNames.isEmpty()) {
      return UsePath(name = identifier)
    }

    checkWit(namespaces.isNotEmpty() && packageNames.isNotEmpty(), location) {
      "must have a namespace and a package name, or neither"
    }

    val version = when {
      namespaces.isNotEmpty() && peek() == '@' -> {
        pos++ // Consume '@'.
        readSemVer()
      }

      else -> null
    }

    return UsePath(
      namespaces = namespaces,
      packageNames = packageNames,
      name = identifier,
      version = version,
    )
  }

  fun skipWhitespace() {
    while (!exhausted) {
      val c = chars[pos]
      when (c) {
        ' ', '\t', '\r' -> {
          pos++
          continue
        }

        '\n' -> {
          pos++
          line++
          lineStart = pos
          continue
        }

        '/' if (pos + 1 < chars.size) -> {
          val afterSlash = chars[pos + 1]
          when (afterSlash) {
            '/' -> {
              val commentEnd = chars.indexOf("\n", pos)
              if (commentEnd == -1) {
                pos = chars.size
                return
              }
              // If the comment starts with '///', it is documentation.
              if (pos + 2 < chars.size && chars[pos + 2] == '/') {
                appendDocumentation(pos + 3, commentEnd)
              }
              pos = commentEnd + 1
              line++
              lineStart = pos
              continue
            }

            '*' -> {
              val commentEnd = chars.indexOf("*/", pos + 2)
              checkWit(commentEnd != -1) { "unterminated comment" }

              // If the comment starts with '/**', it is documentation.
              if (chars[pos + 2] == '*') {
                appendDocumentation(pos + 3, commentEnd)
              }
              for (j in pos until commentEnd + 2) {
                pos++
                if (chars[j] == '\n') {
                  line++
                  lineStart = pos
                }
              }
              continue
            }
          }
        }
      }

      return
    }
  }

  /**
   * Reads a list of [minSize] or more items, wrapped in braces and separated by commas. Trailing
   * commas are okay.
   */
  fun <T> readCommaSeparatedList(
    minSize: Int = 1,
    open: Char = '{',
    close: Char = '}',
    readItem: () -> T,
  ): List<T> {
    val result = mutableListOf<T>()

    skipWhitespace()
    readLiteral(open)
    skipWhitespace()
    while (result.size < minSize || !tryReadLiteral(close)) {
      result += readItem()

      skipWhitespace()
      if (!tryReadLiteral(',')) {
        readLiteral(close)
        break
      }

      skipWhitespace()
    }

    return result
  }

  internal inline fun checkWit(value: Boolean, message: () -> String) {
    checkWit(value, location, message)
  }
}

internal inline fun checkWit(
  value: Boolean,
  location: Location,
  message: () -> String,
) {
  contract {
    returns() implies value
  }
  if (!value) {
    throw WitException(
      description = message(),
      location = location,
    )
  }
}


internal fun errorWit(location: Location, message: String): Nothing {
  throw WitException(
    description = message,
    location = location,
  )
}

internal fun CharArray.indexOf(substring: String, fromIndex: Int): Int {
  require(substring.isNotEmpty())
  search@
  for (i in fromIndex..size - substring.length) {
    for ((j, c) in substring.withIndex()) {
      if (this[i + j] != c) continue@search
    }
    return i
  }
  return -1
}

internal fun CharArray.indexOfNextNonMatch(
  fromIndex: Int,
  match: (Char) -> Boolean,
): Int {
  for (i in fromIndex until size) {
    if (match(this[i])) continue
    else return i
  }
  return size
}

internal val Char.isWordCharacter: Boolean
  get() = when (this) {
    in 'a'..'z' -> true
    in 'A'..'Z' -> true
    in '0'..'9' -> true
    '-' -> true
    else -> false
  }

/** https://semver.org/spec/v2.0.0.html */
internal val Char.isSemverCharacter: Boolean
  get() = when (this) {
    in 'a'..'z' -> true
    in 'A'..'Z' -> true
    in '0'..'9' -> true
    '-', '+', '.' -> true
    else -> false
  }

internal val Char.isDigit: Boolean
  get() = when (this) {
    in '0'..'9' -> true
    else -> false
  }
