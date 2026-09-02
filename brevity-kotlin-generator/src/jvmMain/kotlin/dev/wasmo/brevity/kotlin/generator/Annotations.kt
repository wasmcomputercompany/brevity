package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import dev.wasmo.brevity.FunctionName

internal val FunctionName.wasmImportAnnotation: AnnotationSpec
  get() = AnnotationSpec.builder(Symbols.KotlinWasm.WasmImport)
    .apply {
      val moduleName = moduleName
      if (moduleName != null) {
        addMember("module = %S", moduleName)
        addMember("name = %S", abiName)
      } else {
        addMember("module = %S", abiName)
      }
    }
    .build()

internal val FunctionName.wasmExportAnnotation: AnnotationSpec
  get() = AnnotationSpec.builder(Symbols.KotlinWasm.WasmExport)
    .addMember("%S", toString())
    .build()
