package dev.wasmo.brevity.ir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.wasmo.brevity.FunctionNameConstructor
import dev.wasmo.brevity.FunctionNameInterface
import dev.wasmo.brevity.FunctionNameMethod
import dev.wasmo.brevity.FunctionNameResourceDrop
import dev.wasmo.brevity.FunctionNameStatic
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoTypeName
import dev.wasmo.brevity.io.IrMapper
import dev.wasmo.brevity.io.toServiceName
import dev.wasmo.brevity.io.toUsePath
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IrMapperTest {
  @Test
  fun `find local symbols`() {
    val clockLocation = Location("clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:clocks".toPackageName(),
        files = listOf(
          """
          |package wasi:clocks;
          |
          |interface wall-clock {
          |    record datetime {
          |        seconds: u64,
          |    }
          |}
          """.trimMargin().toWitFile(clockLocation),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        serviceName = "wasi:clocks/wall-clock",
        typeName = IoTypeName.Declared("datetime"),
      ),
    ).isEqualTo(
      TypeNameDeclared(
        serviceName = "wasi:clocks/wall-clock",
        typeName = "datetime",
      ),
    )

    assertThat(
      assertFailsWith<IllegalArgumentException> {
        irMapper.getType(
          serviceName = "wasi:clocks/wall-clock",
          typeName = IoTypeName.Declared("instant"),
        )
      },
    ).hasMessage("unable to find instant in wasi:clocks/wall-clock")
  }

  @Test
  fun `find symbols across packages with use`() {
    val stdioLocation = Location("stdio.wit")
    val streamsLocation = Location("streams.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli".toPackageName(),
        files = listOf(
          """
          |package wasi:cli;
          |interface stdin {
          |  use wasi:io/streams@0.2.12.{input-stream};
          |
          |  get-stdin: func() -> input-stream;
          |}
          """.trimMargin().toWitFile(stdioLocation),
        ),
      ),
      IoToplevelWitPackage(
        packageName = "wasi:io@0.2.12".toPackageName(),
        files = listOf(
          """
          |package wasi:io@0.2.12;
          |
          |interface streams {
          |    resource input-stream {
          |        read: func(len: u64) -> result;
          |    }
          |}
          """.trimMargin().toWitFile(streamsLocation),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        serviceName = "wasi:cli/stdin",
        typeName = IoTypeName.Declared("input-stream"),
      ),
    ).isEqualTo(
      TypeNameDeclared(
        serviceName = "wasi:io/streams@0.2.12",
        typeName = "input-stream",
      ),
    )
  }

  @Test
  fun `find symbols across inline packages with use`() {
    val stdioLocation = Location("stdio.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli".toPackageName(),
        files = listOf(
          """
          |package wasi:cli;
          |
          |interface stdin {
          |  use wasi:io/streams@0.2.12.{input-stream};
          |
          |  get-stdin: func() -> input-stream;
          |}
          |
          |package wasi:io@0.2.12 {
          |  interface streams {
          |    resource input-stream {
          |        read: func(len: u64) -> result;
          |    }
          |  }
          |}
          """.trimMargin().toWitFile(stdioLocation),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        serviceName = "wasi:cli/stdin",
        typeName = IoTypeName.Declared("input-stream"),
      ),
    ).isEqualTo(
      TypeNameDeclared(
        serviceName = "wasi:io/streams@0.2.12",
        typeName = "input-stream",
      ),
    )
  }


  @Test
  fun `find symbols across services with use`() {
    val stdioLocation = Location("stdio.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli".toPackageName(),
        files = listOf(
          """
          |package wasi:cli;
          |
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
          """.trimMargin().toWitFile(stdioLocation),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        serviceName = "wasi:cli/stdin",
        typeName = IoTypeName.Declared("input-stream"),
      ),
    ).isEqualTo(
      TypeNameDeclared(
        serviceName = "wasi:cli/streams",
        typeName = "input-stream",
      ),
    )
  }

  @Test
  fun `imports across packages`() {
    val commandLocation = Location("command.wit")
    val importsLocation = Location("imports.wit")
    val worldLocation = Location("world.wit")
    val monotonicClockLocation = Location("monotonic-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli@0.3.0".toPackageName(),
        files = listOf(
          """
          |package wasi:cli@0.3.0;
          |
          |world command {
          |  include imports;
          |}
          """.trimMargin().toWitFile(commandLocation),
          """
          |package wasi:cli@0.3.0;
          |
          |world imports {
          |  include wasi:clocks/imports@0.3.0;
          |}
          """.trimMargin().toWitFile(importsLocation),
        ),
      ),
      IoToplevelWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        files = listOf(
          """
          |package wasi:clocks@0.3.0;
          |
          |world imports {
          |  import monotonic-clock;
          |}
          """.trimMargin().toWitFile(worldLocation),
          """
          |package wasi:clocks@0.3.0;
          |
          |interface monotonic-clock {
          |  now: func() -> s64;
          |}
          """.trimMargin().toWitFile(monotonicClockLocation),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)
    val irPackages = irMapper.map()

    assertThat(irPackages).containsExactly(
      IrWitPackage(
        packageName = "wasi:cli@0.3.0".toPackageName(),
        services = listOf(
          IrWorld(
            location = commandLocation.at(3, 1),
            serviceName = "wasi:cli/command@0.3.0",
            imports = listOf(
              IrExternalApi(
                location = worldLocation.at(4, 3),
                serviceName = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
              ),
            ),
          ),
          IrWorld(
            location = importsLocation.at(3, 1),
            serviceName = "wasi:cli/imports@0.3.0",
            imports = listOf(
              IrExternalApi(
                location = worldLocation.at(4, 3),
                serviceName = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
              ),
            ),
          ),
        ),
      ),
      IrWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        services = listOf(
          IrWorld(
            location = worldLocation.at(3, 1),
            serviceName = "wasi:clocks/imports@0.3.0",
            imports = listOf(
              IrExternalApi(
                location = worldLocation.at(4, 3),
                serviceName = "wasi:clocks/monotonic-clock@0.3.0".toServiceName(),
              ),
            ),
          ),
          IrInterface(
            location = monotonicClockLocation.at(3, 1),
            serviceName = "wasi:clocks/monotonic-clock@0.3.0",
            items = listOf(
              IrFunction(
                location = monotonicClockLocation.at(4, 3),
                name = "now",
                returnType = TypeName.S64,
                functionName = FunctionNameInterface(
                  serviceName = "wasi:clocks/monotonic-clock@0.3.0",
                  name = "now",
                ),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `imports across inline packages`() {
    val commandLocation = Location("command.wit")
    val importsLocation = Location("imports.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:cli@0.3.0".toPackageName(),
        files = listOf(
          """
          |package wasi:cli@0.3.0;
          |
          |world command {
          |  include imports;
          |}
          """.trimMargin().toWitFile(commandLocation),
          """
          |package wasi:cli@0.3.0;
          |
          |world imports {
          |  include wasi:clocks/imports@0.3.0;
          |}
          |
          |package wasi:clocks@0.3.0 {
          |  world imports {
          |    import monotonic-clock;
          |  }
          |  interface monotonic-clock {
          |    now: func() -> s64;
          |  }
          |}
          """.trimMargin().toWitFile(importsLocation),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)
    val irPackages = irMapper.map()

    assertThat(irPackages).containsExactly(
      IrWitPackage(
        packageName = "wasi:cli@0.3.0".toPackageName(),
        services = listOf(
          IrWorld(
            location = commandLocation.at(3, 1),
            serviceName = "wasi:cli/command@0.3.0",
            imports = listOf(
              IrExternalApi(
                location = importsLocation.at(9, 5),
                serviceName = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
              ),
            ),
          ),
          IrWorld(
            location = importsLocation.at(3, 1),
            serviceName = "wasi:cli/imports@0.3.0",
            imports = listOf(
              IrExternalApi(
                location = importsLocation.at(9, 5),
                serviceName = ServiceName("wasi:clocks@0.3.0", "monotonic-clock"),
              ),
            ),
          ),
        ),
      ),
      IrWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        services = listOf(
          IrWorld(
            location = importsLocation.at(8, 3),
            serviceName = "wasi:clocks/imports@0.3.0",
            imports = listOf(
              IrExternalApi(
                location = importsLocation.at(9, 5),
                serviceName = "wasi:clocks/monotonic-clock@0.3.0".toServiceName(),
              ),
            ),
          ),
          IrInterface(
            location = importsLocation.at(11, 3),
            serviceName = "wasi:clocks/monotonic-clock@0.3.0",
            items = listOf(
              IrFunction(
                location = importsLocation.at(12, 5),
                name = "now",
                returnType = TypeName.S64,
                functionName = FunctionNameInterface(
                  serviceName = "wasi:clocks/monotonic-clock@0.3.0",
                  name = "now",
                ),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `inline interface is flattened`() {
    val location = Location("world.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "local:demo".toPackageName(),
        files = listOf(
          """
          |package local:demo;
          |
          |world your-world {
          |    import out-of-line: interface {
          |        the-function: func();
          |    }
          |}
          """.trimMargin().toWitFile(location),
        ),
      ),
    )
    val irMapper = IrMapper(ioPackages)
    val irPackages = irMapper.map()

    assertThat(irPackages).containsExactly(
      IrWitPackage(
        packageName = "local:demo".toPackageName(),
        services = listOf(
          IrInterface(
            location = location.at(4, 5),
            serviceName = "local:demo/out-of-line",
            items = listOf(
              IrFunction(
                location = location.at(5, 9),
                name = "the-function",
                functionName = FunctionNameInterface(
                  serviceName = "local:demo/out-of-line",
                  name = "the-function",
                ),
              ),
            ),
          ),
          IrWorld(
            location = location.at(3, 1),
            serviceName = "local:demo/your-world",
            imports = listOf(
              IrExternalApi(
                location = location.at(4, 5),
                packageName = "local:demo",
                serviceName = "out-of-line",
                plainName = "out-of-line",
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `find symbols in same package with use`() {
    val timezoneLocation = Location("timezone.wit")
    val wallClockLocation = Location("wall-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:clocks@0.2.12".toPackageName(),
        files = listOf(
          """
          |interface timezone {
          |    use wall-clock.{datetime};
          |
          |    display: func(when: datetime);
          |}
          """.trimMargin().toWitFile(timezoneLocation),
          """
          |package wasi:io@0.2.12;
          |
          |interface wall-clock {
          |    record datetime {
          |        seconds: u64,
          |        nanoseconds: u32,
          |    }
          |}
          """.trimMargin().toWitFile(wallClockLocation),
        ),
      ),
    )

    val irMapper = IrMapper(ioPackages)

    assertThat(
      irMapper.getType(
        serviceName = "wasi:clocks/timezone@0.2.12",
        typeName = IoTypeName.Declared("datetime"),
      ),
    ).isEqualTo(
      TypeNameDeclared(
        serviceName = "wasi:clocks/wall-clock@0.2.12",
        typeName = "datetime",
      ),
    )
  }

  @Test
  fun `get world`() {
    val worldLocation = Location("world.wit")
    val commandLocation = Location("command.wit")
    val wasiCli = IoToplevelWitPackage(
      packageName = "wasi:cli@0.2.12".toPackageName(),
      files = listOf(
        """
        |package wasi:cli@0.2.12;
        |
        |world command {
        |}
        """.trimMargin().toWitFile(commandLocation),
      ),
    )
    val wasiIo = IoToplevelWitPackage(
      packageName = "wasi:io@0.2.12".toPackageName(),
      files = listOf(
        """
        |package wasi:io@0.2.12;
        |
        |world imports {
        |}
        """.trimMargin().toWitFile(worldLocation),
      ),
    )

    val irMapper = IrMapper(listOf(wasiCli, wasiIo))

    assertThat(irMapper.getWorldOrNull("wasi:io/imports@0.2.12".toUsePath()))
      .isEqualTo(wasiIo.files.single().items.single())

    assertThat(irMapper.getWorldOrNull("wasi:cli/command@0.2.12".toUsePath()))
      .isEqualTo(wasiCli.files.single().items.single())

    assertThat(irMapper.getWorldOrNull("wasi:cli/command".toUsePath())).isNull()
  }

  @Test
  fun `interface function abi names`() {
    val systemClockLocation = Location("system-clock.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:clocks@0.3.0".toPackageName(),
        files = listOf(
          """
          |package wasi:clocks@0.3.0;
          |
          |interface system-clock {
          |  now: func() -> u64;
          |}
          """.trimMargin().toWitFile(systemClockLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irInterface = irPackage.services.single() as IrInterface
    val irFunction = irInterface.items.single() as IrFunction
    assertThat(irFunction.functionName).isEqualTo(
      FunctionNameInterface(
        serviceName = "wasi:clocks/system-clock@0.3.0",
        name = "now",
      ),
    )
    assertThat(irFunction.functionName.abiName).isEqualTo("now")
  }

  @Test
  fun `resource function abi names`() {
    val typesLocation = Location("types.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "wasi:http@0.3.0".toPackageName(),
        files = listOf(
          """
          |package wasi:http@0.3.0;
          |
          |interface types {
          |  resource fields {
          |    constructor();
          |    from-list: static func(entries: list<tuple<string,list<u8>>>) -> fields;
          |    has: func(name: string) -> bool;
          |    clone: func() -> fields;
          |  }
          |}
          """.trimMargin().toWitFile(typesLocation),
        ),
      ),
    )

    val irPackage = IrMapper(ioPackages).map().single()
    val irInterface = irPackage.services.single() as IrInterface
    val irResource = irInterface.items.single() as IrResource
    assertThat(irResource.functions.map { it.functionName }).containsExactly(
      FunctionNameConstructor(
        serviceName = "wasi:http/types@0.3.0",
        name = "fields",
      ),
      FunctionNameStatic(
        serviceName = "wasi:http/types@0.3.0",
        name = "from-list",
        resourceName = "fields",
      ),
      FunctionNameMethod(
        serviceName = "wasi:http/types@0.3.0",
        name = "has",
        resourceName = "fields",
      ),
      FunctionNameMethod(
        serviceName = "wasi:http/types@0.3.0",
        name = "clone",
        resourceName = "fields",
      ),
      FunctionNameResourceDrop(
        serviceName = "wasi:http/types@0.3.0",
        resourceName = "fields",
      ),
    )
  }

  @Test
  fun `resolve all type codecs`() {
    val typesLocation = Location("types.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "test:types".toPackageName(),
        files = listOf(
          """
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
          """.trimMargin().toWitFile(typesLocation),
        ),
      ),
    )

    val irMapper = IrMapper(ioPackages)
    val packages = irMapper.map()
    val location = Location("types.wit")

    val serviceName = "test:types/all-types"
    assertThat(packages).containsExactly(
      IrWitPackage(
        packageName = "test:types".toPackageName(),
        services = listOf(
          IrWorld(
            serviceName = "test:types/all-types",
            location = location.at(3, 1),
            types = listOf(
              IrTypeAlias(
                serviceName = serviceName,
                name = "my-alias",
                location = location.at(4, 5),
                target = TypeName.Tuple(
                  types = listOf(
                    TypeNameDeclared(
                      serviceName = serviceName,
                      typeName = "my-resource",
                    ),
                    TypeName.List(
                      TypeNameDeclared(
                        serviceName = serviceName,
                        typeName = "my-enum",
                      ),
                    ),
                  ),
                ),
              ),
              IrRecord(
                serviceName = serviceName,
                name = "my-record",
                location = location.at(5, 5),
                fields = listOf(
                  IrField(
                    location = location.at(6, 9),
                    name = "field",
                    type = TypeName.U64,
                  ),
                ),
              ),
              IrEnum(
                serviceName = serviceName,
                name = "my-enum",
                location = location.at(8, 5),
                cases = listOf(
                  IrCase(
                    location = location.at(9, 9),
                    name = "red",
                  ),
                  IrCase(
                    location = location.at(10, 9),
                    name = "blue",
                  ),
                ),
              ),
              IrFlags(
                serviceName = serviceName,
                name = "my-flags",
                location = location.at(12, 5),
                flags = listOf(
                  IrFlag(
                    location = location.at(13, 9),
                    name = "loaded",
                  ),
                  IrFlag(
                    location = location.at(14, 9),
                    name = "enabled",
                  ),
                ),
              ),
              IrResource(
                serviceName = serviceName,
                name = "my-resource",
                location = location.at(16, 5),
                functions = listOf(
                  IrFunction(
                    location = location.at(17, 9),
                    name = "write",
                    parameters = listOf(
                      IrParameter(
                        location = location.at(17, 21),
                        name = "bytes",
                        type = TypeName.List(TypeName.U8),
                      ),
                    ),
                    functionName = FunctionNameMethod(
                      serviceName = "test:types/all-types",
                      name = "write",
                      resourceName = "my-resource",
                    ),
                  ),
                  IrFunction(
                    location = location.at(16, 5),
                    name = "close",
                    functionName = FunctionNameResourceDrop(
                      serviceName = "test:types/all-types",
                      resourceName = "my-resource",
                    ),
                  ),
                ),
              ),
              IrVariant(
                serviceName = serviceName,
                name = "my-variant",
                location = location.at(19, 5),
                cases = listOf(
                  IrCase(
                    location = location.at(20, 9),
                    name = "none",
                  ),
                  IrCase(
                    location = location.at(21, 9),
                    name = "some",
                    type = TypeName.List(
                      TypeNameDeclared(
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
    serviceName: String,
    typeName: IoTypeName,
  ): TypeName {
    context(IrMapper.Context(serviceName.toServiceName())) {
      return typeName.typeNameToIr()
    }
  }
}
