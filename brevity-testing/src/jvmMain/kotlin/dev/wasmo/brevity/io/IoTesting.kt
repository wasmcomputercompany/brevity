package dev.wasmo.brevity.io

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.PackageName

fun IoCase(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  type: IoTypeName? = null,
) = IoCase(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  type = type,
)

fun IoEnum(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  cases: List<IoCase>,
) = IoEnum(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  cases = cases,
)

fun IoExternalApi(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  plainName: String? = null,
  path: String,
) = IoExternalApi(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  plainName = plainName?.let { Identifier(it) },
  path = path.toUsePath(),
)

fun IoFlags(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  flags: List<IoFlag>,
) = IoFlags(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  flags = flags,
)

fun IoField(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  type: IoTypeName,
) = IoField(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  type = type,
)

fun IoFlag(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
) = IoFlag(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
)

fun IoFunction(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  async: Boolean = false,
  static: Boolean = false,
  constructor: Boolean = false,
  name: String,
  parameters: List<IoParameter> = listOf(),
  returnType: IoTypeName? = null,
) = IoFunction(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  async = async,
  static = static,
  constructor = constructor,
  name = Identifier(name),
  parameters = parameters,
  returnType = returnType,
)

fun IoInclude(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  path: String,
  items: List<IoInclude.Item> = listOf(),
) = IoInclude(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  path = path.toUsePath(),
  items = items,
)

fun IoIncludeItem(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  type: String,
  alias: String,
) = IoInclude.Item(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = IoTypeName.Declared(type),
  name = Identifier(alias),
)

fun IoInterface(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  items: List<IoInterface.Item> = listOf(),
) = IoInterface(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  items = items,
)

fun IoParameter(
  documentation: String? = null,
  location: Location = Location("file.wit"),
  name: String,
  type: IoTypeName,
) = IoParameter(
  documentation = documentation?.let { Documentation(it) },
  location = location,
  name = Identifier(name),
  type = type,
)

fun IoRecord(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  fields: List<IoField>,
) = IoRecord(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  fields = fields,
)

fun IoResource(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  functions: List<IoFunction> = listOf(),
) = IoResource(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  functions = functions,
)

fun IoTypeAlias(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  target: IoTypeName,
) = IoTypeAlias(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  target = target,
)

fun IoUse(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  path: String,
  items: List<IoUse.Item>,
) = IoUse(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  path = path.toUsePath(),
  items = items,
)

fun IoUseItem(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  type: String,
  alias: String? = null,
) = IoUse.Item(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = IoTypeName.Declared(type),
  alias = alias?.let { Identifier(it) },
)

fun IoVariant(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  cases: List<IoCase>,
) = IoVariant(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  cases = cases,
)

fun IoWitFile(
  packageDocumentation: Documentation? = null,
  packageName: PackageName,
  items: List<IoWitFile.Item> = listOf(),
  location: Location,
): IoWitFile = IoWitFile(
  packageDocumentation,
  IoPackageNameElement(packageName, location),
  items,
  location,
)


fun IoWorld(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  items: List<IoWorld.Item> = listOf(),
  imports: List<IoWorld.Api> = listOf(),
  exports: List<IoWorld.Api> = listOf(),
) = IoWorld(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  items = items,
  imports = imports,
  exports = exports,
)
