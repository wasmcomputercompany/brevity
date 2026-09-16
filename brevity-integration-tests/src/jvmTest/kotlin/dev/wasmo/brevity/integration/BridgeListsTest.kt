package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeListsTest {
  @Test
  fun test() = runTest {
    val test = BrevityExecutionTester(
      name = "lists",
      types = listOf(
        SampleType(
          id = Identifier("list-bool"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<bool>",
          kotlinType = "BooleanArray",
          rustType = "Vec<bool>",
          values = listOf(
            SampleValue(kotlin = "booleanArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "booleanArrayOf(false)", rust = "vec![false]"),
            SampleValue(kotlin = "booleanArrayOf(true)", rust = "vec![true]"),
            SampleValue(
              kotlin = "booleanArrayOf(true, false, true)",
              rust = "vec![true, false, true]",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-s8"),
          witType = "list<s8>",
          kotlinType = "okio.ByteString",
          rustType = "Vec<i8>",
          values = listOf(
            SampleValue(kotlin = "okio.ByteString.of()", rust = "vec![]"),
            SampleValue(kotlin = "okio.ByteString.of(0.toByte())", rust = "vec![0]"),
            SampleValue(
              kotlin = "okio.ByteString.of(kotlin.Byte.MIN_VALUE, kotlin.Byte.MAX_VALUE)",
              rust = "vec![-128, 127]",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-s16"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<s16>",
          kotlinType = "ShortArray",
          rustType = "Vec<i16>",
          values = listOf(
            SampleValue(kotlin = "shortArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "shortArrayOf(0.toShort(), 5.toShort())", rust = "vec![0, 5]"),
            SampleValue(
              kotlin = "shortArrayOf(kotlin.Short.MIN_VALUE, kotlin.Short.MAX_VALUE)",
              rust = "vec![-32768, 32767]",
            ),
            SampleValue(
              kotlin = "ShortArray(100) { i -> i.toShort() }",
              rust = "((0..=99).collect::<Vec<i16>>())",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-s32"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<s32>",
          kotlinType = "IntArray",
          rustType = "Vec<i32>",
          values = listOf(
            SampleValue(kotlin = "intArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "intArrayOf(0, 5)", rust = "vec![0, 5]"),
            SampleValue(
              kotlin = "intArrayOf(kotlin.Int.MIN_VALUE, kotlin.Int.MAX_VALUE)",
              rust = "vec![-2147483648, 2147483647]",
            ),
            SampleValue(
              kotlin = "IntArray(100) { i -> i + 32767  }",
              rust = "((32767..=32866).collect::<Vec<i32>>())",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-s64"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<s64>",
          kotlinType = "LongArray",
          rustType = "Vec<i64>",
          values = listOf(
            SampleValue(kotlin = "longArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "longArrayOf(0L, 5L)", rust = "vec![0, 5]"),
            SampleValue(
              kotlin = "longArrayOf(kotlin.Long.MIN_VALUE, kotlin.Long.MAX_VALUE)",
              rust = "vec![-9223372036854775808, 9223372036854775807]",
            ),
            SampleValue(
              kotlin = "LongArray(100) { i -> i + 2147483647L }",
              rust = "((2147483647..=2147483746).collect::<Vec<i64>>())",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-u8"),
          witType = "list<u8>",
          kotlinType = "okio.ByteString",
          rustType = "Vec<u8>",
          values = listOf(
            SampleValue(kotlin = "okio.ByteString.of()", rust = "vec![]"),
            SampleValue(
              kotlin = "okio.ByteString.of(0.toUByte().toByte(), 5.toUByte().toByte())",
              rust = "vec![0, 5]",
            ),
            SampleValue(
              kotlin = "okio.ByteString.of(kotlin.UByte.MIN_VALUE.toByte(), kotlin.UByte.MAX_VALUE.toByte())",
              rust = "vec![0, 255]",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-u16"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<u16>",
          kotlinType = "UShortArray",
          rustType = "Vec<u16>",
          values = listOf(
            SampleValue(kotlin = "ushortArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "ushortArrayOf(0.toUShort(), 5.toUShort())", rust = "vec![0, 5]"),
            SampleValue(
              kotlin = "ushortArrayOf(kotlin.UShort.MIN_VALUE, kotlin.UShort.MAX_VALUE)",
              rust = "vec![0, 65535]",
            ),
            SampleValue(
              kotlin = "UShortArray(100) { i -> i.toUShort() }",
              rust = "((0..=99).collect::<Vec<u16>>())",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-u32"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<u32>",
          kotlinType = "UIntArray",
          rustType = "Vec<u32>",
          values = listOf(
            SampleValue(kotlin = "uintArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "uintArrayOf(0U, 5U)", rust = "vec![0, 5]"),
            SampleValue(
              kotlin = "uintArrayOf(kotlin.UInt.MIN_VALUE, kotlin.UInt.MAX_VALUE)",
              rust = "vec![0, 4294967295]",
            ),
            SampleValue(
              kotlin = "UIntArray(100) { i -> i.toUInt() + 65535U  }",
              rust = "((65535..=65634).collect::<Vec<u32>>())",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-u64"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<u64>",
          kotlinType = "ULongArray",
          rustType = "Vec<u64>",
          values = listOf(
            SampleValue(kotlin = "ulongArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "ulongArrayOf(0UL, 5UL)", rust = "vec![0, 5]"),
            SampleValue(
              kotlin = "ulongArrayOf(kotlin.ULong.MIN_VALUE, kotlin.ULong.MAX_VALUE)",
              rust = "vec![0, 18446744073709551615]",
            ),
            SampleValue(
              kotlin = "ULongArray(100) { i -> i.toULong() + 4294967295UL }",
              rust = "((4294967295..=4294967394).collect::<Vec<u64>>())",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-f32"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<f32>",
          kotlinType = "FloatArray",
          rustType = "Vec<f32>",
          values = listOf(
            SampleValue(kotlin = "floatArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "floatArrayOf(0.0f, -0.0f, 0.5f)", rust = "vec![0.0, -0.0, 0.5]"),
            SampleValue(
              kotlin = "floatArrayOf(1.17549435E-38f)",
              rust = "vec![0.000000000000000000000000000000000000011754944]",
            ),
            SampleValue(
              kotlin = "floatArrayOf(kotlin.Float.MIN_VALUE, kotlin.Float.MAX_VALUE)",
              rust = "vec![0.000000000000000000000000000000000000000000001, 3.4028235e38]",
            ),
            SampleValue(
              kotlin = "floatArrayOf(kotlin.Float.POSITIVE_INFINITY, kotlin.Float.NEGATIVE_INFINITY)",
              rust = "vec![std::f32::INFINITY, std::f32::NEG_INFINITY]",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-f64"),
          kotlinEqualityMethod = "contentEquals",
          witType = "list<f64>",
          kotlinType = "DoubleArray",
          rustType = "Vec<f64>",
          values = listOf(
            SampleValue(kotlin = "doubleArrayOf()", rust = "vec![]"),
            SampleValue(kotlin = "doubleArrayOf(0.0, -0.0, 0.5)", rust = "vec![0.0, -0.0, 0.5]"),
            SampleValue(
              kotlin = "doubleArrayOf(2.2250738585072014e-308)",
              rust = "vec![2.2250738585072014e-308]",
            ),
            SampleValue(
              kotlin = "doubleArrayOf(kotlin.Double.MIN_VALUE, kotlin.Double.MAX_VALUE)",
              rust = "vec![4.9E-324, 1.7976931348623157E308]",
            ),
            SampleValue(
              kotlin = "doubleArrayOf(kotlin.Double.POSITIVE_INFINITY, kotlin.Double.NEGATIVE_INFINITY)",
              rust = "vec![std::f64::INFINITY, std::f64::NEG_INFINITY]",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-string"),
          witType = "list<string>",
          kotlinType = "List<String>",
          rustType = "Vec<String>",
          values = listOf(
            SampleValue(kotlin = "listOf<String>()", rust = "vec![] as Vec<String>"),
            SampleValue(
              kotlin = "listOf(\"hello\", \"world\")",
              rust = "[\"hello\", \"world\"].map(String::from).to_vec()",
            ),
            SampleValue(
              kotlin = "listOf(\"\", \"abc\\u0000def\", \"\uD83C\uDF69\")",
              rust = "[\"\", \"abc\\u{0000}def\", \"\uD83C\uDF69\"].map(String::from).to_vec()",
            ),
          ),
        ),
        SampleType(
          id = Identifier("list-char"),
          witType = "list<char>",
          kotlinType = "List<Int>", // In Java/Kotlin char is an u16. In wit it's a u32.
          rustType = "Vec<char>",
          values = listOf(
            SampleValue(kotlin = "listOf<Int>()", rust = "vec![]"),
            SampleValue(
              kotlin = "listOf('a'.code, 'b'.code, 'c'.code)",
              rust = "vec!['a', 'b', 'c']",
            ),
          ),
        ),
      ),
    )

    test.execute()
  }
}
