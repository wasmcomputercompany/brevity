package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
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
}
