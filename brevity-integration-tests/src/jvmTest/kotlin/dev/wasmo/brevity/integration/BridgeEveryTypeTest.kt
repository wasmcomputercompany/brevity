package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier.Companion.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeEveryTypeTest {
  @Test
  fun primitives() = runTest {
    val test = BrevityExecutionTester(
      name = "primitives",
      types = listOf(
        SampleType(
          id = Identifier("bool"),
          witType = "bool",
          kotlinType = "Boolean",
          rustType = "bool",
          values = listOf(
            SampleValue(kotlin = "false", rust = "false"),
            SampleValue(kotlin = "true", rust = "true"),
          ),
        ),
        SampleType(
          id = Identifier("s8"),
          witType = "s8",
          kotlinType = "Byte",
          rustType = "i8",
          values = listOf(
            SampleValue(kotlin = "0.toByte()", rust = "0"),
            SampleValue(kotlin = "kotlin.Byte.MIN_VALUE", rust = "-128"),
            SampleValue(kotlin = "kotlin.Byte.MAX_VALUE", rust = "127"),
          ),
        ),
        SampleType(
          id = Identifier("s16"),
          witType = "s16",
          kotlinType = "Short",
          rustType = "i16",
          values = listOf(
            SampleValue(kotlin = "0.toShort()", rust = "0"),
            SampleValue(kotlin = "kotlin.Short.MIN_VALUE", rust = "-32768"),
            SampleValue(kotlin = "kotlin.Short.MAX_VALUE", rust = "32767"),
          ),
        ),
        SampleType(
          id = Identifier("s32"),
          witType = "s32",
          kotlinType = "Int",
          rustType = "i32",
          values = listOf(
            SampleValue(kotlin = "0", rust = "0"),
            SampleValue(kotlin = "5", rust = "5"),
            SampleValue(kotlin = "kotlin.Int.MIN_VALUE", rust = "-2147483648"),
            SampleValue(kotlin = "kotlin.Int.MAX_VALUE", rust = "2147483647"),
          ),
        ),
        SampleType(
          id = Identifier("s64"),
          witType = "s64",
          kotlinType = "Long",
          rustType = "i64",
          values = listOf(
            SampleValue(kotlin = "0L", rust = "0"),
            SampleValue(kotlin = "5L", rust = "5"),
            SampleValue(kotlin = "kotlin.Long.MIN_VALUE", rust = "-9223372036854775808"),
            SampleValue(kotlin = "kotlin.Long.MAX_VALUE", rust = "9223372036854775807"),
          ),
        ),
        SampleType(
          id = Identifier("u8"),
          witType = "u8",
          kotlinType = "UByte",
          rustType = "u8",
          values = listOf(
            SampleValue(kotlin = "5.toUByte()", rust = "5"),
            SampleValue(kotlin = "kotlin.UByte.MIN_VALUE", rust = "0"),
            SampleValue(kotlin = "kotlin.UByte.MAX_VALUE", rust = "255"),
          ),
        ),
        SampleType(
          id = Identifier("u16"),
          witType = "u16",
          kotlinType = "UShort",
          rustType = "u16",
          values = listOf(
            SampleValue(kotlin = "5.toUShort()", rust = "5"),
            SampleValue(kotlin = "kotlin.UShort.MIN_VALUE", rust = "0"),
            SampleValue(kotlin = "kotlin.UShort.MAX_VALUE", rust = "65535"),
          ),
        ),
        SampleType(
          id = Identifier("u32"),
          witType = "u32",
          kotlinType = "UInt",
          rustType = "u32",
          values = listOf(
            SampleValue(kotlin = "5U", rust = "5"),
            SampleValue(kotlin = "kotlin.UInt.MIN_VALUE", rust = "0"),
            SampleValue(kotlin = "kotlin.UInt.MAX_VALUE", rust = "4294967295"),
          ),
        ),
        SampleType(
          id = Identifier("u64"),
          witType = "u64",
          kotlinType = "ULong",
          rustType = "u64",
          values = listOf(
            SampleValue(kotlin = "5UL", rust = "5"),
            SampleValue(kotlin = "kotlin.ULong.MIN_VALUE", rust = "0"),
            SampleValue(kotlin = "kotlin.ULong.MAX_VALUE", rust = "18446744073709551615"),
          ),
        ),
        SampleType(
          id = Identifier("f32"),
          compareAsString = true,
          witType = "f32",
          kotlinType = "Float",
          rustType = "f32",
          values = listOf(
            SampleValue(kotlin = "0.0f", rust = "0.0"),
            SampleValue(kotlin = "-0.0f", rust = "-0.0"),
            SampleValue(kotlin = "0.5f", rust = "0.5"),
            SampleValue(
              kotlin = "1.17549435E-38f",
              rust = "0.000000000000000000000000000000000000011754944",
            ),
            SampleValue(
              kotlin = "kotlin.Float.MIN_VALUE",
              rust = "0.000000000000000000000000000000000000000000001",
            ),
            SampleValue(kotlin = "kotlin.Float.MAX_VALUE", rust = "3.4028235e38"),
            SampleValue(kotlin = "kotlin.Float.POSITIVE_INFINITY", rust = "std::f32::INFINITY"),
            SampleValue(kotlin = "kotlin.Float.NEGATIVE_INFINITY", rust = "std::f32::NEG_INFINITY"),
            SampleValue(kotlin = "kotlin.Float.NaN", rust = "std::f32::NAN"),
          ),
        ),
        SampleType(
          id = Identifier("f64"),
          compareAsString = true,
          witType = "f64",
          kotlinType = "Double",
          rustType = "f64",
          values = listOf(
            SampleValue(kotlin = "0.0", rust = "0.0"),
            SampleValue(kotlin = "-0.0", rust = "-0.0"),
            SampleValue(kotlin = "0.5", rust = "0.5"),
            SampleValue(kotlin = "2.2250738585072014e-308", rust = "2.2250738585072014e-308"),
            SampleValue(kotlin = "kotlin.Double.MIN_VALUE", rust = "4.9E-324"),
            SampleValue(kotlin = "kotlin.Double.MAX_VALUE", rust = "1.7976931348623157E308"),
            SampleValue(kotlin = "kotlin.Double.POSITIVE_INFINITY", rust = "std::f64::INFINITY"),
            SampleValue(
              kotlin = "kotlin.Double.NEGATIVE_INFINITY",
              rust = "std::f64::NEG_INFINITY",
            ),
            SampleValue(kotlin = "kotlin.Double.NaN", rust = "std::f64::NAN"),
          ),
        ),
        SampleType(
          id = Identifier("string"),
          mustAllocate = true,
          witType = "string",
          kotlinType = "String",
          rustType = "String",
          values = listOf(
            SampleValue(kotlin = "\"hello\"", rust = "\"hello\""),
            SampleValue(kotlin = "\"\"", rust = "\"\""),
            SampleValue(kotlin = "\"abc\\u0000def\"", rust = "\"abc\\u{0000}def\""),
            SampleValue(kotlin = "\"\uD83C\uDF69\"", rust = "\"\uD83C\uDF69\""),
          ),
        ),
        SampleType(
          id = Identifier("char"),
          witType = "char",
          kotlinType = "Int",
          rustType = "char",
          values = listOf(
            SampleValue(kotlin = "'a'.code", rust = "'a'"),
            SampleValue(kotlin = "'b'.code", rust = "'b'"),
          ),
        ),
      ),
    )

    test.execute()
  }

  @Test
  fun enums() = runTest {
    val test = BrevityExecutionTester(
      name = "enums",
      rawWit = """
        |  enum shaker {
        |    salt,
        |    pepper,
        |  }
        |  enum max-byte-enum {
        |    ${List(256) { "v$it" }.joinToString(separator = ",")}
        |  }
        |  enum min-short-enum {
        |    ${List(257) { "v$it" }.joinToString(separator = ",")}
        |  }
        |""".trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("shaker"),
          witType = "shaker",
          kotlinType = "BrevityTest.Shaker",
          rustType = "bindings::Shaker",
          values = listOf(
            SampleValue(kotlin = "BrevityTest.Shaker.Salt", rust = "bindings::Shaker::Salt"),
            SampleValue(kotlin = "BrevityTest.Shaker.Pepper", rust = "bindings::Shaker::Pepper"),
          ),
        ),
        SampleType(
          id = Identifier("max-byte-enum"),
          witType = "max-byte-enum",
          kotlinType = "BrevityTest.MaxByteEnum",
          rustType = "bindings::MaxByteEnum",
          values = listOf(
            SampleValue(kotlin = "BrevityTest.MaxByteEnum.V0", rust = "bindings::MaxByteEnum::V0"),
            SampleValue(kotlin = "BrevityTest.MaxByteEnum.V255", rust = "bindings::MaxByteEnum::V255"),
          ),
        ),
        SampleType(
          id = Identifier("min-short-enum"),
          witType = "min-short-enum",
          kotlinType = "BrevityTest.MinShortEnum",
          rustType = "bindings::MinShortEnum",
          values = listOf(
            SampleValue(kotlin = "BrevityTest.MinShortEnum.V0", rust = "bindings::MinShortEnum::V0"),
            SampleValue(kotlin = "BrevityTest.MinShortEnum.V255", rust = "bindings::MinShortEnum::V255"),
            SampleValue(kotlin = "BrevityTest.MinShortEnum.V256", rust = "bindings::MinShortEnum::V256"),
          ),
        ),
      ),
    )

    test.execute()
  }

  @Test
  fun options() = runTest {
    val test = BrevityExecutionTester(
      name = "options",
      rawWit = """
        |
        |""".trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("option-bool"),
          witType = "option<bool>",
          kotlinType = "Boolean?",
          rustType = "Option<bool>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "false", rust = "Some(false)"),
            SampleValue(kotlin = "true", rust = "Some(true)"),
          ),
        ),
        SampleType(
          id = Identifier("option-s8"),
          witType = "option<s8>",
          kotlinType = "Byte?",
          rustType = "Option<i8>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "0.toByte()", rust = "Some(0)"),
            SampleValue(kotlin = "kotlin.Byte.MIN_VALUE", rust = "Some(-128)"),
            SampleValue(kotlin = "kotlin.Byte.MAX_VALUE", rust = "Some(127)"),
          ),
        ),
        SampleType(
          id = Identifier("option-s32"),
          witType = "option<s32>",
          kotlinType = "Int?",
          rustType = "Option<i32>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "0", rust = "Some(0)"),
            SampleValue(kotlin = "5", rust = "Some(5)"),
            SampleValue(kotlin = "kotlin.Int.MIN_VALUE", rust = "Some(-2147483648)"),
            SampleValue(kotlin = "kotlin.Int.MAX_VALUE", rust = "Some(2147483647)"),
          ),
        ),
        SampleType(
          id = Identifier("option-u64"),
          witType = "option<u64>",
          kotlinType = "ULong?",
          rustType = "Option<u64>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "5UL", rust = "Some(5)"),
            SampleValue(kotlin = "kotlin.ULong.MIN_VALUE", rust = "Some(0)"),
            SampleValue(kotlin = "kotlin.ULong.MAX_VALUE", rust = "Some(18446744073709551615)"),
          ),
        ),
        SampleType(
          id = Identifier("option-f64"),
          witType = "option<f64>",
          kotlinType = "Double?",
          rustType = "Option<f64>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "0.0", rust = "Some(0.0)"),
            SampleValue(kotlin = "0.5", rust = "Some(0.5)"),
            SampleValue(kotlin = "2.2250738585072014e-308", rust = "Some(2.2250738585072014e-308)"),
            SampleValue(kotlin = "kotlin.Double.MIN_VALUE", rust = "Some(4.9E-324)"),
            SampleValue(kotlin = "kotlin.Double.MAX_VALUE", rust = "Some(1.7976931348623157E308)"),
            SampleValue(kotlin = "kotlin.Double.POSITIVE_INFINITY", rust = "Some(std::f64::INFINITY)"),
            SampleValue(
              kotlin = "kotlin.Double.NEGATIVE_INFINITY",
              rust = "Some(std::f64::NEG_INFINITY)",
            ),
          ),
        ),
        SampleType(
          id = Identifier("option-string"),
          mustAllocate = true,
          witType = "option<string>",
          kotlinType = "String?",
          rustType = "Option<String>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "\"hello\"", rust = "Some(\"hello\".to_string())"),
            SampleValue(kotlin = "\"\"", rust = "Some(\"\".to_string())"),
            SampleValue(kotlin = "\"abc\\u0000def\"", rust = "Some(\"abc\\u{0000}def\".to_string())"),
            SampleValue(kotlin = "\"\uD83C\uDF69\"", rust = "Some(\"\uD83C\uDF69\".to_string())"),
          ),
        ),
        SampleType(
          id = Identifier("option-char"),
          witType = "option<char>",
          kotlinType = "Int?",
          rustType = "Option<char>",
          values = listOf(
            SampleValue(kotlin = "null", rust = "None"),
            SampleValue(kotlin = "'a'.code", rust = "Some('a')"),
            SampleValue(kotlin = "'b'.code", rust = "Some('b')"),
          ),
        ),
      ),
    )

    test.execute()
  }

  @Test
  fun lists() = runTest {
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
            SampleValue(kotlin = "booleanArrayOf(true, false, true)", rust = "vec![true, false, true]"),
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
            SampleValue(kotlin = "okio.ByteString.of(kotlin.Byte.MIN_VALUE, kotlin.Byte.MAX_VALUE)", rust = "vec![-128, 127]"),
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
            SampleValue(kotlin = "shortArrayOf(kotlin.Short.MIN_VALUE, kotlin.Short.MAX_VALUE)", rust = "vec![-32768, 32767]"),
            SampleValue(kotlin = "ShortArray(100) { i -> i.toShort() }", rust = "((0..=99).collect::<Vec<i16>>())"),
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
            SampleValue(kotlin = "intArrayOf(kotlin.Int.MIN_VALUE, kotlin.Int.MAX_VALUE)", rust = "vec![-2147483648, 2147483647]"),
            SampleValue(kotlin = "IntArray(100) { i -> i + 32767  }", rust = "((32767..=32866).collect::<Vec<i32>>())"),
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
            SampleValue(kotlin = "longArrayOf(kotlin.Long.MIN_VALUE, kotlin.Long.MAX_VALUE)", rust = "vec![-9223372036854775808, 9223372036854775807]"),
            SampleValue(kotlin = "LongArray(100) { i -> i + 2147483647L }", rust = "((2147483647..=2147483746).collect::<Vec<i64>>())"),
          ),
        ),
        SampleType(
          id = Identifier("list-u8"),
          witType = "list<u8>",
          kotlinType = "okio.ByteString",
          rustType = "Vec<u8>",
          values = listOf(
            SampleValue(kotlin = "okio.ByteString.of()", rust = "vec![]"),
            SampleValue(kotlin = "okio.ByteString.of(0.toUByte().toByte(), 5.toUByte().toByte())", rust = "vec![0, 5]"),
            SampleValue(kotlin = "okio.ByteString.of(kotlin.UByte.MIN_VALUE.toByte(), kotlin.UByte.MAX_VALUE.toByte())", rust = "vec![0, 255]"),
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
            SampleValue(kotlin = "ushortArrayOf(kotlin.UShort.MIN_VALUE, kotlin.UShort.MAX_VALUE)", rust = "vec![0, 65535]"),
            SampleValue(kotlin = "UShortArray(100) { i -> i.toUShort() }", rust = "((0..=99).collect::<Vec<u16>>())"),
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
            SampleValue(kotlin = "uintArrayOf(kotlin.UInt.MIN_VALUE, kotlin.UInt.MAX_VALUE)", rust = "vec![0, 4294967295]"),
            SampleValue(kotlin = "UIntArray(100) { i -> i.toUInt() + 65535U  }", rust = "((65535..=65634).collect::<Vec<u32>>())"),
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
            SampleValue(kotlin = "ulongArrayOf(kotlin.ULong.MIN_VALUE, kotlin.ULong.MAX_VALUE)", rust = "vec![0, 18446744073709551615]"),
            SampleValue(kotlin = "ULongArray(100) { i -> i.toULong() + 4294967295UL }", rust = "((4294967295..=4294967394).collect::<Vec<u64>>())"),
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
              rust = "vec![std::f32::INFINITY, std::f32::NEG_INFINITY]"
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
              rust = "vec![std::f64::INFINITY, std::f64::NEG_INFINITY]"
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
            SampleValue(kotlin = "listOf(\"hello\", \"world\")", rust = "[\"hello\", \"world\"].map(String::from).to_vec()"),
            SampleValue(kotlin = "listOf(\"\", \"abc\\u0000def\", \"\uD83C\uDF69\")", rust = "[\"\", \"abc\\u{0000}def\", \"\uD83C\uDF69\"].map(String::from).to_vec()"),
          ),
        ),
        SampleType(
          id = Identifier("list-char"),
          witType = "list<char>",
          kotlinType = "List<Int>", // In Java/Kotlin char is an u16. In wit it's a u32.
          rustType = "Vec<char>",
          values = listOf(
            SampleValue(kotlin = "listOf<Int>()", rust = "vec![]"),
            SampleValue(kotlin = "listOf('a'.code, 'b'.code, 'c'.code)", rust = "vec!['a', 'b', 'c']"),
          ),
        ),
      ),
    )

    test.execute()
  }


  @Test
  fun sizedLists() = runTest {
    val test = BrevityExecutionTester(
      name = "sizedlists",
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
//        SampleType( // TODO: The two large list tests don't work yet as they appear to be zeroed out in rust.
//          id = Identifier("large-sized-list-s32"),
//          witType = "list<s32, 100>",
//          kotlinType = "List<Int>",
//          rustType = "[i32; 100]",
//          values = listOf(
//            SampleValue(kotlin = "List(100) { i -> i }", rust = "(std::array::from_fn(|i| i as i32))"),
//          ),
//        ),
//        SampleType(
//          id = Identifier("extra-large-sized-list-s32"),
//          witType = "list<s32, 1000>",
//          kotlinType = "List<Int>",
//          rustType = "[i32; 1000]",
//          values = listOf(
//            SampleValue(kotlin = "List(1000) { i -> i }", rust = "(std::array::from_fn(|i| i as i32))"),
//          ),
//        ),
//        SampleType( // Bring this one back if/when this is allowed. Brevity chokes on this right now.
//          id = Identifier("over-9000-sized-list-i32"),
//          witType = "list<s32, 10000>",
//          kotlinType = "List<Int>",
//          rustType = "[i32; 10000]",
//          values = listOf(
//            SampleValue(kotlin = "List(10000) { i -> i }", rust = "(std::array::from_fn(|i| i as i32))"),
//          ),
//        ),
      ),
    )

    test.execute()
  }
}
