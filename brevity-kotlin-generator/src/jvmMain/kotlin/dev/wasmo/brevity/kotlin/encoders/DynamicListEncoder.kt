package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.code.CodeBuilder

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

  context(codeBuilder: CodeBuilder)
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

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val (address, length) = storeList(value)
    codeBuilder.platform.store(baseAddress, offset, CoreType.Pointer, address)
    codeBuilder.platform.store(
      baseAddress,
      offset + CoreType.Pointer.byteCount,
      CoreType.Pointer,
      length,
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(flatBuilder: FlatBuilder) {
    flatBuilder.put(loadList(flatBuilder.take(), flatBuilder.take()))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(flatBuilder: FlatBuilder) {
    val value = flatBuilder.take()
    val (address, length) = storeList(value)
    flatBuilder.put(address)
    flatBuilder.put(length)
  }

  context(codeBuilder: CodeBuilder)
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
      with(elementEncoder) {
        codeBuilder.addStatement(
          "val %N = %N + %N * %L",
          elementAddressName,
          addressName,
          indexName,
          elementEncoder.byteCount,
        )
        codeBuilder.addStatement(
          "%L",
          load(baseAddress = CodeBlock.of("%N", elementAddressName)),
        )
      }
    }
    return CodeBlock.of("%N", listName)
  }

  context(codeBuilder: CodeBuilder)
  private fun storeList(
    list: CodeBlock,
  ): Pair<CodeBlock, CodeBlock> {
    val addressName = codeBuilder.newName("listAddress")
    val elementAddressName = codeBuilder.newName("elementAddress")
    val indexName = codeBuilder.newName("i")

    codeBuilder.addStatement(
      "val %N = %L",
      addressName,
      codeBuilder.allocate(CodeBlock.of("%L.size * %L", list, elementEncoder.byteCount)),
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
      elementEncoder.store(
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
