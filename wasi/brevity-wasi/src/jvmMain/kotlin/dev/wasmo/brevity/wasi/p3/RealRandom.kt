package dev.wasmo.brevity.wasi.p3

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.random.nextULong
import okio.ByteString
import okio.ByteString.Companion.toByteString
import wit.wasi.random.v0_3_1.Insecure
import wit.wasi.random.v0_3_1.InsecureSeed
import wit.wasi.random.v0_3_1.Random as WasiRandom

class RealRandom : WasiRandom {
  val secureRandom = SecureRandom().asKotlinRandom()

  override fun getRandomBytes(maxLen: ULong): ByteString {
    return secureRandom.nextBytes(maxLen.toInt()).toByteString()
  }

  override fun getRandomU64(): ULong {
    return secureRandom.nextULong()
  }
}

class RealInsecureRandom : Insecure {
  override fun getInsecureRandomBytes(maxLen: ULong): ByteString {
    return Random.nextBytes(maxLen.toInt()).toByteString()
  }

  override fun getInsecureRandomU64(): ULong {
    return Random.nextULong()
  }

}

class RealInsecureSeed : InsecureSeed {
  override fun getInsecureSeed(): Pair<ULong, ULong> {
    return Pair(Random.nextULong(), Random.nextULong())
  }
}
