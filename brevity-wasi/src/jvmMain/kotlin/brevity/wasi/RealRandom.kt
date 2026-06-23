package brevity.wasi

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.random.nextUBytes
import kotlin.random.nextULong
import wit.wasi.random.v0_3_0.Insecure
import wit.wasi.random.v0_3_0.InsecureSeed
import wit.wasi.random.v0_3_0.Random as wasiRandom

class RealRandom : wasiRandom {
  val secureRandom = SecureRandom().asKotlinRandom()

  override fun getRandomBytes(maxLen: ULong): UByteArray {
    return secureRandom.nextUBytes(maxLen.toInt())
  }

  override fun getRandomU64(): ULong {
    return secureRandom.nextULong()
  }
}

class RealInsecureRandom : Insecure {
  override fun getInsecureRandomBytes(maxLen: ULong): UByteArray {
    return Random.nextUBytes(maxLen.toInt())
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
