package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import org.junit.Test

class KeywordTest {
  @Test
  fun `validate against EBNF`() {
    // Taken from:
    // https://github.com/WebAssembly/component-model/blob/d6a17ea9f828f44c4659d70c35822ab53d2a8a1d/design/mvp/WIT.md?plain=1#L1058C1-L1099C20
    val keywordEbnf = """
      ||keyword ::= 'as'
      ||           | 'async'
      ||           | 'bool'
      ||           | 'borrow'
      ||           | 'char'
      ||           | 'constructor'
      ||           | 'enum'
      ||           | 'export'
      ||           | 'f32'
      ||           | 'f64'
      ||           | 'flags'
      ||           | 'from'
      ||           | 'func'
      ||           | 'future'
      ||           | 'import'
      ||           | 'include'
      ||           | 'interface'
      ||           | 'list'
      ||           | 'map'
      ||           | 'option'
      ||           | 'own'
      ||           | 'package'
      ||           | 'record'
      ||           | 'resource'
      ||           | 'result'
      ||           | 's16'
      ||           | 's32'
      ||           | 's64'
      ||           | 's8'
      ||           | 'static'
      ||           | 'stream'
      ||           | 'string'
      ||           | 'tuple'
      ||           | 'type'
      ||           | 'u16'
      ||           | 'u32'
      ||           | 'u64'
      ||           | 'u8'
      ||           | 'use'
      ||           | 'variant'
      ||           | 'with'
      ||           | 'world'
    """.trimMargin(marginPrefix = "||")

    val keywords = run {
      keywordEbnf.replace(Regex("keyword ::= | |'|[|]"), "")
        .split("\n")
    }

    for (keyword in keywords) {
      assertThat(Keyword.keywordFor(keyword)).isNotNull()
    }

    assertThat(keywords.size).isEqualTo(Keyword.values().size)
  }
}
