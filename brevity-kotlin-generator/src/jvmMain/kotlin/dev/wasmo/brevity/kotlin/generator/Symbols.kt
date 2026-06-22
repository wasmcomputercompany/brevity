package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName

object Symbols {
  object Kotlin {
    val Boolean = ClassName("kotlin", "Boolean")
    val Byte = ClassName("kotlin", "Byte")
    val Double = ClassName("kotlin", "Double")
    val Float = ClassName("kotlin", "Float")
    val Int = ClassName("kotlin", "Int")
    val Long = ClassName("kotlin", "Long")
    val Pair = ClassName("kotlin", "Pair")
    val Short = ClassName("kotlin", "Short")
    val String = ClassName("kotlin", "String")
    val Triple = ClassName("kotlin", "Triple")
    val UByte = ClassName("kotlin", "UByte")
    val UInt = ClassName("kotlin", "UInt")
    val ULong = ClassName("kotlin", "ULong")
    val UShort = ClassName("kotlin", "UShort")
  }

  object KotlinCollections {
    val List = ClassName("kotlin.collections", "List")
    val Map = ClassName("kotlin.collections", "Map")
  }

  object KotlinCoroutines {
    val Deferred = ClassName("kotlinx.coroutines", "Deferred")
  }

  object KotlinWasm {
    val WasmExport = ClassName("kotlin.wasm", "WasmExport")
  }

  object ChicoryRuntime {
    val ExportFunction = ClassName("com.dylibso.chicory.runtime", "ExportFunction")
    val Instance = ClassName("com.dylibso.chicory.runtime", "Instance")
    val Store = ClassName("com.dylibso.chicory.runtime", "Store")
  }

  object Brevity {
    val Borrow = ClassName("dev.wasmo.brevity", "Borrow")
    val Bridge = ClassName("dev.wasmo.brevity", "Bridge")
    val Quad = ClassName("dev.wasmo.brevity", "Quad")
    val Resource = ClassName("dev.wasmo.brevity", "Resource")
    val Stream = ClassName("dev.wasmo.brevity", "Stream")
    val World = ClassName("dev.wasmo.brevity", "World")
  }
}
