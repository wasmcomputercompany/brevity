package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.TypeName

val TypeName.placeholder: CodeBlock
  get() = when (this) {
    INT -> CodeBlock.of("%L", 0)
    LONG -> CodeBlock.of("%LL", 0)
    FLOAT -> CodeBlock.of("%Lf", 0.0)
    DOUBLE -> CodeBlock.of("%L", 0.0)
    else -> error("unexpected core type")
  }
