@file:OptIn(WitCoreInternalApi::class)

package dev.wasmo.brevity.io

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Identifier.Companion.deprecated
import dev.wasmo.brevity.Identifier.Companion.feature
import dev.wasmo.brevity.Identifier.Companion.since
import dev.wasmo.brevity.Identifier.Companion.unstable
import dev.wasmo.brevity.Identifier.Companion.version
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.Keyword
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.SemVer
import dev.wasmo.brevity.WitCoreInternalApi

context(issueCollector: IssueCollector)
fun String.toWitFile(location: Location): IoWitFile = WitFileReader(location, this).read()

internal class WitFileReader(
  private val source: WitSyntaxReader,
) {
  constructor(
    baseLocation: Location,
    string: String,
  ) : this(WitSyntaxReader(baseLocation, string))

  context(issueCollector: IssueCollector)
  fun read(): IoWitFile {
    val items = mutableListOf<IoWitFile.Item>()

    var packageIdentifier: IoInlinePackage? = null

    while (true) {
      source.skipWhitespace()
      if (source.exhausted) break

      val gate = readGateOrNull()
      val documentation = source.takeDocumentation()
      val location = source.location

      when (val identifier = source.readKeyword()) {
        Keyword.`package` -> {
          val (value, kind) = readPackage(documentation, gate, location)
          when (kind) {
            PackageKind.Identifier -> {
              checkWit(packageIdentifier == null && items.isEmpty(), location) {
                "unexpected package identifier"
              }
              packageIdentifier = value
            }

            PackageKind.Nested -> {
              items += value
            }
          }
        }

        Keyword.`interface` -> {
          items += readInterface(documentation, gate, location)
        }

        Keyword.use -> {
          items += readTopLevelUse(documentation, gate, location)
        }

        Keyword.world -> {
          items += readWorld(documentation, gate, location)
        }

        else -> errorWit(location, "unexpected identifier: $identifier")
      }
    }

    return IoWitFile(
      packageDocumentation = packageIdentifier?.documentation,
      packageName = if (packageIdentifier != null) {
        IoPackageNameElement(packageIdentifier.packageName, packageIdentifier.location)
      } else {
        null
      },
      items = items,
      location = source.location.at(null, null),
    )
  }

  /**
   * Reads either a package identifier or a nested package.
   *
   * ```ebnf
   * nested-package-definition ::= package-decl '{' package-items* '}'
   * package-items ::= toplevel-use-item | interface-item | world-item
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readPackage(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): Pair<IoInlinePackage, PackageKind> {
    source.skipWhitespace()
    val name = source.readPackageName()
    val declarations = mutableListOf<IoWitFile.Item>()

    source.skipWhitespace()
    val packageKind = when {
      source.tryReadLiteral('{') -> {
        while (true) {
          source.skipWhitespace()
          if (source.tryReadLiteral('}')) break

          val nestedGate = readGateOrNull()
          val nestedDocumentation = source.takeDocumentation()
          val nestedLocation = source.location

          declarations += when (val identifier = source.readKeyword()) {
            Keyword.`interface` -> readInterface(nestedDocumentation, nestedGate, nestedLocation)
            Keyword.use -> readTopLevelUse(nestedDocumentation, nestedGate, nestedLocation)
            Keyword.world -> readWorld(nestedDocumentation, nestedGate, nestedLocation)
            else -> errorWit(nestedLocation, "unexpected identifier: $identifier")
          }
        }

        PackageKind.Nested
      }

      else -> {
        source.readLiteral(';')
        PackageKind.Identifier
      }
    }

    return IoInlinePackage(
      documentation = documentation,
      gate = gate,
      location = location,
      packageName = name,
      declarations = declarations,
    ) to packageKind
  }

  private enum class PackageKind {
    /** The package of the top of a .wit document. This must not have '{' curly braces '}'. */
    Identifier,

    /** A nested package. This must have '{' curly braces '}'. */
    Nested
  }

  context(issueCollector: IssueCollector)
  private fun readInterfaceItems(): List<IoInterface.Item> {
    source.skipWhitespace()
    source.readLiteral('{')

    val result = mutableListOf<IoInterface.Item>()
    while (true) {
      source.skipWhitespace()
      if (source.tryReadLiteral('}')) break

      result += readInterfaceItem()
    }
    return result
  }

  /**
   * ```ebnf
   * interface-item ::= gate 'interface' id '{' interface-items* '}'
   * ```
   */
  context(issueCollector: IssueCollector)
  internal fun readInterface(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoInterface {
    source.skipWhitespace()
    val name = source.readIdentifier()
    val declarations = readInterfaceItems()
    return IoInterface(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      items = declarations,
    )
  }

  /**
   * ```ebnf
   * interface-items ::= gate interface-definition
   *
   * interface-definition ::= typedef-item
   *                        | use-item
   *                        | func-item
   *
   * typedef-item ::= resource-item
   *                | variant-items
   *                | record-item
   *                | flags-items
   *                | enum-items
   *                | type-item
   * ```
   */
  context(issueCollector: IssueCollector)
  internal fun readInterfaceItem(): IoInterface.Item {
    val gate = readGateOrNull()
    val documentation = source.takeDocumentation()
    val location = source.location

    val identifier = source.readIdentifierOrKeyword()

    return when (identifier){
      Keyword.enum -> readEnum(documentation, gate, location)
      Keyword.flags -> readFlags(documentation, gate, location)
      Keyword.record -> readRecord(documentation, gate, location)
      Keyword.resource -> readResource(documentation, gate, location)
      Keyword.variant -> readVariant(documentation, gate, location)
      Keyword.type -> readTypeAlias(documentation, gate, location)
      Keyword.use -> readUse(documentation, gate, location)
      is Keyword -> errorWit(location, "unescaped keyword used as interface item: $identifier")
      is Identifier -> readFuncItem(documentation, gate, location, identifier)
    }
  }

  /**
   * ```ebnf
   * record-item ::= 'record' id '{' record-fields '}'
   *
   * record-fields ::= record-field
   *                 | record-field ',' record-fields?
   *
   * record-field ::= id ':' ty
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readRecord(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoRecord {
    source.skipWhitespace()
    val name = source.readIdentifier()

    val fields = source.readCommaSeparatedList {
      val fieldGate = readGateOrNull()
      val fieldDocumentation = source.takeDocumentation()
      val fieldLocation = source.location
      val fieldName = source.readIdentifier()

      source.skipWhitespace()
      source.readLiteral(':')

      source.skipWhitespace()
      val fieldType = source.readTypeName()

      IoField(
        documentation = fieldDocumentation,
        gate = fieldGate,
        location = fieldLocation,
        name = fieldName,
        type = fieldType,
      )
    }

    return IoRecord(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      fields = fields,
    )
  }

  /**
   * ```ebnf
   * variant-items ::= 'variant' id '{' variant-cases '}'
   *
   * variant-cases ::= variant-case
   *                 | variant-case ',' variant-cases?
   *
   * variant-case ::= id
   *                | id '(' ty ')'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readVariant(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoVariant {
    source.skipWhitespace()
    val name = source.readIdentifier()

    val cases = source.readCommaSeparatedList {
      val caseGate = readGateOrNull()
      val caseDocumentation = source.takeDocumentation()
      val caseLocation = source.location
      val caseName = source.readIdentifier()

      source.skipWhitespace()
      val typeName = when {
        source.tryReadLiteral('(') -> {
          source.readTypeName()
            .also {
              source.skipWhitespace()
              source.readLiteral(')')
            }

        }

        else -> null
      }

      IoCase(
        documentation = caseDocumentation,
        gate = caseGate,
        location = caseLocation,
        name = caseName,
        type = typeName,
      )
    }

    return IoVariant(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      cases = cases,
    )
  }

  /**
   * ```ebnf
   * resource-item ::= 'resource' id ';'
   *                 | 'resource' id '{' resource-method* '}'
   * resource-method ::= func-item
   *                   | id ':' 'static' func-type ';'
   *                   | 'constructor' param-list ';'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readResource(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoResource {
    source.skipWhitespace()
    val name = source.readIdentifier()
    val declarations = mutableListOf<IoFunction>()

    source.skipWhitespace()
    if (source.tryReadLiteral('{')) {
      while (true) {
        source.skipWhitespace()
        if (source.tryReadLiteral('}')) break

        val functionGate = readGateOrNull()
        val functionDocumentation = source.takeDocumentation()
        val functionLocation = source.location

        val identifier = source.readIdentifierOrKeyword()

        when (identifier) {
          Keyword.constructor -> {
            val parameters = readParameterList()
            source.skipWhitespace()
            source.readLiteral(';')
            declarations += IoFunction(
              documentation = functionDocumentation,
              gate = functionGate,
              location = functionLocation,
              constructor = true,
              name = Identifier(Keyword.constructor.name),
              parameters = parameters,
            )
          }

          is Keyword -> errorWit(location, "unescaped keyword used as resource item: $identifier")

          is Identifier -> {
            declarations += readFuncItem(
              documentation = functionDocumentation,
              gate = functionGate,
              location = functionLocation,
              identifier = identifier,
            )
          }
        }
      }
    } else {
      source.readLiteral(';')
    }

    return IoResource(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      functions = declarations,
    )
  }

  /**
   * ```ebnf
   * flags-items ::= 'flags' id '{' flags-fields '}'
   *
   * flags-fields ::= id
   *                | id ',' flags-fields?
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readFlags(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoFlags {
    source.skipWhitespace()
    val name = source.readIdentifier()

    val flags = source.readCommaSeparatedList {
      val flagGate = readGateOrNull()
      val flagDocumentation = source.takeDocumentation()
      val flagLocation = source.location
      val flagName = source.readIdentifier()

      IoFlag(
        documentation = flagDocumentation,
        gate = flagGate,
        location = flagLocation,
        name = flagName,
      )
    }

    return IoFlags(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      flags = flags,
    )
  }

  /**
   * ```ebnf
   * enum-items ::= 'enum' id '{' enum-cases '}'
   *
   * enum-cases ::= id
   *              | id ',' enum-cases?
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readEnum(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoEnum {
    source.skipWhitespace()
    val name = source.readIdentifier()

    val cases = source.readCommaSeparatedList {
      val caseGate = readGateOrNull()
      val caseDocumentation = source.takeDocumentation()
      val caseLocation = source.location
      val caseName = source.readIdentifier()

      IoCase(
        documentation = caseDocumentation,
        gate = caseGate,
        location = caseLocation,
        name = caseName,
      )
    }

    return IoEnum(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      cases = cases,
    )
  }

  /**
   * ```ebnf
   * type-item ::= 'type' id '=' ty ';'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readTypeAlias(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoTypeAlias {
    source.skipWhitespace()
    val name = source.readIdentifier()

    source.skipWhitespace()
    source.readLiteral('=')

    source.skipWhitespace()
    val type = source.readTypeName()

    source.skipWhitespace()
    source.readLiteral(';')

    return IoTypeAlias(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      target = type,
    )
  }

  /**
   * ```ebnf
   * toplevel-use-item ::= 'use' use-path ('as' id)? ';'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readTopLevelUse(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoTopLevelUse {
    source.skipWhitespace()
    val path = source.readUsePath()

    source.skipWhitespace()
    val alias = when {
      source.tryReadLiteral("as") -> {
        source.skipWhitespace()
        source.readIdentifier()
      }

      else -> null
    }

    source.skipWhitespace()
    source.readLiteral(';')

    return IoTopLevelUse(
      documentation = documentation,
      gate = gate,
      location = location,
      path = path,
      alias = alias,
    )
  }

  /**
   * ```ebnf
   * use-item ::= 'use' use-path '.' '{' use-names-list '}' ';'
   *
   * use-names-list ::= use-names-item
   *                  | use-names-item ',' use-names-list?
   *
   * use-names-item ::= id
   *                  | id 'as' id
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readUse(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoUse {
    source.skipWhitespace()
    val path = source.readUsePath()

    source.skipWhitespace()
    source.readLiteral('.')

    val items = source.readCommaSeparatedList {
      val itemGate = readGateOrNull()
      val itemDocumentation = source.takeDocumentation()
      val itemLocation = source.location
      val itemName = source.readIdentifier()

      source.skipWhitespace()
      val alias = when {
        source.tryReadLiteral("as") -> {
          source.skipWhitespace()
          source.readIdentifier()
        }

        else -> null
      }

      IoUse.Item(
        gate = itemGate,
        documentation = itemDocumentation,
        location = itemLocation,
        type = IoTypeName.Declared(itemName),
        alias = alias,
      )
    }

    source.skipWhitespace()
    source.readLiteral(';')

    return IoUse(
      documentation = documentation,
      gate = gate,
      location = location,
      path = path,
      items = items,
    )
  }

  /**
   * ```ebnf
   * func-type ::= 'async'? 'func' param-list result-list
   *
   * result-list ::= ϵ
   *               | '->' ty
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readFuncType(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
    identifier: Identifier,
  ): IoFunction {
    var async = false
    var static = false

    while (true) {
      source.skipWhitespace()
      when (val modifier = source.readKeyword()) {
        Keyword.async -> async = true
        Keyword.static -> static = true
        Keyword.func -> break
        else -> errorWit(location, "unexpected keyword: $modifier")
      }
    }

    val parameters = readParameterList()

    source.skipWhitespace()
    val returnType = when {
      source.tryReadLiteral("->") -> {
        source.skipWhitespace()
        source.readTypeName()
          .also { source.skipWhitespace() }
      }

      else -> null
    }

    source.skipWhitespace()
    source.readLiteral(';')

    return IoFunction(
      documentation = documentation,
      gate = gate,
      location = location,
      async = async,
      static = static,
      constructor = false,
      name = identifier,
      parameters = parameters,
      returnType = returnType,
    )
  }

  /**
   * ```ebnf
   * func-item ::= id ':' func-type ';'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readFuncItem(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
    identifier: Identifier,
  ): IoFunction {
    source.skipWhitespace()
    source.readLiteral(':')
    return readFuncType(documentation, gate, location, identifier)
  }

  /**
   * ```ebnf
   * param-list ::= '(' named-type-list ')'
   *
   * named-type-list ::= ϵ
   *                   | named-type ( ',' named-type )*
   *
   * named-type ::= id ':' ty
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readParameterList(): List<IoParameter> {
    return source.readCommaSeparatedList(minSize = 0, '(', ')') {
      val documentation = source.takeDocumentation()
      val location = source.location
      val parameterName = source.readIdentifier()

      source.skipWhitespace()
      source.readLiteral(':')

      source.skipWhitespace()
      val parameterType = source.readTypeName()

      IoParameter(
        documentation = documentation,
        location = location,
        name = parameterName,
        type = parameterType,
      )
    }
  }

  /**
   * ```ebnf
   * world-item ::= gate 'world' id '{' world-items* '}'
   *
   * world-items ::= gate world-definition
   *
   * world-definition ::= export-item
   *                    | import-item
   *                    | use-item
   *                    | typedef-item
   *                    | include-item
   * ```
   */
  context(issueCollector: IssueCollector)
  internal fun readWorld(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoWorld {
    source.skipWhitespace()
    val name = source.readIdentifier()

    source.skipWhitespace()
    source.readLiteral('{')

    val items = mutableListOf<IoWorld.Item>()
    val imports = mutableListOf<IoWorld.Api>()
    val exports = mutableListOf<IoWorld.Api>()

    while (true) {
      source.skipWhitespace()
      if (source.tryReadLiteral('}')) break

      val itemGate = readGateOrNull()
      val itemDocumentation = source.takeDocumentation()
      val itemLocation = source.location
      when (val identifier = source.readKeyword()) {
        Keyword.enum -> items += readEnum(itemDocumentation, itemGate, itemLocation)
        Keyword.export -> exports += readWorldApi(itemDocumentation, itemGate, itemLocation)
        Keyword.flags -> items += readFlags(itemDocumentation, itemGate, itemLocation)
        Keyword.import -> imports += readWorldApi(itemDocumentation, itemGate, itemLocation)
        Keyword.include -> items += readInclude(itemDocumentation, itemGate, itemLocation)
        Keyword.record -> items += readRecord(itemDocumentation, itemGate, itemLocation)
        Keyword.resource -> items += readResource(itemDocumentation, itemGate, itemLocation)
        Keyword.type -> items += readTypeAlias(itemDocumentation, itemGate, itemLocation)
        Keyword.use -> items += readUse(itemDocumentation, itemGate, itemLocation)
        Keyword.variant -> items += readVariant(itemDocumentation, itemGate, itemLocation)
        else -> errorWit(location, "unexpected keyword: $identifier")
      }
    }

    return IoWorld(
      documentation = documentation,
      gate = gate,
      location = location,
      name = name,
      items = items,
      imports = imports,
      exports = exports,
    )
  }

  /**
   * ```ebnf
   * import-item ::= 'import' id ':' extern-type
   *               | 'import' use-path ';'
   * export-item ::= 'export' id ':' extern-type
   *               | 'export' use-path ';'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readWorldApi(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoWorld.Api {
    return source.select(
      {
        source.skipWhitespace()
        val identifier = source.readIdentifier()
        source.skipWhitespace()
        source.readLiteral(':')
        readExternalType(documentation, gate, location, identifier)
      },
      {
        source.skipWhitespace()
        val path = source.readUsePath()
        source.skipWhitespace()
        source.readLiteral(';')
        IoExternalApi(
          documentation = documentation,
          gate = gate,
          location = location,
          path = path,
        )
      },
    )
  }

  /**
   * ```ebnf
   * extern-type ::= func-type ';'
   *               | 'interface' '{' interface-items* '}'
   *               | use-path ';'
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readExternalType(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
    identifier: Identifier,
  ): IoWorld.Api {
    return source.select(
      {
        readFuncType(documentation, gate, location, identifier)
      },
      {
        source.skipWhitespace()
        source.readLiteral("interface")
        val declarations = readInterfaceItems()
        IoInterface(
          documentation = documentation,
          gate = gate,
          location = location,
          name = identifier,
          items = declarations,
        )
      },
      {
        source.skipWhitespace()
        val path = source.readUsePath()
        source.readLiteral(';')
        IoExternalApi(
          documentation = documentation,
          gate = gate,
          location = location,
          plainName = identifier,
          path = path,
        )
      },
    )
  }

  /**
   * ```ebnf
   * include-item ::= 'include' use-path ';'
   *                | 'include' use-path 'with' '{' include-names-list '}'
   *
   * include-names-list ::= include-names-item
   *                      | include-names-list ',' include-names-item
   *
   * include-names-item ::= id 'as' id
   * ```
   */
  context(issueCollector: IssueCollector)
  private fun readInclude(
    documentation: Documentation?,
    gate: Gate?,
    location: Location,
  ): IoInclude {
    source.skipWhitespace()
    val path = source.readUsePath()

    source.skipWhitespace()
    val items = when {
      source.tryReadLiteral("with") -> {
        source.readCommaSeparatedList {
          val itemGate = readGateOrNull()
          val itemDocumentation = source.takeDocumentation()
          val itemLocation = source.location
          val type = IoTypeName.Declared(
            source.readIdentifier()
          )

          source.skipWhitespace()
          source.readLiteral("as")

          source.skipWhitespace()
          val alias = source.readIdentifier()

          IoInclude.Item(
            documentation = itemDocumentation,
            gate = itemGate,
            location = itemLocation,
            type = type,
            name = alias,
          )
        }
      }

      else -> listOf()
    }

    source.skipWhitespace()
    source.readLiteral(';')

    return IoInclude(
      documentation = documentation,
      gate = gate,
      location = location,
      path = path,
      items = items,
    )
  }

  /**
   * ```ebnf
   * gate ::= gate-item*
   * gate-item ::= unstable-gate
   *             | since-gate
   *             | deprecated-gate
   *
   * unstable-gate ::= '@unstable' '(' feature-field ')'
   * since-gate ::= '@since' '(' version-field ')'
   * deprecated-gate ::= '@deprecated' '(' version-field ')'
   *
   * feature-field ::= 'feature' '=' id
   * version-field ::= 'version' '=' <valid semver>
   * ```
   */
  context(issueCollector: IssueCollector)
  internal fun readGateOrNull(): Gate? {
    var unstableFeature: Identifier? = null
    var sinceVersion: SemVer? = null
    var deprecatedVersion: SemVer? = null

    while (true) {
      val location = source.location
      val gateItem = source.readAnnotationOrNull() ?: break

      source.skipWhitespace()
      source.readLiteral('(')

      source.skipWhitespace()
      val fieldName = source.readIdentifier(unescaped = true)

      source.skipWhitespace()
      source.readLiteral('=')

      source.skipWhitespace()
      when {
        unstableFeature == null && gateItem == unstable && fieldName == feature -> {
          unstableFeature = source.readIdentifier()
        }

        sinceVersion == null && gateItem == since && fieldName == version -> {
          sinceVersion = source.readSemVer()
        }

        deprecatedVersion == null && gateItem == deprecated && fieldName == version -> {
          deprecatedVersion = source.readSemVer()
        }

        else -> errorWit(location, "unexpected field: $gateItem.$fieldName")
      }

      source.skipWhitespace()
      source.readLiteral(')')

      source.skipWhitespace()
    }

    if (unstableFeature == null && sinceVersion == null && deprecatedVersion == null) return null

    return Gate(
      unstable = unstableFeature,
      since = sinceVersion,
      deprecated = deprecatedVersion,
    )
  }
}

