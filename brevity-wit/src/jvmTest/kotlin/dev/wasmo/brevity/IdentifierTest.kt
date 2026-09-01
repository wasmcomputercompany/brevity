package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotInstanceOf
import dev.wasmo.brevity.Identifier.Companion.Identifier
import org.junit.Test

class IdentifierTest {
  @Test
  fun identifiers() {
    // Positive examples taken from https://github.com/WebAssembly/component-model/blob/ce4fb2b9435e1a45ff4403a769f7ef650e92e9cc/design/mvp/Explainer.md?plain=1#L2794
    // Letters are fine
    "a".assertIsWellFormed()
    // In all positions
    "a-b-c".assertIsWellFormed()
    // Digits are fine, after the first letter in the first position
    "a1-2-3".assertIsWellFormed()
    // Caps are fine
    "A".assertIsWellFormed()
    // In all positions
    "A-B-C".assertIsWellFormed()
    // And also with solo digits, after the first section
    "A1-2-3".assertIsWellFormed()
    // Mixed digits and letters are fine in all positions
    "a11-w0rds".assertIsWellFormed()
    // Even with leading digits after the first section
    "A11-4CR0NYMS".assertIsWellFormed()
    // Sections with different casings are fine, too
    "m1x3d-4CR0NYMS".assertIsWellFormed()
    // Prefix with % sign as a keyword disambiguator - % gets dropped
    assertThat(Identifier("%abd")).isEqualTo(Identifier("abd"))

    // No empties
    "".assertIsMalformed()
    // Cannot start with digit
    "1-2-3".assertIsMalformed()
    // Or with a hyphen
    "-".assertIsMalformed()
    // Adjacent hyphens are right out
    "a--b".assertIsMalformed()
    // As are terminal hyphens
    "a-b-".assertIsMalformed()
    // Mixed casing within the first position is a no-no
    "aBc".assertIsMalformed()
    // And elsewhere, too
    "a-MaZing".assertIsMalformed()
    // Or valid anywhere
    "ab%d".assertIsMalformed()
    // Fancy uppercase chars can see the door
    "À".assertIsMalformed()
    "Æ".assertIsMalformed()
    // You too, fancy lowercase characters
    "µ".assertIsMalformed()
    "æ".assertIsMalformed()
    // All of you folks around my keyboard - get out
    "!".assertIsMalformed()
    "@".assertIsMalformed()
    "#".assertIsMalformed()
    "$".assertIsMalformed()
    "^".assertIsMalformed()
    "&".assertIsMalformed()
    "*".assertIsMalformed()
    "(".assertIsMalformed()
    ")".assertIsMalformed()
    "{".assertIsMalformed()
    "}".assertIsMalformed()
    // And stay out
    "a!".assertIsMalformed()
    "a@".assertIsMalformed()
    "a#".assertIsMalformed()
    "a$".assertIsMalformed()
    "a^".assertIsMalformed()
    "a&".assertIsMalformed()
    "a*".assertIsMalformed()
    "a(".assertIsMalformed()
    "a)".assertIsMalformed()
    "a{".assertIsMalformed()
    "a}".assertIsMalformed()
  }

  fun String.assertIsMalformed() {
    assertThat(Identifier(this)).isNotInstanceOf<Identifier>()
  }

  fun String.assertIsWellFormed() {
    assertThat(Identifier(this)).isEqualTo(Identifier(this))
  }
}
