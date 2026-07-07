package dev.wasmo.brevity.ir

import dev.wasmo.brevity.Annotation
import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.io.toServiceName

fun IrCase(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  name: String,
  type: IrTypeName? = null,
) = IrCase(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  name = Identifier(name),
  type = type,
)

fun IrTypeNameDeclared(
  serviceName: String,
  typeName: String,
) = IrTypeName.Declared(
  serviceName = serviceName.toServiceName(),
  name = Identifier(typeName),
)

fun IrEnum(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  name: String,
  cases: List<IrCase>,
) = IrEnum(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  type = IrTypeNameDeclared(serviceName, name),
  cases = cases,
)

fun IrExternalApi(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  plainName: String? = null,
  packageName: String,
  serviceName: String,
) = IrExternalApi(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  plainName = plainName?.let { Identifier(it) },
  path = ServiceName(packageName, serviceName),
)

fun IrFlags(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  name: String,
  flags: List<IrFlag>,
) = IrFlags(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  type = IrTypeNameDeclared(serviceName, name),
  flags = flags,
)

fun IrField(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  name: String,
  type: IrTypeName,
) = IrField(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  name = Identifier(name),
  type = type,
)

fun IrFlag(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  name: String,
) = IrFlag(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  name = Identifier(name),
)

fun IrFunction(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  async: Boolean = false,
  name: String,
  parameters: List<IrParameter> = listOf(),
  returnType: IrTypeName? = null,
  serviceName: String,
  resourceName: String? = null,
  annotation: Annotation? = resourceName?.let { Annotation.Method },
) = IrFunction(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  async = async,
  parameters = parameters,
  returnType = returnType,
  functionName = FunctionName(
    serviceName = serviceName.toServiceName(),
    name = Identifier(name),
    resourceName = resourceName?.let { Identifier(it) },
    annotation = annotation,
  ),
)

fun IrInterface(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  items: List<IrInterface.Item> = listOf(),
) = IrInterface(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  name = serviceName.toServiceName(),
  items = items,
)

fun IrParameter(
  documentation: String? = null,
  offset: Offset = Offset(1, 1),
  name: String,
  type: IrTypeName,
) = IrParameter(
  documentation = documentation?.let { Documentation(it) },
  offset = offset,
  name = Identifier(name),
  type = type,
)

fun IrRecord(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  name: String,
  fields: List<IrField>,
) = IrRecord(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  type = IrTypeNameDeclared(serviceName, name),
  fields = fields,
)

fun IrResource(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  name: String,
  functions: List<IrFunction> = listOf(),
) = IrResource(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  type = IrTypeNameDeclared(serviceName, name),
  functions = functions,
)

fun IrTypeAlias(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  name: String,
  target: IrTypeName,
) = IrTypeAlias(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  type = IrTypeNameDeclared(
    serviceName = serviceName,
    typeName = name,
  ),
  target = target,
)

fun IrVariant(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  name: String,
  cases: List<IrCase>,
) = IrVariant(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  type = IrTypeNameDeclared(serviceName, name),
  cases = cases,
)

fun IrWorld(
  documentation: String? = null,
  gate: Gate? = null,
  offset: Offset = Offset(1, 1),
  serviceName: String,
  items: List<IrWorld.Item> = listOf(),
  imports: List<IrWorld.Api> = listOf(),
  exports: List<IrWorld.Api> = listOf(),
) = IrWorld(
  documentation = documentation?.let { Documentation(it) },
  gate = gate,
  offset = offset,
  name = serviceName.toServiceName(),
  items = items,
  imports = imports,
  exports = exports,
)
