package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.integration.CallingMechanism.Async
import dev.wasmo.brevity.integration.PassingMechanism.Parameter
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Confirm that we can transmit a value of each type through three code paths:
 *
 *  * As a parameter (flattened if it encodes to at most 16 core values)
 *  * As a parameter with padding, to force it to not be flattened
 *  * As a return value (flattened if it encodes to at most 1 core value)
 */
class BridgeEveryTypeTest {
  @Test
  fun booleans() = runTest {
    val test = BrevityExecutionTester(
      name = "booleans",
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
      ),
    )

    test.execute()
  }

  @Test
  fun integerTypes() = runTest {
    val test = BrevityExecutionTester(
      name = "integers",
      types = listOf(
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
      ),
    )

    test.execute()
  }

  @Test
  fun floatingPointTypes() = runTest {
    val test = BrevityExecutionTester(
      name = "floatingPoints",
      types = listOf(
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
      ),
    )

    test.execute()
  }

  @Test
  fun characterTypes() = runTest {
    val test = BrevityExecutionTester(
      name = "characterTypes",
      types = listOf(
        SampleType(
          id = Identifier("string"),
          witType = "string",
          kotlinType = "String",
          rustType = "String",
          values = listOf(
            SampleValue(kotlin = "\"hello\"", rust = "\"hello\""),
            SampleValue(kotlin = "\"\"", rust = "\"\""),
            SampleValue(kotlin = "\"abc\\u0000def\"", rust = "\"abc\\u{0000}def\""),
            SampleValue(kotlin = "\"\uD83C\uDF69\"", rust = "\"\uD83C\uDF69\""),
          ),
          borrowRustValue = { "($it).as_str()" },
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
      rawWitTypeDeclarations = """
        |enum shaker {
        |  salt,
        |  pepper,
        |}
        |enum max-byte-enum {
        |  ${List(256) { "v$it" }.joinToString(separator = ",")}
        |}
        |enum min-short-enum {
        |  ${List(257) { "v$it" }.joinToString(separator = ",")}
        |}
        """.trimMargin(),
      rawWitUseStatements = """
        |use types.{shaker, max-byte-enum, min-short-enum};
        |
        """.trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("shaker"),
          witType = "shaker",
          kotlinType = "wit.brevity.testing.Shaker",
          rustType = "bindings::Shaker",
          values = listOf(
            SampleValue(kotlin = "wit.brevity.testing.Shaker.Salt", rust = "bindings::Shaker::Salt"),
            SampleValue(kotlin = "wit.brevity.testing.Shaker.Pepper", rust = "bindings::Shaker::Pepper"),
          ),
        ),
        SampleType(
          id = Identifier("max-byte-enum"),
          witType = "max-byte-enum",
          kotlinType = "wit.brevity.testing.MaxByteEnum",
          rustType = "bindings::MaxByteEnum",
          values = listOf(
            SampleValue(kotlin = "wit.brevity.testing.MaxByteEnum.V0", rust = "bindings::MaxByteEnum::V0"),
            SampleValue(kotlin = "wit.brevity.testing.MaxByteEnum.V255", rust = "bindings::MaxByteEnum::V255"),
          ),
        ),
        SampleType(
          id = Identifier("min-short-enum"),
          witType = "min-short-enum",
          kotlinType = "wit.brevity.testing.MinShortEnum",
          rustType = "bindings::MinShortEnum",
          values = listOf(
            SampleValue(kotlin = "wit.brevity.testing.MinShortEnum.V0", rust = "bindings::MinShortEnum::V0"),
            SampleValue(kotlin = "wit.brevity.testing.MinShortEnum.V255", rust = "bindings::MinShortEnum::V255"),
            SampleValue(kotlin = "wit.brevity.testing.MinShortEnum.V256", rust = "bindings::MinShortEnum::V256"),
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
      ),
      predicate = ::expectedWorkingTests,
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
      ),
      predicate = ::expectedWorkingTests,
    )

    test.execute()
  }

  @Test
  fun sizedLists() = runTest {
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
          id = Identifier("large-sized-list-s32"),
          witType = "list<s32, 18>",
          kotlinType = "List<Int>",
          rustType = "[i32; 18]",
          values = listOf(
            SampleValue(kotlin = "List(18) { i -> i }", rust = "(std::array::from_fn(|i| i as i32))"),
          ),
        ),
      ),
      predicate = {
        when {
          // Several cases are disabled here due to a bug in wit-bindgen
          // https://github.com/bytecodealliance/wit-bindgen/issues/1728
          !functionParent.isWorld -> false
          callingMechanism == Async && type.id == Identifier("sized-list-string") -> false
          orientation == Import && type.id == Identifier("sized-list-string") -> false
          // wit-bindgen panics on sized-lists + async + import.
          // https://github.com/bytecodealliance/wit-bindgen/issues/1744
          callingMechanism == Async && orientation == Import -> false
          else -> true
        }
      },
    )

    test.execute()
  }

  @Test
  fun flags() = runTest {
    val enumSizes = listOf(
      1, // min byte
      8, // max byte
      9, // min short
      16, // max short
      17, // min int
      32, // max int
    )
    val test = BrevityExecutionTester(
      name = "flags",
      rawWitTypeDeclarations = enumSizes.joinToString(separator = "\n") { story ->
        """
        |flags elevator-${story}-story {
        |  ${List(story) { "floor-${it + 1}-chosen" }.joinToString(separator = ",")}
        |}
        |
        """.trimMargin()
      },
      rawWitUseStatements = """
        |use types.{${enumSizes.joinToString { "elevator-$it-story" }}};
        |
        """.trimMargin(),
      types = enumSizes.map { i ->
        SampleType(
          id = Identifier("elevator-$i-story"),
          witType = "elevator-$i-story",
          kotlinType = "wit.brevity.testing.Elevator${i}Story",
          rustType = "bindings::Elevator${i}Story",
          values = buildList {
            // All `true`
            add(
              SampleValue(
                kotlin = "wit.brevity.testing.Elevator${i}Story(${
                  List(i) { "true" }.joinToString(separator = ",")
                })",
                rust = List(i) { floor -> "bindings::Elevator${i}Story::FLOOR_${floor + 1}_CHOSEN" }.joinToString(
                  separator = " | ",
                ),
              ),
            )
            // All `false`.
            add(
              SampleValue(
                kotlin = "wit.brevity.testing.Elevator${i}Story(${
                  List(i) { "false" }.joinToString(separator = ",")
                })",
                rust = "bindings::Elevator${i}Story::empty()",
              ),
            )
            if (i > 1) {
              // Even floors
              add(
                SampleValue(
                  kotlin = "wit.brevity.testing.Elevator${i}Story(${
                    List(i) { if (it % 2 == 1) "true" else "false" }.joinToString(separator = ",")
                  })",
                  rust = List(i / 2) { floor -> "bindings::Elevator${i}Story::FLOOR_${(floor + 1) * 2}_CHOSEN" }.joinToString(
                    separator = " | ",
                  ),
                ),
              )
              // Odd floors
              add(
                SampleValue(
                  kotlin = "wit.brevity.testing.Elevator${i}Story(${
                    List(i) { if (it % 2 == 0) "true" else "false" }.joinToString(separator = ",")
                  })",
                  rust = List((i + 1) / 2) { floor -> "bindings::Elevator${i}Story::FLOOR_${((floor + 1) * 2) - 1}_CHOSEN" }.joinToString(
                    separator = " | ",
                  ),
                ),
              )
            }
          },
        )
      },
    )

    test.execute()
  }

  @Test
  fun typeAliases() = runTest {
    val test = BrevityExecutionTester(
      name = "typeAliases",
      rawWitTypeDeclarations = """
        |type email-address = string;
        """.trimMargin(),
      rawWitUseStatements = """
        |use types.{email-address};
        |
        """.trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("email-address"),
          witType = "email-address",
          kotlinType = "wit.brevity.testing.EmailAddress",
          rustType = "bindings::EmailAddress",
          values = listOf(
            SampleValue(
              kotlin = "wit.brevity.testing.EmailAddress(\"hello@wasmo.com\")",
              rust = "\"hello@wasmo.com\"",
            ),
            SampleValue(
              kotlin = "wit.brevity.testing.EmailAddress(\"noreply@wasmo.com\")",
              rust = "\"noreply@wasmo.com\"",
            ),
          ),
        ),
      ),
      predicate = ::expectedWorkingTests,
    )

    test.execute()
  }

  @Test
  fun tuples() = runTest {
    val test = BrevityExecutionTester(
      name = "tuples",
      rawWitTypeDeclarations = """
        |enum colour-space {
        |  srgb,
        |  linear-srgb,
        |  display-p3,
        |}
        """.trimMargin(),
      rawWitUseStatements = """
        |use types.{colour-space};
        |
        """.trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("pair-s8-bool"),
          witType = "tuple<s8, bool>",
          kotlinType = "Pair<Byte, Boolean>",
          rustType = "(i8, bool)",
          values = listOf(
            SampleValue(kotlin = "Pair(0.toByte(), false)", rust = "(0, false)"),
            SampleValue(kotlin = "Pair(5.toByte(), false)", rust = "(5, false)"),
            SampleValue(kotlin = "Pair(kotlin.Byte.MIN_VALUE, true)", rust = "(-128, true)"),
            SampleValue(kotlin = "Pair(kotlin.Byte.MAX_VALUE, true)", rust = "(127, true)"),
          ),
        ),
        SampleType(
          id = Identifier("triple-s32-u64-char"),
          witType = "tuple<s32, u64, char>",
          kotlinType = "Triple<Int, ULong, Int>",
          rustType = "(i32, u64, char)",
          values = listOf(
            SampleValue(kotlin = "Triple(0, 0UL, 'a'.code)", rust = "(0, 0, 'a')"),
            SampleValue(kotlin = "Triple(5, 5UL, 'b'.code)", rust = "(5, 5, 'b')"),
            SampleValue(
              kotlin = "Triple(kotlin.Int.MIN_VALUE, kotlin.ULong.MIN_VALUE, 'c'.code)",
              rust = "(-2147483648, 0, 'c')",
            ),
            SampleValue(
              kotlin = "Triple(kotlin.Int.MAX_VALUE, kotlin.ULong.MAX_VALUE, 'z'.code)",
              rust = "(2147483647, 18446744073709551615, 'z')",
            ),
          ),
        ),
        SampleType(
          id = Identifier("quad-3d-named-vertices"),
          witType = "tuple<string, f64, f64, f64>",
          kotlinType = "dev.wasmo.brevity.Quad<String, Double, Double, Double>",
          rustType = "(String, f64, f64, f64)",
          values = listOf(
            SampleValue(kotlin = "dev.wasmo.brevity.Quad(\"Origin\", 0.0, 0.0, 0.0)", rust = "(\"Origin\".to_string(), 0.0, 0.0, 0.0)"),
            SampleValue(kotlin = "dev.wasmo.brevity.Quad(\"\uD83D\uDCC8\", 0.5, 0.5, 0.5)", rust = "(\"\uD83D\uDCC8\".to_string(), 0.5, 0.5, 0.5)"),
            SampleValue(
              kotlin = "dev.wasmo.brevity.Quad(\"To the\\u0000 fences\", 2.2250738585072014e-308, kotlin.Double.MIN_VALUE, kotlin.Double.MAX_VALUE)",
              rust = "(\"To the\\u{0000} fences\".to_string(), 2.2250738585072014e-308, 4.9E-324, 1.7976931348623157E308)",
            ),
            SampleValue(
              kotlin = "dev.wasmo.brevity.Quad(\"\", kotlin.Double.POSITIVE_INFINITY, kotlin.Double.NEGATIVE_INFINITY, kotlin.Double.POSITIVE_INFINITY)",
              rust = "(\"\".to_string(), std::f64::INFINITY, std::f64::NEG_INFINITY, std::f64::INFINITY)",
            ),
          ),
        ),
        SampleType(
          id = Identifier("quintuple-colour-space"),
          witType = "tuple<u8, u8, u8, u8, colour-space>",
          kotlinType = "List<*>",
          rustType = "(u8, u8, u8, u8, bindings::ColourSpace)",
          values = listOf(
            SampleValue(
              kotlin = "listOf(0.toUByte(), 0.toUByte(), 0.toUByte(), 255.toUByte(), wit.brevity.testing.ColourSpace.Srgb)",
              rust = "(0, 0, 0, 255, bindings::ColourSpace::Srgb)",
            ),
            SampleValue(
              kotlin = "listOf(255.toUByte(), 255.toUByte(), 255.toUByte(), 255.toUByte(), wit.brevity.testing.ColourSpace.LinearSrgb)",
              rust = "(255, 255, 255, 255, bindings::ColourSpace::LinearSrgb)",
            ),
            SampleValue(
              kotlin = "listOf(128.toUByte(), 128.toUByte(), 0.toUByte(), 128.toUByte(), wit.brevity.testing.ColourSpace.DisplayP3)",
              rust = "(128, 128, 0, 128, bindings::ColourSpace::DisplayP3)",
            ),
          ),
        ),
      ),
      predicate = ::expectedWorkingTests,
    )

    test.execute()
  }

  private fun expectedWorkingTests(
    testFunction: TestFunction,
  ): Boolean = when {
    // Skip tests that don't compile because Rust uses `String` in some cases and `&str` in others,
    // and we don't generate smart-enough code to convert between them.
    testFunction.orientation == Import && testFunction.passingMechanism is Parameter -> false
    else -> true
  }
}
