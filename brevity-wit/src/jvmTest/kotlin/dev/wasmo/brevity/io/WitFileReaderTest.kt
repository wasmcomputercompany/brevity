package dev.wasmo.brevity.io

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.wasmo.brevity.Documentation
import dev.wasmo.brevity.Gate
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Identifier.Companion.Identifier
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.WitException
import dev.wasmo.brevity.collectNoIssuesOrThrow
import dev.wasmo.brevity.toPackageName
import kotlin.test.Test
import kotlin.test.assertFailsWith

class WitFileReaderTest {
  private val location = Location("file.wit")

  @Test
  fun packageOnly() = collectNoIssuesOrThrow {
    val wit = """
      |package wasi:clocks@0.2.9;
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        packageName = IoPackageNameElement("wasi:clocks@0.2.9".toPackageName(), location.at(1, 1)),
        location = location,
      ),
    )
  }

  @Test
  fun `multiple packages`() {
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        """
        |package wasi:clocks@0.2.9;
        |package wasi:clocks;
        """.trimMargin().toWitFile(location)
      }
    }
    assertThat(e.issue.description).isEqualTo("unexpected package identifier")
  }

  @Test
  fun `package after another declaration`() {
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        """
        |interface foo {}
        |package wasi:clocks;
        """.trimMargin().toWitFile(location)
      }
    }
    assertThat(e.issue.description).isEqualTo("unexpected package identifier")
  }

  @Test
  fun `readGate success`() = collectNoIssuesOrThrow {
    val wit = """
      |@since(version = 0.2.0)
      |@deprecated(version = 0.2.2)
      |@unstable(feature = fancier-foo)
      |interface foo {}
      """.trimMargin()
    val gate = WitFileReader(location, wit).readGateOrNull()
    assertThat(gate).isEqualTo(
      Gate(
        unstable = "fancier-foo",
        since = "0.2.0",
        deprecated = "0.2.2",
      ),
    )
  }

  @Test
  fun `readGate unstable only`() = collectNoIssuesOrThrow {
    val wit = """
      |@unstable(feature = fancier-foo)
      |interface foo {}
      """.trimMargin()
    val gate = WitFileReader(location, wit).readGateOrNull()
    assertThat(gate).isEqualTo(Gate(unstable = "fancier-foo"))
  }

  @Test
  fun `readGate since only`() = collectNoIssuesOrThrow {
    val wit = """
      |@since(version = 0.2.0)
      |interface foo {}
      """.trimMargin()
    val gate = WitFileReader(location, wit).readGateOrNull()
    assertThat(gate).isEqualTo(Gate(since = "0.2.0"))
  }

  @Test
  fun `readGate deprecated only`() = collectNoIssuesOrThrow {
    val wit = """
      |@deprecated(version = 0.2.2)
      |interface foo {}
      """.trimMargin()
    val gate = WitFileReader(location, wit).readGateOrNull()
    assertThat(gate).isEqualTo(Gate(deprecated = "0.2.2"))
  }

  @Test
  fun `readGate unexpected field`() {
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        WitFileReader(
          location,
          "@unstable(version = 0.2.2)",
        ).readGateOrNull()
      }
    }
    assertThat(e.issue.description).isEqualTo("unexpected field: unstable.version")
  }

  @Test
  fun `readGate repeated unstable`() {
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        WitFileReader(
          location,
          "@unstable(feature = fancier-foo) @unstable(feature = faster-foo)",
        ).readGateOrNull()
      }
    }
    assertThat(e.issue.description).isEqualTo("unexpected field: unstable.feature")
  }

  @Test
  fun `readGate repeated since`() {
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        WitFileReader(
          location,
          "@since(version = 0.2.0) @since(version = 0.3.0)",
        ).readGateOrNull()
      }
    }
    assertThat(e.issue.description).isEqualTo("unexpected field: since.version")
  }

  @Test
  fun `readGate repeated deprecated`() {
    val e = assertFailsWith<WitException> {
      collectNoIssuesOrThrow {
        WitFileReader(
          location,
          "@deprecated(version = 0.2.0) @deprecated(version = 0.3.0)",
        ).readGateOrNull()
      }
    }
    assertThat(e.issue.description).isEqualTo("unexpected field: deprecated.version")
  }

  @Test
  fun `readGate absent`() = collectNoIssuesOrThrow {
    assertThat(
      WitFileReader(
        location,
        "interface foo {}",
      ).readGateOrNull(),
    ).isNull()
  }

  @Test
  fun `readInterface success`() = collectNoIssuesOrThrow {
    val wit = """
      |interface foo {}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            name = "foo",
            location = location.at(1, 1),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `readInterface with documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |/// this is the foo interface
      |@deprecated(version = 0.2.2)
      |/**it is a good interface*/
      |interface foo {}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            documentation = """
              | this is the foo interface
              |it is a good interface
              """.trimMargin(),
            gate = Gate(deprecated = "0.2.2"),
            location = location.at(4, 1),
            name = "foo",
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `readInterface with functions`() = collectNoIssuesOrThrow {
    val wit = """
      |interface foo {
      |  print: func(message: string, repeat: option<u32>) -> result<_, errno>;
      |  async-print: async func();
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "foo",
            items = listOf(
              IoFunction(
                location = location.at(2, 3),
                name = "print",
                parameters = listOf(
                  IoParameter(
                    location = location.at(2, 15),
                    name = "message",
                    type = IoTypeName.String,
                  ),
                  IoParameter(
                    location = location.at(2, 32),
                    name = "repeat",
                    type = IoTypeName.Option(IoTypeName.U32),
                  ),
                ),
                returnType = IoTypeName.Result(
                  error = IoTypeName.Declared("errno"),
                ),
              ),
              IoFunction(
                location = location.at(3, 3),
                name = "async-print",
                async = true,
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `read sample interface`() = collectNoIssuesOrThrow {
    val wit = """
      |package wasi:clocks@0.2.9;
      |
      |interface wall-clock {
      |  record datetime {
      |    seconds: u64,
      |    nanoseconds: u32,
      |  }
      |
      |  now: func() -> datetime;
      |
      |  resolution: func() -> datetime;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        packageName = IoPackageNameElement("wasi:clocks@0.2.9".toPackageName(), location.at(1, 1)),
        items = listOf(
          IoInterface(
            location = location.at(3, 1),
            name = "wall-clock",
            items = listOf(
              IoRecord(
                location = location.at(4, 3),
                name = "datetime",
                fields = listOf(
                  IoField(
                    location = location.at(5, 5),
                    name = "seconds",
                    type = IoTypeName.U64,
                  ),
                  IoField(
                    location = location.at(6, 5),
                    name = "nanoseconds",
                    type = IoTypeName.U32,
                  ),
                ),
              ),
              IoFunction(
                location = location.at(9, 3),
                name = "now",
                returnType = IoTypeName.Declared("datetime"),
              ),
              IoFunction(
                location = location.at(11, 3),
                name = "resolution",
                returnType = IoTypeName.Declared("datetime"),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `interface documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |/// tick tock
      |/// wall clock
      |@since(version = 1.0)
      |interface wall-clock {
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            documentation = """
              | tick tock
              | wall clock
              """.trimMargin(),
            gate = Gate(since = "1.0"),
            location = location.at(4, 1),
            name = "wall-clock",
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `record documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface wall-clock {
      |  /// spacetime
      |  @since(version = 2.0)
      |  record datetime {
      |    /// just a second
      |    @since(version = 3.0)
      |    seconds: u64,
      |    /// tick
      |    @since(version = 4.0)
      |    nanoseconds: u32,
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "wall-clock",
            items = listOf(
              IoRecord(
                documentation = " spacetime",
                gate = Gate(since = "2.0"),
                location = location.at(4, 3),
                name = "datetime",
                fields = listOf(
                  IoField(
                    documentation = " just a second",
                    gate = Gate(since = "3.0"),
                    location = location.at(7, 5),
                    name = "seconds",
                    type = IoTypeName.U64,
                  ),
                  IoField(
                    documentation = " tick",
                    gate = Gate(since = "4.0"),
                    location = location.at(10, 5),
                    name = "nanoseconds",
                    type = IoTypeName.U32,
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `function documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface wall-clock {
      |  /// sample the clock
      |  @since(version = 5.0)
      |  now: func(
      |    /// True to return a non-decreasing value.
      |    monotonic: bool,
      |  ) -> datetime;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "wall-clock",
            items = listOf(
              IoFunction(
                documentation = " sample the clock",
                gate = Gate(since = "5.0"),
                location = location.at(4, 3),
                name = "now",
                parameters = listOf(
                  IoParameter(
                    documentation = " True to return a non-decreasing value.",
                    location = location.at(6, 5),
                    name = "monotonic",
                    type = IoTypeName.Bool,
                  ),
                ),
                returnType = IoTypeName.Declared("datetime"),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `function parameters trailing commas`() = collectNoIssuesOrThrow {
    val wit = """
      |interface monotonic-clock {
      |  subscribe-instant: func(
      |    when: instant,
      |  ) -> pollable;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "monotonic-clock",
            items = listOf(
              IoFunction(
                location = location.at(2, 3),
                name = "subscribe-instant",
                parameters = listOf(
                  IoParameter(
                    location = location.at(3, 5),
                    name = "when",
                    type = IoTypeName.Declared("instant"),
                  ),
                ),
                returnType = IoTypeName.Declared("pollable"),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `resource documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  /// big boi
      |  @since(version = 1.0)
      |  resource blob {
      |    /// makes a new one
      |    @since(version = 2.0)
      |    constructor(init: list<u8>);
      |
      |    /// puts some bytes
      |    @since(version = 3.0)
      |    write: func(bytes: list<u8>);
      |
      |    /// gets some bytes
      |    @since(version = 4.0)
      |    read: func(n: u32) -> list<u8>;
      |
      |    /// smashes some blobs together
      |    @since(version = 5.0)
      |    merge: static func(lhs: borrow<blob>, rhs: borrow<blob>) -> blob;
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoResource(
                documentation = " big boi",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "blob",
                functions = listOf(
                  IoFunction(
                    documentation = " makes a new one",
                    gate = Gate(since = "2.0"),
                    location = location.at(7, 5),
                    constructor = true,
                    name = "constructor",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(7, 17),
                        name = "init",
                        type = IoTypeName.List(IoTypeName.U8),
                      ),
                    ),
                  ),
                  IoFunction(
                    documentation = " puts some bytes",
                    gate = Gate(since = "3.0"),
                    location = location.at(11, 5),
                    name = "write",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(11, 17),
                        name = "bytes",
                        type = IoTypeName.List(IoTypeName.U8),
                      ),
                    ),
                  ),
                  IoFunction(
                    documentation = " gets some bytes",
                    gate = Gate(since = "4.0"),
                    location = location.at(15, 5),
                    name = "read",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(15, 16),
                        name = "n",
                        type = IoTypeName.U32,
                      ),
                    ),
                    returnType = IoTypeName.List(IoTypeName.U8),
                  ),
                  IoFunction(
                    documentation = " smashes some blobs together",
                    gate = Gate(since = "5.0"),
                    location = location.at(19, 5),
                    static = true,
                    name = "merge",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(19, 24),
                        name = "lhs",
                        type = IoTypeName.Borrow(IoTypeName.Declared("blob")),
                      ),
                      IoParameter(
                        location = location.at(19, 43),
                        name = "rhs",
                        type = IoTypeName.Borrow(IoTypeName.Declared("blob")),
                      ),
                    ),
                    returnType = IoTypeName.Declared("blob"),
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `empty resource`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  resource blob;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoResource(
                location = location.at(2, 3),
                name = "blob",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `variant documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  /// whats included
      |  @since(version = 1.0)
      |  variant filter {
      |    /// all the things
      |    @since(version = 2.0)
      |    all,
      |    /// zilch
      |    @since(version = 3.0)
      |    none,
      |    /// one
      |    @since(version = 4.0)
      |    some(list<string>),
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoVariant(
                documentation = " whats included",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "filter",
                cases = listOf(
                  IoCase(
                    documentation = " all the things",
                    gate = Gate(since = "2.0"),
                    location = location.at(7, 5),
                    name = "all",
                  ),
                  IoCase(
                    documentation = " zilch",
                    gate = Gate(since = "3.0"),
                    location = location.at(10, 5),
                    name = "none",
                  ),
                  IoCase(
                    documentation = " one",
                    gate = Gate(since = "4.0"),
                    location = location.at(13, 5),
                    name = "some",
                    type = IoTypeName.List(IoTypeName.String),
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `flags documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  /// comic character
      |  @since(version = 1.0)
      |  flags properties {
      |    /// plastic
      |    @since(version = 2.0)
      |    lego,
      |    /// avenger
      |    @since(version = 3.0)
      |    marvel-superhero,
      |    /// naughty
      |    @since(version = 4.0)
      |    supervillain,
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoFlags(
                documentation = " comic character",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "properties",
                flags = listOf(
                  IoFlag(
                    documentation = " plastic",
                    gate = Gate(since = "2.0"),
                    location = location.at(7, 5),
                    name = "lego",
                  ),
                  IoFlag(
                    documentation = " avenger",
                    gate = Gate(since = "3.0"),
                    location = location.at(10, 5),
                    name = "marvel-superhero",
                  ),
                  IoFlag(
                    documentation = " naughty",
                    gate = Gate(since = "4.0"),
                    location = location.at(13, 5),
                    name = "supervillain",
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `enum documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  /// Roy G.
      |  @since(version = 1.0)
      |  enum color {
      |    /// #ff0000
      |    @since(version = 2.0)
      |    red,
      |    /// #0000ff
      |    @since(version = 3.0)
      |    blue,
      |    /// #00ff00
      |    @since(version = 4.0)
      |    green,
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoEnum(
                documentation = " Roy G.",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "color",
                cases = listOf(
                  IoCase(
                    documentation = " #ff0000",
                    gate = Gate(since = "2.0"),
                    location = location.at(7, 5),
                    name = "red",
                  ),
                  IoCase(
                    documentation = " #0000ff",
                    gate = Gate(since = "3.0"),
                    location = location.at(10, 5),
                    name = "blue",
                  ),
                  IoCase(
                    documentation = " #00ff00",
                    gate = Gate(since = "4.0"),
                    location = location.at(13, 5),
                    name = "green",
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `type alias documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  /// So Awesome.
      |  @since(version = 1.0)
      |  type my-awesome-u32 = u32;
      |  /// So Complicated.
      |  @since(version = 2.0)
      |  type my-complicated-tuple = tuple<u32, s32, string>;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoTypeAlias(
                documentation = " So Awesome.",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "my-awesome-u32",
                target = IoTypeName.U32,
              ),
              IoTypeAlias(
                documentation = " So Complicated.",
                gate = Gate(since = "2.0"),
                location = location.at(7, 3),
                name = "my-complicated-tuple",
                target = IoTypeName.Tuple(
                  listOf(
                    IoTypeName.U32,
                    IoTypeName.S32,
                    IoTypeName.String,
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `use documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |interface db {
      |  /// Four values.
      |  @since(version = 1.0)
      |  use an-interface.{a, list, of, names};
      |  /// One aliased value.
      |  @since(version = 2.0)
      |  use my:dependency/the-interface@3.0.{
      |    /// we can document use items?!
      |    more,
      |    names as foo
      |  };
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInterface(
            location = location.at(1, 1),
            name = "db",
            items = listOf(
              IoUse(
                documentation = " Four values.",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                path = "an-interface",
                items = listOf(
                  IoUseItem(location = location.at(4, 21), type = "a"),
                  IoUseItem(location = location.at(4, 24), type = "list"),
                  IoUseItem(location = location.at(4, 30), type = "of"),
                  IoUseItem(location = location.at(4, 34), type = "names"),
                ),
              ),
              IoUse(
                documentation = " One aliased value.",
                gate = Gate(since = "2.0"),
                location = location.at(7, 3),
                path = "my:dependency/the-interface@3.0",
                items = listOf(
                  IoUseItem(
                    documentation = " we can document use items?!",
                    location = location.at(9, 5),
                    type = "more",
                  ),
                  IoUseItem(
                    location = location.at(10, 5),
                    type = "names",
                    alias = "foo",
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `world documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |/// a printer-scanner-fax thingy
      |@since(version = 1.0)
      |world multi-function-device {
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            documentation = " a printer-scanner-fax thingy",
            gate = Gate(since = "1.0"),
            location = location.at(3, 1),
            name = "multi-function-device",
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `import export use path documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// The component needs an `error-reporter`
      |  @since(version = 1.0)
      |  import error-reporter;
      |  /// This also exports an `error-creator`
      |  @since(version = 2.0)
      |  export error-creator;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            imports = listOf(
              IoExternalApi(
                documentation = " The component needs an `error-reporter`",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                path = "error-reporter",
              ),
            ),
            exports = listOf(
              IoExternalApi(
                documentation = " This also exports an `error-creator`",
                gate = Gate(since = "2.0"),
                location = location.at(7, 3),
                path = "error-creator",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `import plain named use path documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// This store is aliased as 'primary'
      |  @since(version = 1.0)
      |  import primary: wasi:keyvalue/store;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            imports = listOf(
              IoExternalApi(
                documentation = " This store is aliased as 'primary'",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                plainName = "primary",
                path = "wasi:keyvalue/store",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `export plain named use path documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// This store is aliased as 'secondary'
      |  @since(version = 2.0)
      |  export secondary: wasi:keyvalue/store;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            exports = listOf(
              IoExternalApi(
                documentation = " This store is aliased as 'secondary'",
                gate = Gate(since = "2.0"),
                location = location.at(4, 3),
                plainName = "secondary",
                path = "wasi:keyvalue/store",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `import inline interface documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// This interface is inline
      |  @since(version = 1.0)
      |  import host: interface {
      |    /// This function is in an inline interface
      |    @since(version = 2.0)
      |    log: func(param: string);
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            imports = listOf(
              IoInterface(
                documentation = " This interface is inline",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "host",
                items = listOf(
                  IoFunction(
                    documentation = " This function is in an inline interface",
                    gate = Gate(since = "2.0"),
                    location = location.at(7, 5),
                    name = "log",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(7, 15),
                        name = "param",
                        type = IoTypeName.String,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `export inline interface documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// We can export an inline interface
      |  @since(version = 3.0)
      |  export guest: interface {
      |    /// A function in an inline interface
      |    @since(version = 4.0)
      |    scan: func(document: string);
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            exports = listOf(
              IoInterface(
                documentation = " We can export an inline interface",
                gate = Gate(since = "3.0"),
                location = location.at(4, 3),
                name = "guest",
                items = listOf(
                  IoFunction(
                    documentation = " A function in an inline interface",
                    gate = Gate(since = "4.0"),
                    location = location.at(7, 5),
                    name = "scan",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(7, 16),
                        name = "document",
                        type = IoTypeName.String,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `import inline function documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// This function is inline
      |  @since(version = 4.0)
      |  import log: func(param: string);
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            imports = listOf(
              IoFunction(
                documentation = " This function is inline",
                gate = Gate(since = "4.0"),
                location = location.at(4, 3),
                name = "log",
                parameters = listOf(
                  IoParameter(
                    location = location.at(4, 20),
                    name = "param",
                    type = IoTypeName.String,
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `export inline function documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// This exported function is inline
      |  @since(version = 1.0)
      |  export scan: func(document: string);
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            exports = listOf(
              IoFunction(
                documentation = " This exported function is inline",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "scan",
                parameters = listOf(
                  IoParameter(
                    location = location.at(4, 21),
                    name = "document",
                    type = IoTypeName.String,
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `import plain named simple use path`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  import two: store;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            imports = listOf(
              IoExternalApi(
                location = location.at(2, 3),
                plainName = "two",
                path = "store",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `export plain named simple use path`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  export two: store;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            exports = listOf(
              IoExternalApi(
                location = location.at(2, 3),
                plainName = "two",
                path = "store",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `world include documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  /// This include is pretty basic.
      |  @since(version = 1.0)
      |  include my-world-2;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoInclude(
                documentation = " This include is pretty basic.",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                path = "my-world-2",
                items = listOf(),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `world include with items`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  include wasi:io/my-world-1 with {
      |    a as a1,
      |    /// we can document include items?!
      |    b as b1
      |  };
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoInclude(
                location = location.at(2, 3),
                path = "wasi:io/my-world-1",
                items = listOf(
                  IoIncludeItem(
                    location = location.at(3, 5),
                    type = "a",
                    alias = "a1",
                  ),
                  IoIncludeItem(
                    documentation = " we can document include items?!",
                    location = location.at(5, 5),
                    type = "b",
                    alias = "b1",
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `inline package documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |/// This package is pasted from somewhere else.
      |@since(version = 1.0)
      |package local:a {
      |  /// This interface is included in a package.
      |  @since(version = 2.0)
      |  interface foo {}
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInlinePackage(
            documentation = Documentation(" This package is pasted from somewhere else."),
            gate = Gate(since = "1.0"),
            location = location.at(3, 1),
            packageName = "local:a".toPackageName(),
            declarations = listOf(
              IoInterface(
                documentation = " This interface is included in a package.",
                gate = Gate(since = "2.0"),
                location = location.at(6, 3),
                name = "foo",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `top level use documentation and gates`() = collectNoIssuesOrThrow {
    val wit = """
      |/// Use the Wasi HTTP types.
      |@since(version = 1.0)
      |use wasi:http/types@1.0.0;
      |/// Use the Wasi HTTP handler also.
      |@since(version = 2.0)
      |use wasi:http/handler as http-handler;
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoTopLevelUse(
            documentation = Documentation(" Use the Wasi HTTP types."),
            gate = Gate(since = "1.0"),
            location = location.at(3, 1),
            path = "wasi:http/types@1.0.0".toUsePath(),
          ),
          IoTopLevelUse(
            documentation = Documentation(" Use the Wasi HTTP handler also."),
            gate = Gate(since = "2.0"),
            location = location.at(6, 1),
            path = "wasi:http/handler".toUsePath(),
            alias = Identifier("http-handler"),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `top level use in nested package`() = collectNoIssuesOrThrow {
    val wit = """
      |package local:a {
      |  /// Use the Wasi HTTP types.
      |  @since(version = 1.0)
      |  use wasi:http/types@1.0.0;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInlinePackage(
            location = location.at(1, 1),
            packageName = "local:a".toPackageName(),
            declarations = listOf(
              IoTopLevelUse(
                documentation = Documentation(" Use the Wasi HTTP types."),
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                path = "wasi:http/types@1.0.0".toUsePath(),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `world in nested package`() = collectNoIssuesOrThrow {
    val wit = """
      |package local:a {
      |  /// a printer-scanner-fax thingy
      |  @since(version = 1.0)
      |  world multi-function-device {
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoInlinePackage(
            location = location.at(1, 1),
            packageName = "local:a".toPackageName(),
            declarations = listOf(
              IoWorld(
                documentation = " a printer-scanner-fax thingy",
                gate = Gate(since = "1.0"),
                location = location.at(4, 3),
                name = "multi-function-device",
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `record in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  record datetime {
      |    seconds: u64,
      |    nanoseconds: u32,
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoRecord(
                location = location.at(2, 3),
                name = "datetime",
                fields = listOf(
                  IoField(
                    location = location.at(3, 5),
                    name = "seconds",
                    type = IoTypeName.U64,
                  ),
                  IoField(
                    location = location.at(4, 5),
                    name = "nanoseconds",
                    type = IoTypeName.U32,
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `enum in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  enum color {
      |    red,
      |    blue,
      |    green,
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoEnum(
                location = location.at(2, 3),
                name = "color",
                cases = listOf(
                  IoCase(
                    location = location.at(3, 5),
                    name = "red",
                  ),
                  IoCase(
                    location = location.at(4, 5),
                    name = "blue",
                  ),
                  IoCase(
                    location = location.at(5, 5),
                    name = "green",
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `flags in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  flags properties {
      |    lego,
      |    marvel-superhero,
      |    supervillain,
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoFlags(
                location = location.at(2, 3),
                name = "properties",
                flags = listOf(
                  IoFlag(
                    location = location.at(3, 5),
                    name = "lego",
                  ),
                  IoFlag(
                    location = location.at(4, 5),
                    name = "marvel-superhero",
                  ),
                  IoFlag(
                    location = location.at(5, 5),
                    name = "supervillain",
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `resource in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  resource blob {
      |    constructor(init: list<u8>);
      |    write: func(bytes: list<u8>);
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoResource(
                location = location.at(2, 3),
                name = "blob",
                functions = listOf(
                  IoFunction(
                    location = location.at(3, 5),
                    constructor = true,
                    name = "constructor",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(3, 17),
                        name = "init",
                        type = IoTypeName.List(IoTypeName.U8),
                      ),
                    ),
                  ),
                  IoFunction(
                    location = location.at(4, 5),
                    name = "write",
                    parameters = listOf(
                      IoParameter(
                        location = location.at(4, 17),
                        name = "bytes",
                        type = IoTypeName.List(IoTypeName.U8),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `type alias in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  type my-awesome-u32 = u32;
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoTypeAlias(
                location = location.at(2, 3),
                name = "my-awesome-u32",
                target = IoTypeName.U32,
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `use in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  use an-interface.{a, list, of, names};
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoUse(
                location = location.at(2, 3),
                path = "an-interface",
                items = listOf(
                  IoUseItem(location = location.at(2, 21), type = "a"),
                  IoUseItem(location = location.at(2, 24), type = "list"),
                  IoUseItem(location = location.at(2, 30), type = "of"),
                  IoUseItem(location = location.at(2, 34), type = "names"),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }

  @Test
  fun `variant in world`() = collectNoIssuesOrThrow {
    val wit = """
      |world multi-function-device {
      |  variant filter {
      |    all,
      |    none,
      |    some(list<string>),
      |  }
      |}
      """.trimMargin().toWitFile(location)
    assertThat(wit).isEqualTo(
      IoWitFile(
        items = listOf(
          IoWorld(
            location = location.at(1, 1),
            name = "multi-function-device",
            items = listOf(
              IoVariant(
                location = location.at(2, 3),
                name = "filter",
                cases = listOf(
                  IoCase(
                    location = location.at(3, 5),
                    name = "all",
                  ),
                  IoCase(
                    location = location.at(4, 5),
                    name = "none",
                  ),
                  IoCase(
                    location = location.at(5, 5),
                    name = "some",
                    type = IoTypeName.List(IoTypeName.String),
                  ),
                ),
              ),
            ),
          ),
        ),
        location = location,
      ),
    )
  }
}
