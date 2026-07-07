package dev.wasmo.brevity.ir

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Offset
import dev.wasmo.brevity.io.IoExternalApi
import dev.wasmo.brevity.io.IoFunction
import dev.wasmo.brevity.io.IoInclude
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.io.toWitFile
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test
import okio.Path.Companion.toPath

class WorldFlattenerTest {
  /**
   * Note that imports and exports are stripped unless the target interfaces declare at least one
   * function.
   */
  @Test
  fun `include relative path`() {
    val command = IoWorld(
      name = "command",
      items = listOf(IoInclude(path = "imports")),
      exports = listOf(IoExternalApi(path = "run")),
    )

    val imports = IoWorld(
      name = "imports",
      imports = listOf(IoExternalApi(path = "exit")),
    )

    val run = IoInterface(
      name = "run",
      items = listOf(
        IoFunction(
          name = "run",
        ),
      ),
    )

    val exit = IoInterface(
      name = "exit",
      items = listOf(
        IoFunction(
          name = "exit",
        ),
      ),
    )

    val wasiCommand = IoToplevelWitPackage(
      packageName = "wasi:cli@0.3.0".toPackageName(),
      files = mapOf(
        "command.wit".toPath() to IoWitFile(
          items = listOf(command),
        ),
        "exit.wit".toPath() to IoWitFile(
          items = listOf(exit),
        ),
        "imports.wit".toPath() to IoWitFile(
          items = listOf(imports),
        ),
        "run.wit".toPath() to IoWitFile(
          items = listOf(run),
        ),
      ),
    )
    val irMapper = IrMapper(listOf(wasiCommand))
    val mapped = irMapper.map()

    assertThat(
      mapped.single().services.single { (it as? IrWorld)?.serviceName?.name == Identifier("command") },
    ).isEqualTo(
      IrWorld(
        serviceName = "wasi:cli/command@0.3.0",
        exports = listOf(
          IrExternalApi(
            packageName = "wasi:cli@0.3.0",
            serviceName = "run",
          ),
        ),
        imports = listOf(
          IrExternalApi(
            packageName = "wasi:cli@0.3.0",
            serviceName = "exit",
          ),
        ),
      ),
    )
  }

  /**
   * It's unclear what the expected behavior should be when a world includes another world: should
   * the items retain their original package names, or should those be replaced with the including
   * world's names?
   *
   * We currently implement the former behavior; this test validates that.
   *
   * https://github.com/bytecodealliance/wit-bindgen/issues/1647
   */
  @Test
  fun `included types are not mapped`() {
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "test:subject".toPackageName(),
        files = mapOf(
          "subject/world.wit".toPath() to """
            |package test:subject;
            |
            |world subject-world {
            |    type source-alias = tuple<my-record, my-enum>;
            |    record my-record {
            |        field: my-flags,
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
            |        write: func(variants: my-variant) -> my-flags;
            |    }
            |    variant my-variant {
            |        none,
            |        some(my-resource),
            |    }
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
      IoToplevelWitPackage(
        packageName = "test:exported".toPackageName(),
        files = mapOf(
          "exported/world.wit".toPath() to """
            |package test:exported;
            |
            |world exported-world {
            |    include test:subject/subject-world;
            |}
            """.trimMargin().toWitFile(),
        ),
      ),
    )

    val irMapper = IrMapper(ioPackages)
    val mapped = irMapper.map()

    assertThat(mapped.single { it.packageName == "test:exported".toPackageName() }).isEqualTo(
      IrWitPackage(
        packageName = "test:exported".toPackageName(),
        services = listOf(
          IrWorld(
            offset = Offset(3, 1),
            serviceName = "test:exported/exported-world",
            types = listOf(
              IrTypeAlias(
                offset = Offset(4, 5),
                serviceName = "test:subject/subject-world",
                name = "source-alias",
                target = IrTypeName.Tuple(
                  listOf(
                    IrTypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-record",
                    ),
                    IrTypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-enum",
                    ),
                  ),
                ),
              ),
              IrRecord(
                offset = Offset(5, 5),
                serviceName = "test:subject/subject-world",
                name = "my-record",
                fields = listOf(
                  IrField(
                    offset = Offset(6, 9),
                    name = "field",
                    type = IrTypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-flags",
                    ),
                  ),
                ),
              ),
              IrEnum(
                offset = Offset(8, 5),
                serviceName = "test:subject/subject-world",
                name = "my-enum",
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
                offset = Offset(12, 5),
                serviceName = "test:subject/subject-world",
                name = "my-flags",
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
                offset = Offset(16, 5),
                serviceName = "test:subject/subject-world",
                name = "my-resource",
                functions = listOf(
                  IrFunction(
                    offset = Offset(17, 9),
                    name = "write",
                    serviceName = "test:subject/subject-world",
                    resourceName = "my-resource",
                    parameters = listOf(
                      IrParameter(
                        offset = Offset(17, 21),
                        name = "variants",
                        type = IrTypeNameDeclared(
                          serviceName = "test:subject/subject-world",
                          typeName = "my-variant",
                        ),
                      ),
                    ),
                    returnType = IrTypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-flags",
                    ),
                  ),
                ),
              ),
              IrVariant(
                offset = Offset(19, 5),
                serviceName = "test:subject/subject-world",
                name = "my-variant",
                cases = listOf(
                  IrCase(
                    offset = Offset(20, 9),
                    name = "none",
                  ),
                  IrCase(
                    offset = Offset(21, 9),
                    name = "some",
                    type = IrTypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-resource",
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
}
