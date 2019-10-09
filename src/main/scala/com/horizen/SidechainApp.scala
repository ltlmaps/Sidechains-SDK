package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}
import java.io.{File => JFile}
import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.http.scaladsl.server.ExceptionHandler
import com.horizen.api.http._
import com.horizen.block.{SidechainBlock, SidechainBlockSerializer}
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.forge.ForgerRef
import com.horizen.params.{MainNetParams, StorageParams}
import com.horizen.secret.SecretSerializer
import com.horizen.state.{ApplicationState, DefaultApplicationState}
import com.horizen.storage._
import com.horizen.transaction.TransactionSerializer
import com.horizen.validation.{MainchainPoWValidator, SidechainBlockValidator}
import com.horizen.wallet.{ApplicationWallet, DefaultApplicationWallet}
import io.iohk.iodb.LSMStore
import scorex.core.api.http.ApiRoute
import scorex.core.app.Application
import scorex.core.network.message.MessageSpec
import scorex.core.network.{NodeViewSynchronizerRef, PeerFeature}
import scorex.core.serialization.{ScorexSerializer, SerializerRegistry}
import scorex.core.settings.ScorexSettings
import scorex.util.{ModifierId, ScorexLogging}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.horizen.forge.ForgerRef
import com.horizen.websocket.{DefaultWebSocketReconnectionHandler, DisconnectionCode, WebSocketChannel, WebSocketCommunicationClient, WebSocketConnector, WebSocketConnectorConfiguration, WebSocketConnectorImpl, WebSocketMessageHandler, WebSocketReconnectionHandler}
import scorex.core.transaction.Transaction
import scorex.core.{ModifierTypeId, NodeViewModifier}
import scorex.util.{ModifierId, ScorexLogging}

import scala.collection.mutable
import scala.io.Source
import scala.util.Try
import scala.concurrent.duration._
import scala.collection.immutable.Map

