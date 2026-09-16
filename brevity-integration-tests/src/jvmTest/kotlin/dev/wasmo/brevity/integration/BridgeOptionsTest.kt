package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeOptionsTest {
  @Test
  fun test() = runTest {
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
}
