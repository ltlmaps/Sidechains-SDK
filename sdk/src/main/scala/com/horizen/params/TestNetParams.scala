package com.horizen.params
import java.math.BigInteger

import com.horizen.proposition.SchnorrPublicKey
import scorex.core.block.Block
import scorex.util.ModifierId
import scorex.util.bytesToId

case class TestNetParams(
                          override val sidechainId: Array[Byte] = new Array[Byte](32),
                          override val sidechainGenesisBlockId: ModifierId = bytesToId(new Array[Byte](32)),
                          override val genesisMainchainBlockHash: Array[Byte] = new Array[Byte](32),
                          override val genesisPoWData: Seq[(Int, Int)] = Seq(),
                          override val mainchainCreationBlockHeight: Int = 1,
                          override val withdrawalEpochLength: Int = 100,
                          override val sidechainGenesisBlockTimestamp: Block.Timestamp = 720 * 120,
                          override val consensusSecondsInSlot: Int = 120,
                          override val consensusSlotsInEpoch: Int = 720,
                          override val schnorrPublicKeys: Seq[SchnorrPublicKey] = Seq(),
                          override val backwardTransferThreshold: Int = 0,
                          override val provingKeyFilePath: String = ""
                        ) extends NetworkParams {
  override val EquihashN: Int = 200
  override val EquihashK: Int = 9
  override val EquihashVarIntLength: Int = 3
  override val EquihashSolutionLength: Int = 1344

  override val powLimit: BigInteger = new BigInteger("07ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
  override val nPowAveragingWindow: Int = 17
  override val nPowMaxAdjustDown: Int = 32 // 32% adjustment down
  override val nPowMaxAdjustUp: Int = 16 // 16% adjustment up
  override val nPowTargetSpacing: Int = 150 // 2.5 * 60
}
