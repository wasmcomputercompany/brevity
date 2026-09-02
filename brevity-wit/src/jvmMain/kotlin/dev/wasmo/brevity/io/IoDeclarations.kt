package dev.wasmo.brevity.io

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.PackageName

/**
 * A collection of `.wit` files from a single file system directory.
 */
data class IoToplevelWitPackage(
  override val documentation: Documentation? = null,
  override val packageName: PackageName,
  val files: List<IoWitFile>,
) : IoWitPackage

data class IoWitFile(
  val packageDocumentation: Documentation? = null,
  val packageName: IoPackageNameElement? = null,
  val items: List<Item> = listOf(),
  val location: Location,
) {
  sealed interface Item : IoDeclaration
}

sealed interface IoElement {
  val location: Location
}

data class IoPackageNameElement(
  val packageName: PackageName,
  override val location: Location,
) : IoElement

sealed interface IoDeclaration : IoElement {
  val documentation: Documentation?
  val gate: Gate?
}

/**
 * Common interface for anything that has an identifier that functions as a legible name.
 */
sealed interface IoNamedDeclaration : IoDeclaration {
  val name: IoIdentifier
}

sealed interface IoService: IoDeclaration {
  val name: IoIdentifier
}

sealed interface IoWitPackage {
  val documentation: Documentation?
  val packageName: PackageName
}

sealed interface IoTypeDeclaration : IoNamedDeclaration, IoInterface.Item, IoWorld.Item

/**
 * An inline package.
 *
 * ```wit
 * package local:a {
 *     interface foo {}
 * }
 * ```
 */
data class IoInlinePackage(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val location: Location,
  override val packageName: PackageName,
  val declarations: List<IoWitFile.Item>,
) : IoDeclaration, IoWitFile.Item, IoWitPackage

/**
 * Declarations may be:
 *
 *  * [IoUse]
 *  * Type Declarations
 *    * [IoResource]
 *    * [IoRecord]
 *    * [IoVariant]
 *    * [IoEnum]
 *    * [IoFlags]
 *    * [IoTypeAlias]
 *  * [IoFunction]
 */
data class IoInterface(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val items: List<Item>,
) : IoDeclaration, IoService, IoWorld.Api, IoWitFile.Item {
  sealed interface Item : IoDeclaration
}

data class IoWorld(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val items: List<Item>,
    val imports: List<Api>,
    val exports: List<Api>,
) : IoDeclaration, IoService, IoWitFile.Item {
  sealed interface Api : IoDeclaration
  sealed interface Item : IoDeclaration
}

data class IoResource(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val functions: List<IoFunction>,
) : IoTypeDeclaration

data class IoRecord(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val fields: List<IoField>,
) : IoTypeDeclaration

data class IoField(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val type: IoTypeName,
) : IoNamedDeclaration

data class IoFunction(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val async: Boolean = false,
    val static: Boolean = false,
    val constructor: Boolean = false,
    val parameters: List<IoParameter>,
    val returnType: IoTypeName? = null,
) : IoNamedDeclaration, IoWorld.Api, IoInterface.Item

data class IoVariant(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val cases: List<IoCase>,
) : IoTypeDeclaration

data class IoEnum(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val cases: List<IoCase>,
) : IoTypeDeclaration

data class IoCase(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val type: IoTypeName? = null,
) : IoNamedDeclaration

data class IoParameter(
    override val documentation: Documentation? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val type: IoTypeName,
) : IoNamedDeclaration {
  override val gate: Gate? = null
}

data class IoFlags(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val flags: List<IoFlag>,
) : IoTypeDeclaration

data class IoFlag(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
) : IoNamedDeclaration

data class IoTypeAlias(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    override val name: IoIdentifier,
    val target: IoTypeName,
) : IoTypeDeclaration

data class IoTopLevelUse(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    val path: UsePath,
    val alias: IoIdentifier? = null,
) : IoDeclaration, IoWitFile.Item

/**
 * Examples:
 *
 * ```wit
 * use wasi:http/types@1.0.0.{request, response};
 * use types.{request, response};
 * use types.{errno};
 * use types.{errno as my-errno};
 * ```
 */
data class IoUse(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val location: Location,
  val path: UsePath,
  val items: List<Item>,
) : IoDeclaration, IoInterface.Item, IoWorld.Item {

  data class Item(
      override val documentation: Documentation? = null,
      override val gate: Gate? = null,
      override val location: Location,
      val type: IoTypeName.Declared,
      val alias: IoIdentifier? = null,
  ) : IoNamedDeclaration {
    override val name: IoIdentifier
      get() = alias ?: type.name
  }
}

data class IoExternalApi(
    override val documentation: Documentation? = null,
    override val gate: Gate? = null,
    override val location: Location,
    val plainName: IoIdentifier? = null,
    val path: UsePath,
) : IoDeclaration, IoWorld.Api

/**
 * Examples:
 *
 * ```wit
 * include wasi:io/my-world-1 with { a as a1, b as b1 };
 * include my-world-2;
 * ```
 */
data class IoInclude(
  override val documentation: Documentation? = null,
  override val gate: Gate? = null,
  override val location: Location,
  val path: UsePath,
  val items: List<Item>,
) : IoDeclaration, IoWorld.Item {
  data class Item(
      override val documentation: Documentation? = null,
      override val gate: Gate? = null,
      override val location: Location,
      override val name: IoIdentifier,
      val type: IoTypeName.Declared,
  ) : IoNamedDeclaration
}
