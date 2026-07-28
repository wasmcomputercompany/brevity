package dev.wasmo.brevity.ir

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.FunctionNameMethod
import dev.wasmo.brevity.FunctionNameResourceDrop
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.TypeName
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

    val commandLocation = Location("command.wit")
    val exitLocation = Location("exit.wit")
    val importsLocation = Location("imports.wit")
    val runLocation = Location("run.wit")

    val wasiCommand = IoToplevelWitPackage(
      packageName = "wasi:cli@0.3.0".toPackageName(),
      files = listOf(
        IoWitFile(
          packageName = "wasi:cli@0.3.0".toPackageName(),
          items = listOf(command),
          location = commandLocation,
        ),
        IoWitFile(
          items = listOf(exit),
          location = exitLocation,
        ),
        IoWitFile(
          items = listOf(imports),
          location = importsLocation,
        ),
        IoWitFile(
          items = listOf(run),
          location = runLocation,
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
    val subjectLocation = Location("subject/world.wit")
    val exportedLocation = Location("exported/world.wit")
    val ioPackages = listOf(
      IoToplevelWitPackage(
        packageName = "test:subject".toPackageName(),
        files = listOf(
          """
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
          """.trimMargin().toWitFile(subjectLocation),
        ),
      ),
      IoToplevelWitPackage(
        packageName = "test:exported".toPackageName(),
        files = listOf(
          """
          |package test:exported;
          |
          |world exported-world {
          |    include test:subject/subject-world;
          |}
          """.trimMargin().toWitFile(exportedLocation),
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
            location = exportedLocation.at(3, 1),
            serviceName = "test:exported/exported-world",
            types = listOf(
              IrTypeAlias(
                location = subjectLocation.at(4, 5),
                serviceName = "test:subject/subject-world",
                name = "source-alias",
                target = TypeName.Tuple(
                  listOf(
                    TypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-record",
                    ),
                    TypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-enum",
                    ),
                  ),
                ),
              ),
              IrRecord(
                location = subjectLocation.at(5, 5),
                serviceName = "test:subject/subject-world",
                name = "my-record",
                fields = listOf(
                  IrField(
                    location = subjectLocation.at(6, 9),
                    name = "field",
                    type = TypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-flags",
                    ),
                  ),
                ),
              ),
              IrEnum(
                location = subjectLocation.at(8, 5),
                serviceName = "test:subject/subject-world",
                name = "my-enum",
                cases = listOf(
                  IrCase(
                    location = subjectLocation.at(9, 9),
                    name = "red",
                  ),
                  IrCase(
                    location = subjectLocation.at(10, 9),
                    name = "blue",
                  ),
                ),
              ),
              IrFlags(
                location = subjectLocation.at(12, 5),
                serviceName = "test:subject/subject-world",
                name = "my-flags",
                flags = listOf(
                  IrFlag(
                    location = subjectLocation.at(13, 9),
                    name = "loaded",
                  ),
                  IrFlag(
                    location = subjectLocation.at(14, 9),
                    name = "enabled",
                  ),
                ),
              ),
              IrResource(
                location = subjectLocation.at(16, 5),
                serviceName = "test:subject/subject-world",
                name = "my-resource",
                functions = listOf(
                  IrFunction(
                    location = subjectLocation.at(17, 9),
                    name = "write",
                    functionName = FunctionNameMethod(
                      serviceName = "test:subject/subject-world",
                      resourceName = "my-resource",
                      name = "write",
                    ),
                    parameters = listOf(
                      IrParameter(
                        location = subjectLocation.at(17, 21),
                        name = "variants",
                        type = TypeNameDeclared(
                          serviceName = "test:subject/subject-world",
                          typeName = "my-variant",
                        ),
                      ),
                    ),
                    returnType = TypeNameDeclared(
                      serviceName = "test:subject/subject-world",
                      typeName = "my-flags",
                    ),
                  ),
                  IrFunction(
                    location = subjectLocation.at(16, 5),
                    name = "close",
                    functionName = FunctionNameResourceDrop(
                      serviceName = "test:subject/subject-world",
                      resourceName = "my-resource",
                    ),
                  ),
                ),
              ),
              IrVariant(
                location = subjectLocation.at(19, 5),
                serviceName = "test:subject/subject-world",
                name = "my-variant",
                cases = listOf(
                  IrCase(
                    location = subjectLocation.at(20, 9),
                    name = "none",
                  ),
                  IrCase(
                    location = subjectLocation.at(21, 9),
                    name = "some",
                    type = TypeNameDeclared(
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
