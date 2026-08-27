package dev.wasmo.brevity.io.validation

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Issue
import dev.wasmo.brevity.IssueCollector
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.io.IoCase
import dev.wasmo.brevity.io.IoDeclaration
import dev.wasmo.brevity.io.IoField
import dev.wasmo.brevity.io.IoFlag
import dev.wasmo.brevity.io.IoFlags
import dev.wasmo.brevity.io.IoFunction
import dev.wasmo.brevity.io.IoInclude
import dev.wasmo.brevity.io.IoInterface
import dev.wasmo.brevity.io.IoParameter
import dev.wasmo.brevity.io.IoRecord
import dev.wasmo.brevity.io.IoResource
import dev.wasmo.brevity.io.IoToplevelWitPackage
import dev.wasmo.brevity.io.IoTypeAlias
import dev.wasmo.brevity.io.IoTypeName
import dev.wasmo.brevity.io.IoUse
import dev.wasmo.brevity.io.IoVariant
import dev.wasmo.brevity.io.IoWitFile
import dev.wasmo.brevity.io.IoWorld
import dev.wasmo.brevity.io.toUsePath
import dev.wasmo.brevity.toPackageName
import org.junit.Test

class ValidateUniqueInternalNames {
  private val cliLocation = Location("")

  @Test
  fun validatesUniqueInternalNames() {
    assertItemProducesIssues(
      IoFlags(
        location = cliLocation.at(1, 2),
        name = "streets",
        flags = listOf(
          IoFlag(location = cliLocation.at(2, 1), name = "street-1st"),
          IoFlag(location = cliLocation.at(3, 1), name = "street-1st"),
          IoFlag(location = cliLocation.at(4, 1), name = "street-2ND"),
          IoFlag(location = cliLocation.at(5, 1), name = "STREET-2nd"),
        ),
      ),
      Issue(
        "Duplicate flags named street-1st",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
      Issue(
        "Duplicate flags named street-2nd",
        listOf(
          cliLocation.at(4, 1),
          cliLocation.at(5, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoFunction(
        location = cliLocation.at(1, 2),
        name = "function",
        parameters = listOf(
          IoParameter(location = cliLocation.at(2, 1), name = "param1", type = IoTypeName.Bool),
          IoParameter(location = cliLocation.at(3, 1), name = "PARAM1", type = IoTypeName.Bool),
        ),
      ),
      Issue(
        "Duplicate parameters named param1",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoInclude(
        location = cliLocation.at(1, 2),
        path = "interface".toUsePath(),
        items = listOf(
          IoInclude.Item(
            location = cliLocation.at(2, 1),
            type = IoTypeName.Declared("included-type1"),
            name = Identifier("included-alias"),
          ),
          IoInclude.Item(
            location = cliLocation.at(3, 1),
            type = IoTypeName.Declared("included-type1"),
            name = Identifier("included-ALIAS"),
          ),
        ),
      ),
      Issue(
        "Duplicate include aliases named included-alias",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoInterface(
        location = cliLocation.at(1, 2),
        name = "myInterface",
        items = listOf(
          IoResource(
            location = cliLocation.at(2, 1),
            name = "interface-item-1",
          ),
          IoTypeAlias(
            location = cliLocation.at(3, 1),
            name = Identifier("interface-ITEM-1"),
            target = IoTypeName.Bool,
          ),
        ),
      ),
      Issue(
        "Duplicate interface items named interface-item-1",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoRecord(
        location = cliLocation.at(1, 2),
        name = "record",
        fields = listOf(
          IoField(
            location = cliLocation.at(2, 1),
            type = IoTypeName.Bool,
            name = "field-1",
          ),
          IoField(
            location = cliLocation.at(3, 1),
            type = IoTypeName.Bool,
            name = "FIELD-1",
          ),
        ),
      ),
      Issue(
        "Duplicate fields named field-1",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoResource(
        location = cliLocation.at(1, 2),
        name = "resource",
        functions = listOf(
          IoFunction(
            location = cliLocation.at(2, 1),
            name = "function-1",
          ),
          IoFunction(
            location = cliLocation.at(3, 1),
            name = "FUNCTION-1",
          ),
        ),
      ),
      Issue(
        "Duplicate functions named function-1",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoVariant(
        location = cliLocation.at(1, 2),
        name = "variant",
        cases = listOf(
          IoCase(
            location = cliLocation.at(2, 1),
            name = "case-1",
          ),
          IoCase(
            location = cliLocation.at(3, 1),
            name = "CASE-1",
          ),
        ),
      ),
      Issue(
        "Duplicate cases named case-1",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoUse(
        location = cliLocation.at(1, 2),
        path = "use".toUsePath(),
        items = listOf(
          IoUse.Item(
            location = cliLocation.at(2, 1),
            type = IoTypeName.Declared("use-type"),
          ),
          IoUse.Item(
            location = cliLocation.at(3, 1),
            type = IoTypeName.Declared("USE-type"),
          ),
          IoUse.Item(
            location = cliLocation.at(4, 1),
            type = IoTypeName.Declared("aliased-type"),
          ),
          IoUse.Item(
            location = cliLocation.at(5, 1),
            type = IoTypeName.Declared("USE-type-2"),
            alias = Identifier("ALIASED-type"),
          ),
        ),
      ),
      Issue(
        "Duplicate use named use-type",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
      Issue(
        "Duplicate use named aliased-type",
        listOf(
          cliLocation.at(4, 1),
          cliLocation.at(5, 1),
        ),
      ),
    )

    assertItemProducesIssues(
      IoWorld(
        location = cliLocation.at(1, 2),
        name = "myInterface",
        items = listOf(
          IoResource(
            location = cliLocation.at(2, 1),
            name = "world-item-1",
          ),
          IoTypeAlias(
            location = cliLocation.at(3, 1),
            name = Identifier("world-ITEM-1"),
            target = IoTypeName.Bool,
          ),
        ),
      ),
      Issue(
        "Duplicate world items named world-item-1",
        listOf(
          cliLocation.at(2, 1),
          cliLocation.at(3, 1),
        ),
      ),
    )
  }

  fun assertItemProducesIssues(item: IoDeclaration, vararg expected: Issue) {
    val cliWorld = IoWorld(
      location = cliLocation,
      name = "world",
      items = if (item is IoWorld.Item) {
        listOf(item)
      } else {
        emptyList()
      },
    )
    val cliInterface = IoInterface(
      location = cliLocation,
      name = "interface",
      items = if (item is IoInterface.Item && item !is IoWorld.Item) {
        listOf(item)
      } else {
        emptyList()
      },
    )

    val cliPackage = IoToplevelWitPackage(
      packageName = "wasi:cli".toPackageName(),
      files = listOf(
        IoWitFile(
          location = cliLocation,
          packageName = "wasi:cli".toPackageName(),
          items = if (item is IoWitFile.Item) {
            listOf(item)
          } else {
            listOf(cliInterface, cliWorld)
          },
        ),
      ),
    )

    val issueCollector = IssueCollector()

    with(issueCollector) {
      validateUniqueServiceNames(listOf(cliPackage))
    }

    assertThat(issueCollector.issues).containsExactlyInAnyOrder(*expected)
  }
}
