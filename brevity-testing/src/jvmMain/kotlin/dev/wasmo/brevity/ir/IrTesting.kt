package dev.wasmo.brevity.ir

import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.FunctionNameWorld
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.Identifier.Companion.Identifier
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.io.toServiceName

fun IrCase(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  type: TypeName? = null,
) = IrCase(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  type = type,
)

fun TypeNameDeclared(
  serviceName: String,
  typeName: String,
) = TypeName.Declared(
  serviceName = serviceName.toServiceName(),
  name = Identifier(typeName),
)

fun IrEnum(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  name: String,
  cases: List<IrCase>,
) = IrEnum(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = TypeNameDeclared(serviceName, name),
  cases = cases,
)

fun IrExternalApi(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  plainName: String? = null,
  packageName: String,
  serviceName: String,
) = IrExternalApi(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  plainName = plainName?.let { Identifier(it) },
  serviceName = ServiceName(packageName, serviceName),
)

fun IrFlags(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  name: String,
  flags: List<IrFlag>,
) = IrFlags(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = TypeNameDeclared(serviceName, name),
  flags = flags,
)

fun IrField(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
  type: TypeName,
) = IrField(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
  type = type,
)

fun IrFlag(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  name: String,
) = IrFlag(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  name = Identifier(name),
)

fun IrFunction(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  async: Boolean = false,
  name: String,
  parameters: List<IrParameter> = listOf(),
  returnType: TypeName? = null,
  functionName: FunctionName = FunctionNameWorld(name),
) = IrFunction(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  async = async,
  parameters = parameters,
  returnType = returnType,
  functionName = functionName,
)

fun IrInterface(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  items: List<IrInterface.Item> = listOf(),
) = IrInterface(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  serviceName = serviceName.toServiceName().constrain(),
  items = items,
)

fun IrParameter(
  documentation: String? = null,
  location: Location = Location("file.wit"),
  name: String,
  type: TypeName,
) = IrParameter(
  documentation = documentation?.let { Documentation(it) },
  location = location,
  name = Identifier(name),
  type = type,
)

fun IrRecord(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  name: String,
  fields: List<IrField>,
) = IrRecord(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = TypeNameDeclared(serviceName, name),
  fields = fields,
)

fun IrResource(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  name: String,
  functions: List<IrFunction> = listOf(),
) = IrResource(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = TypeNameDeclared(serviceName, name),
  functions = functions,
)

fun IrTypeAlias(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  name: String,
  target: TypeName,
) = IrTypeAlias(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = TypeNameDeclared(
    serviceName = serviceName,
    typeName = name,
  ),
  target = target,
)

fun IrVariant(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  name: String,
  cases: List<IrCase>,
) = IrVariant(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  type = TypeNameDeclared(serviceName, name),
  cases = cases,
)

fun IrWorld(
  documentation: String? = null,
  gate: Gate? = null,
  location: Location = Location("file.wit"),
  serviceName: String,
  types: List<IrTypeDeclaration> = listOf(),
  imports: List<IrWorld.Api> = listOf(),
  exports: List<IrWorld.Api> = listOf(),
) = IrWorld(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  location = location,
  serviceName = serviceName.toServiceName().constrain(),
  types = types,
  imports = imports,
  exports = exports,
)