class SidechainApp(val settingsFilename: String)
  extends Application
  with ScorexLogging
{
  override type TX = SidechainTypes#SCBT
  override type PMOD = SidechainBlock
  override type NVHT = SidechainNodeViewHolder

  private val sidechainSettings = SidechainSettings.read(Some(settingsFilename))
  override implicit lazy val settings: ScorexSettings = SidechainSettings.read(Some(settingsFilename)).scorexSettings

  private val storageList = mutable.ListBuffer[Storage]()

  System.out.println(s"Starting application with settings \n$sidechainSettings")
  log.debug(s"Starting application with settings \n$sidechainSettings")

  override implicit def exceptionHandler: ExceptionHandler = SidechainApiErrorHandler.exceptionHandler
  override protected lazy val features: Seq[PeerFeature] = Seq()

  override protected lazy val additionalMessageSpecs: Seq[MessageSpec[_]] = Seq(SidechainSyncInfoMessageSpec)

  protected val sidechainBoxesCompanion: SidechainBoxesCompanion =  SidechainBoxesCompanion(new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]())
  protected val sidechainSecretsCompanion: SidechainSecretsCompanion = SidechainSecretsCompanion(new JHashMap[JByte, SecretSerializer[SidechainTypes#SCS]]())
  protected val sidechainTransactionsCompanion: SidechainTransactionsCompanion = SidechainTransactionsCompanion(new JHashMap[JByte, TransactionSerializer[SidechainTypes#SCBT]]())
  protected val defaultApplicationWallet: ApplicationWallet = new DefaultApplicationWallet()
  protected val defaultApplicationState: ApplicationState = new DefaultApplicationState()

  val mainNetParams = new MainNetParams()

  case class CustomParams(override val sidechainGenesisBlockId: ModifierId) extends MainNetParams {

  }
  val params: CustomParams = CustomParams(sidechainSettings.genesisBlock.get.id)

  protected val sidechainSecretStorage = new SidechainSecretStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/secret")),
    sidechainSecretsCompanion)
  protected val sidechainWalletBoxStorage = new SidechainWalletBoxStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/wallet")),
    sidechainBoxesCompanion)
  protected val sidechainStateStorage = new SidechainStateStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/state")),
    sidechainBoxesCompanion)
  protected val sidechainHistoryStorage = new SidechainHistoryStorage(
    openStorage(new JFile(s"${sidechainSettings.scorexSettings.dataDir.getAbsolutePath}/history")),
    sidechainTransactionsCompanion, params)

  //TODO remove these test settings
  if(sidechainSettings.scorexSettings.network.nodeName.equals("testNode1")) {
    sidechainSecretStorage.add(sidechainSettings.targetSecretKey1)
    sidechainSecretStorage.add(sidechainSettings.targetSecretKey2)
  }


  override val nodeViewHolderRef: ActorRef = SidechainNodeViewHolderRef(sidechainSettings, sidechainHistoryStorage,
    sidechainStateStorage,
    "test seed %s".format(sidechainSettings.scorexSettings.network.nodeName).getBytes(), // To Do: add Wallet group to config file => wallet.seed
    sidechainWalletBoxStorage, sidechainSecretStorage, params, timeProvider,
    defaultApplicationWallet, defaultApplicationState, sidechainSettings.genesisBlock.get,
    Seq(new SidechainBlockValidator(params), new MainchainPoWValidator(sidechainHistoryStorage, params))
  )


  def modifierSerializers: Map[ModifierTypeId, ScorexSerializer[_ <: NodeViewModifier]] =
    Map(SidechainBlock.ModifierTypeId -> new SidechainBlockSerializer(sidechainTransactionsCompanion),
      Transaction.ModifierTypeId -> sidechainTransactionsCompanion)

  override val nodeViewSynchronizer: ActorRef =
    actorSystem.actorOf(NodeViewSynchronizerRef.props[SidechainTypes#SCBT, SidechainSyncInfo, SidechainSyncInfoMessageSpec.type,
      SidechainBlock, SidechainHistory, SidechainMemoryPool]
      (networkControllerRef, nodeViewHolderRef,
        SidechainSyncInfoMessageSpec, settings.network, timeProvider,
        modifierSerializers
      ))

  val sidechainTransactioActorRef : ActorRef = SidechainTransactionActorRef(nodeViewHolderRef)
  val sidechainBlockForgerActorRef : ActorRef = ForgerRef(sidechainSettings, nodeViewHolderRef, sidechainTransactionsCompanion, params)
  val sidechainBlockActorRef : ActorRef = SidechainBlockActorRef(sidechainSettings, nodeViewHolderRef, sidechainBlockForgerActorRef)

  implicit val serializerReg: SerializerRegistry = SerializerRegistry(Seq())

  override val apiRoutes: Seq[ApiRoute] = Seq[ApiRoute](
    MainchainBlockApiRoute(settings.restApi, nodeViewHolderRef, mainNetParams),
    SidechainBlockApiRoute(settings.restApi, nodeViewHolderRef, sidechainBlockActorRef, sidechainBlockForgerActorRef),
    SidechainNodeApiRoute(peerManagerRef, networkControllerRef, timeProvider, settings.restApi, nodeViewHolderRef),
    SidechainTransactionApiRoute(settings.restApi, nodeViewHolderRef, sidechainTransactioActorRef),
    SidechainUtilsApiRoute(settings.restApi, nodeViewHolderRef),
    SidechainWalletApiRoute(settings.restApi, nodeViewHolderRef)
  )

  override val swaggerConfig: String = ""

  override def stopAll(): Unit = {
    super.stopAll()
    storageList.foreach(_.close())
  }

  //TODO additional initialization (see HybridApp)
  private def openStorage(storagePath: JFile) : Storage = {
    storagePath.mkdirs()
    val storage = new IODBStoreAdapter(new LSMStore(storagePath, StorageParams.storageKeySize))
    storageList += storage
    storage
  }

  // Note: ignore this at the moment
  // waiting WS client interface
  private def setupMainchainConnection  = ???

  // waiting WS client interface
  private def getMainchainConnectionInfo  = ???

  // retrieve information for using a web socket connector
  val webSocketConfiguration : WebSocketConnectorConfiguration = new WebSocketConnectorConfiguration(
    bindAddress = "ws://localhost:8888",
    connectionTimeout = 100 milliseconds,
    reconnectionDelay = 1 seconds,
    reconnectionMaxAttempts = 1)
  val webSocketMessageHandler : WebSocketMessageHandler = new WebSocketCommunicationClient()
  val webSocketReconnectionHandler : WebSocketReconnectionHandler = new DefaultWebSocketReconnectionHandler(webSocketConfiguration)

  // create the cweb socket connector and configure it
  val webSocketConnector : WebSocketConnector[_ <: WebSocketChannel] = new WebSocketConnectorImpl(
    webSocketConfiguration, webSocketMessageHandler, webSocketReconnectionHandler
  )

  // start the web socket connector
  val channel : Try[WebSocketChannel] = webSocketConnector.start()

  // if the web socket connector can be started, maybe we would to associate a client to the web socket channel created by the connector
  if(channel.isSuccess)
    {
      val communicationClient : WebSocketCommunicationClient = webSocketMessageHandler.asInstanceOf[WebSocketCommunicationClient]
      communicationClient.setWebSocketChannel(channel.get)
    }

}

object SidechainApp /*extends App*/ {
  def main(args: Array[String]) : Unit = {
    val settingsFilename = args.headOption.getOrElse("src/main/resources/settings.conf")
    val app = new SidechainApp(settingsFilename)
    app.run()
    app.log.info("Sidechain application successfully started...")
  }
}
