package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

/**
 * Encode a [List], [ByteArray], [IntArray], etc., whose length is not known at build time.
 */
class DynamicListEncoder(
  private val elementEncoder: Encoder,
  private val listType: KtTypeName,
) : Encoder() {
  override val type: TypeName
    get() = TypeName.List(elementEncoder.type)
  override val coreTypes = listOf(CoreType.Pointer, CoreType.Pointer)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("address"), Identifier("size"))

  override val byteCount: Int
    get() = 2 * CoreType.Pointer.byteCount

  override val alignment: Int
    get() = CoreType.Pointer.alignment

  override val lowerAllocates: Boolean
    get() = true

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return loadList(
      address = codeBuilder.platform.load(baseAddress, offset, CoreType.Pointer),
      length = codeBuilder.platform.load(
        baseAddress,
        offset + CoreType.Pointer.byteCount,
        CoreType.Pointer,
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val (address, length) = storeList(memoryAllocator!!, value)
    codeBuilder.platform.store(baseAddress, offset, CoreType.Pointer, address)
    codeBuilder.platform.store(
      baseAddress,
      offset + CoreType.Pointer.byteCount,
      CoreType.Pointer,
      length,
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(loadList(transformer.take(), transformer.take()))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    val value = transformer.take()
    val (address, length) = storeList(memoryAllocator!!, value)
    transformer.put(address)
    transformer.put(length)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  private fun loadList(
    address: CodeBlock,
    length: CodeBlock,
  ): CodeBlock {
    val addressName = codeBuilder.newName("listAddress")
    val elementAddressName = codeBuilder.newName("elementAddress")
    val lengthName = codeBuilder.newName("length")

    codeBuilder.addStatement(
      "val %N = %L",
      addressName,
      codeBuilder.platform.liftAddress(address),
    )
    codeBuilder.addStatement(
      "val %N = %L",
      lengthName,
      length,
    )

    val listName = codeBuilder.newName("list")
    val indexName = codeBuilder.newName("i")
    codeBuilder.controlFlow(
      "val %N = %T(%L) { %N ->",
      listName,
      listType,
      lengthName,
      indexName,
    ) {
      codeBuilder.addStatement(
        "val %N = %N + %N * %L",
        elementAddressName,
        addressName,
        indexName,
        elementEncoder.byteCount,
      )
      codeBuilder.addStatement(
        "%L",
        context.load(
          encoder = elementEncoder,
          baseAddress = CodeBlock.of("%N", elementAddressName),
        ),
      )
    }
    return CodeBlock.of("%N", listName)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  private fun storeList(
    memoryAllocator: MemoryAllocator,
    list: CodeBlock,
  ): Pair<CodeBlock, CodeBlock> {
    val addressName = codeBuilder.newName("listAddress")
    val elementAddressName = codeBuilder.newName("elementAddress")
    val indexName = codeBuilder.newName("i")

    codeBuilder.addStatement(
      "val %N = %L",
      addressName,
      memoryAllocator.allocate("%L.size * %L", list, elementEncoder.byteCount),
    )
    codeBuilder.controlFlow(
      "for (%N in %L.indices)",
      indexName,
      list,
    ) {
      codeBuilder.addStatement(
        "val %N = %N + %N * %L",
        elementAddressName,
        addressName,
        indexName,
        elementEncoder.byteCount,
      )
      context.store(
        encoder = elementEncoder,
        memoryAllocator = memoryAllocator,
        baseAddress = CodeBlock.of("%N", elementAddressName),
        value = CodeBlock.of("%L[%N]", list, indexName),
      )
    }

    return codeBuilder.platform.lowerAddress(
      CodeBlock.of(
        "%N",
        addressName,
      ),
    ) to CodeBlock.of("%L.size", list)
  }
}
