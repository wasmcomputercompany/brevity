package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object Symbols {
  object Kotlin {
    val Boolean = ClassName("kotlin", "Boolean")
    val Byte = ClassName("kotlin", "Byte")
    val Double = ClassName("kotlin", "Double")
    val EncodeToByteArray = MemberName("kotlin.text", "encodeToByteArray")
    val ExperimentalUnsignedTypes = ClassName("kotlin", "ExperimentalUnsignedTypes")
    val Float = ClassName("kotlin", "Float")
    val Int = ClassName("kotlin", "Int")
    val Long = ClassName("kotlin", "Long")
    val OptIn = ClassName("kotlin", "OptIn")
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
    val ComponentModelInternalApi = ClassName("kotlin.wasm.unsafe", "ComponentModelInternalApi")
    val ExperimentalWasmInterop = ClassName("kotlin.wasm", "ExperimentalWasmInterop")
    val FreeAllComponentModelReallocAllocatedMemory = MemberName(
      "kotlin.wasm.unsafe",
      "freeAllComponentModelReallocAllocatedMemory"
    )
    val Pointer = ClassName("kotlin.wasm.unsafe", "Pointer")
    val UnsafeWasmMemoryApi = ClassName("kotlin.wasm.unsafe", "UnsafeWasmMemoryApi")
    val WasmExport = ClassName("kotlin.wasm", "WasmExport")
    val WasmImport = ClassName("kotlin.wasm", "WasmImport")
    val WithScopedMemoryAllocator = MemberName("kotlin.wasm.unsafe", "withScopedMemoryAllocator")
  }

  object ChicoryRuntime {
    val ExportFunction = ClassName("com.dylibso.chicory.runtime", "ExportFunction")
    val FunctionType = ClassName("com.dylibso.chicory.wasm.types", "FunctionType")
    val HostFunction = ClassName("com.dylibso.chicory.runtime", "HostFunction")
    val Instance = ClassName("com.dylibso.chicory.runtime", "Instance")
    val Store = ClassName("com.dylibso.chicory.runtime", "Store")
    val ValType = ClassName("com.dylibso.chicory.wasm.types", "ValType")
    val WasmFunctionHandle = ClassName("com.dylibso.chicory.runtime", "WasmFunctionHandle")
  }

  object Brevity {
    val Borrow = ClassName("dev.wasmo.brevity", "Borrow")
    val GuestBridge = ClassName("dev.wasmo.brevity", "GuestBridge")
    val HostBridge = ClassName("dev.wasmo.brevity", "HostBridge")
    val HostBridgeGet = MemberName("dev.wasmo.brevity", "get")
    val LoadPointer = MemberName("dev.wasmo.brevity", "loadPointer")
    val LoadString = MemberName("dev.wasmo.brevity", "loadString")
    val Quad = ClassName("dev.wasmo.brevity", "Quad")
    val Resource = ClassName("dev.wasmo.brevity", "Resource")
    val StoreByteArray = MemberName("dev.wasmo.brevity", "storeByteArray")
    val Stream = ClassName("dev.wasmo.brevity", "Stream")
    val World = ClassName("dev.wasmo.brevity", "World")
  }
}
