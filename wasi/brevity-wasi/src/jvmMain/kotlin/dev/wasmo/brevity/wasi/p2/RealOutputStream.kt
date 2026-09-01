package dev.wasmo.brevity.wasi.p2

import dev.wasmo.brevity.Result
import java.io.IOException
import java.io.OutputStream
import okio.ByteString
import wit.wasi.io.v0_2_12.Error
import wit.wasi.io.v0_2_12.Poll
import wit.wasi.io.v0_2_12.Streams

class RealOutputStream(
  val delegate: OutputStream,
) : Streams.OutputStream {
  override fun checkWrite(): Result<ULong, Streams.StreamError> {
    return Result.Ok(1024UL * 1024UL)
  }

  override fun write(contents: ByteString): Result<*, Streams.StreamError> {
    try {
      delegate.write(contents.toByteArray())
      return Result.Ok(Unit)
    } catch (e: IOException) {
      return e.asStreamErrorResult()
    }
  }

  override fun blockingWriteAndFlush(contents: ByteString): Result<*, Streams.StreamError> {
    TODO("Not yet implemented")
  }

  override fun flush(): Result<*, Streams.StreamError> {
    TODO("Not yet implemented")
  }

  override fun blockingFlush(): Result<*, Streams.StreamError> {
    return Result.Ok(Unit)
  }

  override fun subscribe(): Poll.Pollable {
    return RealPollable()
  }

  override fun writeZeroes(len: ULong): Result<*, Streams.StreamError> {
    TODO("Not yet implemented")
  }

  override fun blockingWriteZeroesAndFlush(len: ULong): Result<*, Streams.StreamError> {
    TODO("Not yet implemented")
  }

  override fun splice(
    src: Streams.InputStream,
    len: ULong,
  ): Result<ULong, Streams.StreamError> {
    TODO("Not yet implemented")
  }

  override fun blockingSplice(
    src: Streams.InputStream,
    len: ULong,
  ): Result<ULong, Streams.StreamError> {
    TODO("Not yet implemented")
  }
}

internal fun IOException.asStreamErrorResult(): Result.Error<Unit, Streams.StreamError> {
  val error = object : Error.Error {
    override fun toDebugString() = this@asStreamErrorResult.stackTraceToString()
  }
  return Result.Error<Unit, Streams.StreamError>(
    Streams.StreamError.LastOperationFailed(error),
  )
}
