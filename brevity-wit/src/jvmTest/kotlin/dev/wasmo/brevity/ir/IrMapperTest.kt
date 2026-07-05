package dev.wasmo.brevity.ir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.wasmo.brevity.Annotation
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoTypeName
import dev.wasmo.brevity.io.toUsePath
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath

class IrMapperTest {
  @Test
  fun `find local symbols`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:clocks".toPackageName(),
        files = mapOf(
          "clock.wit".toPath() to """
            |package wasi:clocks;
            |
            |interface wall-clock {
            |    record datetime {
            |        seconds: u64,
            |    }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        contextPackageName = "wasi:clocks",
        contextServiceName = "wall-clock",
        typeName = IoTypeName.Declared("datetime"),
      ),
    ).isEqualTo(
      IrTypeName.Declared(
        packageName = "wasi:clocks".toPackageName(),
        serviceName = Identifier("wall-clock"),
        name = Identifier("datetime"),
        codec = IrTypeName.Declared.Codec.Record,
      ),
    )

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        irMapper.getType(
          contextPackageName = "wasi:clocks",
          contextServiceName = "wall-clock",
          typeName = IoTypeName.Declared("instant"),
        )
      },
    ).hasMessage("unable to find instant in wasi:clocks/wall-clock")
  }

  @Test
  fun `find symbols across packages with use`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli".toPackageName(),
        files = mapOf(
          "stdio.wit".toPath() to """
            |interface stdin {
            |  use wasi:io/streams@0.2.12.{input-stream};
            |
            |  get-stdin: func() -> input-stream;
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
      IoToplevelWitPackage(
        packageName = "wasi:io@0.2.12".toPackageName(),
        files = mapOf(
          "streams.wit".toPath() to """
            |package wasi:io@0.2.12;
            |
            |interface streams {
            |    resource input-stream {
            |        read: func(len: u64) -> result;
            |    }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        contextPackageName = "wasi:cli",
        contextServiceName = "stdin",
        typeName = IoTypeName.Declared("input-stream"),
      ),
    ).isEqualTo(
      IrTypeName.Declared(
        packageName = "wasi:io@0.2.12".toPackageName(),
        serviceName = Identifier("streams"),
        name = Identifier("input-stream"),
        codec = IrTypeName.Declared.Codec.Resource,
      ),
    )
  }

  @Test
  fun `find symbols across services with use`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli".toPackageName(),
        files = mapOf(
          "stdio.wit".toPath() to """
            |world stdin {
            |  use streams.{input-stream};
            |
            |  export get-stdin: func() -> input-stream;
            |}
            |
            |interface streams {
            |    resource input-stream {
            |        read: func(len: u64) -> result;
            |    }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        contextPackageName = "wasi:cli",
        contextServiceName = "stdin",
        typeName = IoTypeName.Declared("input-stream"),
      ),
    ).isEqualTo(
      IrTypeName.Declared(
        packageName = "wasi:cli".toPackageName(),
        serviceName = Identifier("streams"),
        name = Identifier("input-stream"),
        codec = IrTypeName.Declared.Codec.Resource,
      ),
    )
  }

  @Test
  fun `imports across packages`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli@0.3.0".toPackageName(),
        files = mapOf(
          "command.wit".toPath() to """
            |package wasi:cli@0.3.0;
            |
            |world command {
            |  include imports;
            |}
            """.trimMargin().toWitFile(),
          "imports.wit".toPath() to """
            |package wasi:cli@0.3.0;
            |
            |world imports {
            |  include wasi:clocks/imports@0.3.0;
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
      IoToplevelWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        files = mapOf(
          "world.wit".toPath() to """
            |package wasi:clocks@0.3.0;
            |
            |world imports {
            |  import monotonic-clock;
            |}
            """.trimMargin().toWitFile(),
          "monotonic-clock.wit".toPath() to """
            |package wasi:clocks@0.3.0;
            |
            |interface monotonic-clock {
            |  now: func() -> s64;
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)
    val irPackages = irMapper.map()

    assertThat(irPackages).containsExactly(
      IrWitPackage(
        packageName = "wasi:cli@0.3.0".toPackageName(),
        items = listOf(
          IrWorld(
            offset = Offset(3, 1),
            name = "command",
            imports = listOf(
              IrExternalApi(
                offset = Offset(4, 3),
                path = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
                functions = listOf(
                  IrFunction(
                    offset = Offset(4, 3),
                    returnType = IrTypeName.S64,
                    name = "now",
                    packageName = "wasi:clocks@0.3.0",
                    serviceName = "monotonic-clock",
                  ),
                ),
              ),
            ),
          ),
          IrWorld(
            offset = Offset(3, 1),
            name = "imports",
            imports = listOf(
              IrExternalApi(
                offset = Offset(4, 3),
                path = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
                functions = listOf(
                  IrFunction(
                    offset = Offset(4, 3),
                    returnType = IrTypeName.S64,
                    name = "now",
                    packageName = "wasi:clocks@0.3.0",
                    serviceName = "monotonic-clock",
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
      IrWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        items = listOf(
          IrWorld(
            offset = Offset(3, 1),
            name = "imports",
            imports = listOf(
              IrExternalApi(
                offset = Offset(4, 3),
                path = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
                functions = listOf(
                  IrFunction(
                    offset = Offset(4, 3),
                    returnType = IrTypeName.S64,
                    name = "now",
                    packageName = "wasi:clocks@0.3.0",
                    serviceName = "monotonic-clock",
                  ),
                ),
              ),
            ),
          ),
          IrInterface(
            offset = Offset(3, 1),
            name = "monotonic-clock",
            items = listOf(
              IrFunction(
                offset = Offset(4, 3),
                name = "now",
                returnType = IrTypeName.S64,
                packageName = "wasi:clocks@0.3.0",
                serviceName = "monotonic-clock",
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `find symbols in same package with use`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:clocks@0.2.12".toPackageName(),
        files = mapOf(
          "timezone.wit".toPath() to """
            |interface timezone {
            |    use wall-clock.{datetime};
            |
            |    display: func(when: datetime);
            |}
            """.trimMargin().toWitFile(),
          "wall-clock.wit".toPath() to """
            |package wasi:io@0.2.12;
            |
            |interface wall-clock {
            |    record datetime {
            |        seconds: u64,
            |        nanoseconds: u32,
            |    }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )

    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        contextPackageName = "wasi:clocks@0.2.12",
        contextServiceName = "timezone",
        typeName = IoTypeName.Declared("datetime"),
      ),
    ).isEqualTo(
      IrTypeName.Declared(
        packageName = "wasi:clocks@0.2.12".toPackageName(),
        serviceName = Identifier("wall-clock"),
        name = Identifier("datetime"),
        codec = IrTypeName.Declared.Codec.Record,
      ),
    )
  }

  @Test
  fun `get world`() {
    val wasiCli = IoToplevelWitPackage(
      packageName = "wasi:cli@0.2.12".toPackageName(),
      files = mapOf(
        "command.wit".toPath() to """
          |package wasi:cli@0.2.12;
          |
          |world command {
          |}
          """.trimMargin().toWitFile(),
      ),
    )
    val wasiIo = IoToplevelWitPackage(
      packageName = "wasi:io@0.2.12".toPackageName(),
      files = mapOf(
        "world.wit".toPath() to """
          |package wasi:io@0.2.12;
          |
          |world imports {
          |}
          """.trimMargin().toWitFile(),
      ),
    )

    val irMapper = IrMapper(listOf(wasiCli, wasiIo))

    assertThat(irMapper.getWorldOrNull("wasi:io/imports@0.2.12".toUsePath()))
      .isEqualTo(wasiIo.files.values.single().items.single())

    assertThat(irMapper.getWorldOrNull("wasi:cli/command@0.2.12".toUsePath()))
      .isEqualTo(wasiCli.files.values.single().items.single())

    assertThat(irMapper.getWorldOrNull("wasi:cli/command".toUsePath())).isNull()
  }

  @Test
  fun `interface function abi names`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        files = mapOf(
          "system-clock.wit".toPath() to """
            |interface system-clock {
            |  now: func() -> u64;
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irInterface = irPackage.items.single() as IrInterface
    val irFunction = irInterface.items.single() as IrFunction
    assertThat(irFunction.functionName).isEqualTo(
      FunctionName(
        name = "now",
        packageName = "wasi:clocks@0.3.0",
        serviceName = "system-clock",
      ),
    )
    assertThat(irFunction.functionName.abiName).isEqualTo("now")
  }

  @Test
  fun `resource function abi names`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:http@0.3.0".toPackageName(),
        files = mapOf(
          "types.wit".toPath() to """
            |interface types {
            |  resource fields {
            |    constructor();
            |    from-list: static func(entries: list<tuple<string,list<u8>>>) -> fields;
            |    has: func(name: string) -> bool;
            |    clone: func() -> fields;
            |  }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irInterface = irPackage.items.single() as IrInterface
    val irResource = irInterface.items.single() as IrResource
    assertThat(irResource.functions.map { it.functionName }).containsExactly(
      FunctionName(
        packageName = "wasi:http@0.3.0",
        serviceName = "types",
        name = "fields",
        annotation = Annotation.Constructor,
      ),
      FunctionName(
        packageName = "wasi:http@0.3.0",
        serviceName = "types",
        name = "from-list",
        resourceName = "fields",
        annotation = Annotation.Static,
      ),
      FunctionName(
        packageName = "wasi:http@0.3.0",
        serviceName = "types",
        name = "has",
        resourceName = "fields",
        annotation = Annotation.Method,
      ),
      FunctionName(
        packageName = "wasi:http@0.3.0",
        serviceName = "types",
        name = "clone",
        resourceName = "fields",
        annotation = Annotation.Method,
      ),
    )
  }

  @Test
  fun `resolve all type codecs`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "test:types".toPackageName(),
        files = mapOf(
          "types.wit".toPath() to """
            |package test:types;
            |
            |world all-types {
            |    type my-alias = tuple<my-resource, list<my-enum>>;
            |    record my-record {
            |        field: u64,
            |    }
            |    enum my-enum {
            |        red,
            |        blue,
            |    }
            |    flags my-flags {
            |        loaded,
            |        enabled,
            |    }
            |    resource my-resource {
            |        write: func(bytes: list<u8>);
            |    }
            |    variant my-variant {
            |        none,
            |        some(list<my-record>),
            |    }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )

    val irMapper = IrMapper(ioPackages)
    val packages = irMapper.map()
    val serviceName = "test:types/all-types"
    assertThat(packages).containsExactly(
      IrWitPackage(
        packageName = "test:types".toPackageName(),
        items = listOf(
          IrWorld(
            name = "all-types",
            offset = Offset(3, 1),
            items = listOf(
              IrTypeAlias(
                serviceName = serviceName,
                name = "my-alias",
                offset = Offset(4, 5),
                target = IrTypeName.Tuple(
                  types = listOf(
                    IrTypeNameDeclared(
                      serviceName = serviceName,
                      typeName = "my-resource",
                      codec = IrTypeName.Declared.Codec.Resource,
                    ),
                    IrTypeName.List(
                      IrTypeNameDeclared(
                        serviceName = serviceName,
                        typeName = "my-enum",
                        codec = IrTypeName.Declared.Codec.Enum,
                      ),
                    ),
                  ),
                ),
              ),
              IrRecord(
                serviceName = serviceName,
                name = "my-record",
                offset = Offset(5, 5),
                fields = listOf(
                  IrField(
                    offset = Offset(6, 9),
                    name = "field",
                    type = IrTypeName.U64,
                  ),
                ),
              ),
              IrEnum(
                serviceName = serviceName,
                name = "my-enum",
                offset = Offset(8, 5),
                cases = listOf(
                  IrCase(
                    offset = Offset(9, 9),
                    name = "red",
                  ),
                  IrCase(
                    offset = Offset(10, 9),
                    name = "blue",
                  ),
                ),
              ),
              IrFlags(
                serviceName = serviceName,
                name = "my-flags",
                offset = Offset(12, 5),
                flags = listOf(
                  IrFlag(
                    offset = Offset(13, 9),
                    name = "loaded",
                  ),
                  IrFlag(
                    offset = Offset(14, 9),
                    name = "enabled",
                  ),
                ),
              ),
              IrResource(
                serviceName = serviceName,
                name = "my-resource",
                offset = Offset(16, 5),
                functions = listOf(
                  IrFunction(
                    offset = Offset(17, 9),
                    name = "write",
                    parameters = listOf(
                      IrParameter(
                        offset = Offset(17, 21),
                        name = "bytes",
                        type = IrTypeName.List(IrTypeName.U8),
                      ),
                    ),
                    packageName = "test:types",
                    serviceName = "all-types",
                    resourceName = "my-resource",
                  ),
                ),

                ),
              IrVariant(
                serviceName = serviceName,
                name = "my-variant",
                offset = Offset(19, 5),
                cases = listOf(
                  IrCase(
                    offset = Offset(20, 9),
                    name = "none",
                  ),
                  IrCase(
                    offset = Offset(21, 9),
                    name = "some",
                    type = IrTypeName.List(
                      IrTypeNameDeclared(
                        serviceName = "test:types/all-types",
                        typeName = "my-record",
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    )
  }

  private fun IrMapper.getType(
    contextPackageName: String,
    contextServiceName: String,
    typeName: IoTypeName,
  ): IrTypeName {
    context(
      IrMapper.Context(
        packageName = contextPackageName.toPackageName(),
        serviceName = Identifier(contextServiceName),
      ),
    ) {
      return typeName.typeNameToIr()
    }
  }
}
