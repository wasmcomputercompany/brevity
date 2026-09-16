package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeSizedListsTest {
  @Test
  fun test() = runTest {
    val test = BrevityExecutionTester(
      name = "sizedLists",
      types = listOf(
        SampleType(
          id = Identifier("sized-list-bool"),
          witType = "list<bool,2>",
          kotlinType = "List<Boolean>",
          rustType = "[bool; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(false, false)", rust = "[false, false]"),
            SampleValue(kotlin = "listOf(true, true)", rust = "[true, true]"),
            SampleValue(kotlin = "listOf(true, false)", rust = "[true, false]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-s8"),
          witType = "list<s8, 2>",
          kotlinType = "List<Byte>",
          rustType = "[i8; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0.toByte(), 5.toByte())", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.Byte.MIN_VALUE, kotlin.Byte.MAX_VALUE)", rust = "[-128, 127]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-s16"),
          witType = "list<s16, 2>",
          kotlinType = "List<Short>",
          rustType = "[i16; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0.toShort(), 5.toShort())", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.Short.MIN_VALUE, kotlin.Short.MAX_VALUE)", rust = "[-32768, 32767]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-s32"),
          witType = "list<s32, 2>",
          kotlinType = "List<Int>",
          rustType = "[i32; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0, 5)", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.Int.MIN_VALUE, kotlin.Int.MAX_VALUE)", rust = "[-2147483648, 2147483647]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-s64"),
          witType = "list<s64, 2>",
          kotlinType = "List<Long>",
          rustType = "[i64; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0L, 5L)", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.Long.MIN_VALUE, kotlin.Long.MAX_VALUE)", rust = "[-9223372036854775808, 9223372036854775807]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-u8"),
          witType = "list<u8, 2>",
          kotlinType = "List<UByte>",
          rustType = "[u8; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0.toUByte(), 5.toUByte())", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.UByte.MIN_VALUE, kotlin.UByte.MAX_VALUE)", rust = "[0, 255]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-u16"),
          witType = "list<u16, 2>",
          kotlinType = "List<UShort>",
          rustType = "[u16; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0.toUShort(), 5.toUShort())", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.UShort.MIN_VALUE, kotlin.UShort.MAX_VALUE)", rust = "[0, 65535]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-u32"),
          witType = "list<u32, 2>",
          kotlinType = "List<UInt>",
          rustType = "[u32; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0U, 5U)", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.UInt.MIN_VALUE, kotlin.UInt.MAX_VALUE)", rust = "[0, 4294967295]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-u64"),
          witType = "list<u64, 2>",
          kotlinType = "List<ULong>",
          rustType = "[u64; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0UL, 5UL)", rust = "[0, 5]"),
            SampleValue(kotlin = "listOf(kotlin.ULong.MIN_VALUE, kotlin.ULong.MAX_VALUE)", rust = "[0, 18446744073709551615]"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-f32"),
          witType = "list<f32, 2>",
          kotlinType = "List<Float>",
          rustType = "[f32; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0.0f, 0.5f)", rust = "[0.0, 0.5]"),
            SampleValue(
              kotlin = "listOf(-0.0f, 1.17549435E-38f)",
              rust = "[-0.0, 0.000000000000000000000000000000000000011754944]",
            ),
            SampleValue(
              kotlin = "listOf(kotlin.Float.MIN_VALUE, kotlin.Float.MAX_VALUE)",
              rust = "[0.000000000000000000000000000000000000000000001, 3.4028235e38]",
            ),
            SampleValue(
              kotlin = "listOf(kotlin.Float.POSITIVE_INFINITY, kotlin.Float.NEGATIVE_INFINITY)",
              rust = "[std::f32::INFINITY, std::f32::NEG_INFINITY]"
            ),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-f64"),
          witType = "list<f64, 2>",
          kotlinType = "List<Double>",
          rustType = "[f64; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(0.0, 0.5)", rust = "[0.0, 0.5]"),
            SampleValue(
              kotlin = "listOf(-0.0, 2.2250738585072014e-308)",
              rust = "[-0.0, 2.2250738585072014e-308]",
            ),
            SampleValue(
              kotlin = "listOf(kotlin.Double.MIN_VALUE, kotlin.Double.MAX_VALUE)",
              rust = "[4.9E-324, 1.7976931348623157E308]",
            ),
            SampleValue(
              kotlin = "listOf(kotlin.Double.POSITIVE_INFINITY, kotlin.Double.NEGATIVE_INFINITY)",
              rust = "[std::f64::INFINITY, std::f64::NEG_INFINITY]"
            ),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-string"),
          witType = "list<string, 2>",
          kotlinType = "List<String>",
          rustType = "[String; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf(\"hello\", \"world\")", rust = "[\"hello\", \"world\"].map(String::from)"),
            SampleValue(kotlin = "listOf(\"\", \"abc\\u0000def\")", rust = "[\"\", \"abc\\u{0000}def\"].map(String::from)"),
            SampleValue(kotlin = "listOf(\"emoji\", \"\uD83C\uDF69\")", rust = "[\"emoji\", \"\uD83C\uDF69\"].map(String::from)"),
          ),
        ),
        SampleType(
          id = Identifier("sized-list-char"),
          witType = "list<char, 2>",
          kotlinType = "List<Int>", // In Java/Kotlin char is an u16. In wit it's a u32.
          rustType = "[char; 2]",
          values = listOf(
            SampleValue(kotlin = "listOf('a'.code, 'b'.code)", rust = "['a', 'b']"),
          ),
        ),
        SampleType(
          id = Identifier("large-sized-list-s32"),
          witType = "list<s32, 18>",
          kotlinType = "List<Int>",
          rustType = "[i32; 18]",
          values = listOf(
            SampleValue(kotlin = "List(18) { i -> i }", rust = "(std::array::from_fn(|i| i as i32))"),
          ),
        ),
      ),
    )

    test.execute()
  }
}
