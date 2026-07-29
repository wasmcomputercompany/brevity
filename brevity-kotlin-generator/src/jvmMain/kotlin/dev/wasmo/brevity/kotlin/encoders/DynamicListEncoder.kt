package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.Identifier

/**
 * Encode a [List], [ByteArray], [IntArray], etc., whose length is not known at build time.
 */
class DynamicListEncoder(
  private val elementEncoder: Encoder,
  private val listType: TypeName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.Pointer, CoreType.Pointer)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("address"), Identifier("size"))

  override val byteCount: Int
    get() = 2 * CoreType.Pointer.byteCount

  override val alignment: Int
    get() = CoreType.Pointer.alignment

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return loadList(
      address = platform.load(baseAddress, offset, CoreType.Pointer),
      length = platform.load(baseAddress, offset + CoreType.Pointer.byteCount, CoreType.Pointer),
    )
  }

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val (address, length) = storeList(value)
    platform.store(baseAddress, offset, CoreType.Pointer, address)
    platform.store(baseAddress, offset + CoreType.Pointer.byteCount, CoreType.Pointer, length)
  }

  override fun FlatEncoder.liftFlat() {
    put(loadList(take(), take()))
  }

  override fun FlatEncoder.lowerFlat() {
    val value = take()
    val (address, length) = storeList(value)
    put(address)
    put(length)
  }

  private fun BridgeBuilder.loadList(
    address: CodeBlock,
    length: CodeBlock,
  ): CodeBlock {
    val addressName = nameAllocator.newName("listAddress")
    val elementAddressName = nameAllocator.newName("elementAddress")
    val lengthName = nameAllocator.newName("length")

    code.addStatement(
      "val %N = %L",
      addressName,
      platform.liftAddress(address),
    )
    code.addStatement(
      "val %N = %L",
      lengthName,
      length,
    )

    val listName = nameAllocator.newName("list")
    val indexName = nameAllocator.newName("i")
    code.beginControlFlow(
      "val %N = %T(%L) { %N ->",
      listName,
      listType,
      lengthName,
      indexName,
    )
    with(elementEncoder) {
      code.addStatement("val %N = %N + %N * %L",
        elementAddressName,
        addressName,
        indexName,
        elementEncoder.byteCount,
      )
      code.addStatement(
        "%L",
        load(
          baseAddress = CodeBlock.of("%N", elementAddressName),
          offset = 0,
        ),
      )
    }
    code.endControlFlow()
    return CodeBlock.of("%N", listName)
  }

  private fun BridgeBuilder.storeList(
    list: CodeBlock,
  ): Pair<CodeBlock, CodeBlock> {
    val addressName = nameAllocator.newName("listAddress")
    val elementAddressName = nameAllocator.newName("elementAddress")
    val indexName = nameAllocator.newName("i")

    code.addStatement(
      "val %N = %L",
      addressName,
      allocate(CodeBlock.of("%L.size * %L", list, elementEncoder.byteCount)),
    )
    code.beginControlFlow(
      "for (%N in %L.indices)",
      indexName,
      list,
    )
    with(elementEncoder) {
      code.addStatement("val %N = %N + %N * %L",
        elementAddressName,
        addressName,
        indexName,
        elementEncoder.byteCount,
      )
      store(
        CodeBlock.of("%N", elementAddressName),
        0,
        CodeBlock.of("%L[%N]", list, indexName),
      )
    }
    code.endControlFlow()

    return platform.lowerAddress(CodeBlock.of("%N", addressName)) to CodeBlock.of("%L.size", list)
  }
}
