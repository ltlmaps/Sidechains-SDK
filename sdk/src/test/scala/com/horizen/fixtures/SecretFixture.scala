package com.horizen.fixtures

import com.horizen.secret._
import com.horizen.customtypes._

import java.util.{List => JList, ArrayList => JArrayList}
import scala.util.Random

trait SecretFixture {
  val pkc = PrivateKey25519Creator.getInstance()

  val pk1 = pkc.generateSecret("seed1".getBytes())
  val pk2 = pkc.generateSecret("seed2".getBytes())
  val pk3 = pkc.generateSecret("seed3".getBytes())
  val pk4 = pkc.generateSecret("seed4".getBytes())
  val pk5 = pkc.generateSecret("seed5".getBytes())
  val pk6 = pkc.generateSecret("seed6".getBytes())

  val pk7 = pkc.generateSecret("seed7".getBytes())

  def getSecret() : Secret = {
    val seed = new Array[Byte](32)
    Random.nextBytes(seed)
    pkc.generateSecret(seed)
  }

  def getSecret(seed: Array[Byte]) : Secret = {
    pkc.generateSecret(seed)
  }

  def getSecretList(count : Int) : JList[Secret] = {
    val seed = new Array[Byte](32)
    val keysList : JList[Secret] = new JArrayList[Secret]()
    for (i <- 1 to count) {
      Random.nextBytes(seed)
      keysList.add(pkc.generateSecret(seed))
    }
    keysList
  }

  def getCustomSecret() : Secret = {
    val privateBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)

    Random.nextBytes(privateBytes)
    Random.nextBytes(publicBytes)

    new CustomPrivateKey(privateBytes, publicBytes)
  }

  def getCustomSecretList(count : Int) : JList[Secret] = {
    val privateBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)
    val publicBytes = new Array[Byte](CustomPrivateKey.KEY_LENGTH)
    val keysList : JList[Secret] = new JArrayList[Secret]()

    for (i <- 1 to count) {
      Random.nextBytes(privateBytes)
      Random.nextBytes(publicBytes)

      keysList.add(new CustomPrivateKey(privateBytes, publicBytes))
    }

    keysList
  }
}

class SecretFixtureClass extends SecretFixture