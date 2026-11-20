package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import dev.wasmo.brevity.TypeName.Stream.Cardinality

object Symbols {
  object Kotlin {
    val Boolean = ClassName("kotlin", "Boolean")
    val BooleanArray = ClassName("kotlin", "BooleanArray")
    val Byte = ClassName("kotlin", "Byte")
    val ByteArray = ClassName("kotlin", "ByteArray")
    val Double = ClassName("kotlin", "Double")
    val DoubleArray = ClassName("kotlin", "DoubleArray")
    val EncodeToByteArray = MemberName("kotlin.text", "encodeToByteArray")
    val ExperimentalUnsignedTypes = ClassName("kotlin", "ExperimentalUnsignedTypes")
    val Float = ClassName("kotlin", "Float")
    val FloatArray = ClassName("kotlin", "FloatArray")
    val Int = ClassName("kotlin", "Int")
    val IntArray = ClassName("kotlin", "IntArray")
    val Long = ClassName("kotlin", "Long")
    val LongArray = ClassName("kotlin", "LongArray")
    val OptIn = ClassName("kotlin", "OptIn")
    val Pair = ClassName("kotlin", "Pair")
    val Short = ClassName("kotlin", "Short")
    val ShortArray = ClassName("kotlin", "ShortArray")
    val String = ClassName("kotlin", "String")
    val Triple = ClassName("kotlin", "Triple")
    val UByte = ClassName("kotlin", "UByte")
    val UByteArray = ClassName("kotlin", "UByteArray")
    val UInt = ClassName("kotlin", "UInt")
    val UIntArray = ClassName("kotlin", "UIntArray")
    val ULong = ClassName("kotlin", "ULong")
    val ULongArray = ClassName("kotlin", "ULongArray")
    val UShort = ClassName("kotlin", "UShort")
    val UShortArray = ClassName("kotlin", "UShortArray")
  }

  object KotlinCollections {
    val List = ClassName("kotlin.collections", "List")
    val ListOf = MemberName("kotlin.collections", "listOf")
    val Map = ClassName("kotlin.collections", "Map")
  }

  object KotlinWasm {
    val ComponentModelInternalApi = ClassName("kotlin.wasm.unsafe", "ComponentModelInternalApi")
    val ExperimentalWasmInterop = ClassName("kotlin.wasm", "ExperimentalWasmInterop")
    val FreeAllComponentModelReallocAllocatedMemory = MemberName(
      "kotlin.wasm.unsafe",
      "freeAllComponentModelReallocAllocatedMemory",
    )
    val MemoryAllocator = ClassName("kotlin.wasm.unsafe", "MemoryAllocator")
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

  object Okio {
    val ByteString = ClassName("okio", "ByteString")
    val ByteStringToByteString = MemberName(ByteString.nestedClass("Companion"), "toByteString")
  }

  object Brevity {
    val BrevityInternalApi = ClassName("dev.wasmo.brevity", "BrevityInternalApi")
    val CallBuilder = ClassName("dev.wasmo.brevity", "CallBuilder")
    val CallbackCode = ClassName("dev.wasmo.brevity", "CallbackCode")
    val CurrentTask = MemberName("dev.wasmo.brevity", "currentTask")
    val Event = ClassName("dev.wasmo.brevity", "Event")
    val Future = ClassName("dev.wasmo.brevity", "Future")
    val FutureReceiver = ClassName("dev.wasmo.brevity", "Future", "Receiver")
    val FutureSender = ClassName("dev.wasmo.brevity", "Future", "Sender")
    val GuestBridge = ClassName("dev.wasmo.brevity", "GuestBridge")
    val HostBridge = ClassName("dev.wasmo.brevity", "HostBridge")
    val LoadString = MemberName("dev.wasmo.brevity", "loadString")
    val MemoryAllocator = ClassName("dev.wasmo.brevity", "MemoryAllocator")
    val PackedAsyncResult = ClassName("dev.wasmo.brevity", "PackedAsyncResult")
    val PackedSubtaskResult = ClassName("dev.wasmo.brevity", "PackedSubtaskResult")
    val Quad = ClassName("dev.wasmo.brevity", "Quad")
    val Resource = ClassName("dev.wasmo.brevity", "Resource")
    val Result = ClassName("dev.wasmo.brevity", "Result")
    val ResultError = Result.nestedClass("Error")
    val ResultOk = Result.nestedClass("Ok")
    val RootTask = ClassName("dev.wasmo.brevity", "RootTask")
    val StoreByteArray = MemberName("dev.wasmo.brevity", "storeByteArray")
    val Stream = ClassName("dev.wasmo.brevity", "Stream")
    val StreamBinding = ClassName("dev.wasmo.brevity", "StreamBinding")
    val StreamReceiver = ClassName("dev.wasmo.brevity", "Stream", "Receiver")
    val StreamSender = ClassName("dev.wasmo.brevity", "Stream", "Sender")
    val WitAdapter = ClassName("dev.wasmo.brevity", "WitAdapter")
    val World = ClassName("dev.wasmo.brevity", "World")

    fun stream(cardinality: Cardinality): ClassName {
      return when (cardinality) {
        Cardinality.Future -> Future
        Cardinality.Stream -> Stream
      }
    }

    fun streamReceiver(cardinality: Cardinality): ClassName {
      return when (cardinality) {
        Cardinality.Future -> FutureReceiver
        Cardinality.Stream -> StreamReceiver
      }
    }

    fun streamSender(cardinality: Cardinality): ClassName {
      return when (cardinality) {
        Cardinality.Future -> FutureSender
        Cardinality.Stream -> StreamSender
      }
    }

    fun streamBindingType(cardinality: Cardinality): ClassName {
      return when (cardinality) {
        Cardinality.Future -> StreamBinding
        Cardinality.Stream -> StreamBinding
      }
    }
  }
}
