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
      ),
    )

    test.execute()
  }
}
