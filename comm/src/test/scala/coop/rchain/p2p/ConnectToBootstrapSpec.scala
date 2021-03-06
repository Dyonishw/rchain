package coop.rchain.p2p

import org.scalatest._
import coop.rchain.comm.protocol.rchain._
import com.google.common.io.BaseEncoding
import coop.rchain.comm._, CommError._, NetworkProtocol._, Network.defaultTimeout
import coop.rchain.p2p.effects._
import cats._, cats.data._, cats.implicits._
import coop.rchain.catscontrib._, Catscontrib._, ski._, Encryption._

import EffectsTestInstances._

class ConnectToBootstrapSpec
    extends FunSpec
    with Matchers
    with BeforeAndAfterEach
    with AppendedClues {

  val encoder = BaseEncoding.base16().lowerCase()

  val src: ProtocolNode = protocolNode("src", 30300)
  val remote: PeerNode  = peerNode("remote", 30301)
  val srcKeys           = PublicPrivateKeys(encoder.decode("ff00ff00"), encoder.decode("cc00cc00"))
  val remoteKeys        = PublicPrivateKeys(encoder.decode("ee00ee00"), encoder.decode("dd00dd00"))
  val nonce             = encoder.decode("00112233")

  type Effect[A] = CommErrT[Id, A]

  implicit val logEff           = new LogStub[Effect]
  implicit val timeEff          = new LogicalTime[Effect]
  implicit val metricEff        = new Metrics.MetricsNOP[Effect]
  implicit val communicationEff = new CommunicationStub[Effect](src)
  implicit val encryptionEff    = new EncryptionStub[Effect](srcKeys, nonce)
  implicit val keysStoreEff     = new Kvs.InMemoryKvs[Effect, PeerNode, Key]

  override def beforeEach(): Unit = {
    logEff.reset()
    communicationEff.reset()
    encryptionEff.reset()
    keysStoreEff.keys.map(_.map(k => keysStoreEff.delete(k)))
  }

  describe("Node when connecting to bootstrap") {
    it("should run attempts before it exits") {
      // given
      communicationEff.setResponses(kp(failEverything))
      // when
      val result = Network.connectToBootstrap[Effect](remote.toAddress, maxNumOfAttempts = 5)
      // then
      logEff.warns should equal(
        List(
          "Failed to connect to bootstrap (attempt 1 / 5)",
          "Failed to connect to bootstrap (attempt 2 / 5)",
          "Failed to connect to bootstrap (attempt 3 / 5)",
          "Failed to connect to bootstrap (attempt 4 / 5)",
          "Failed to connect to bootstrap (attempt 5 / 5)"
        ))
    }

    it("should log on ERROR and return error when failed connecting") {
      // given
      communicationEff.setResponses(kp(failEverything))
      // when
      val result = Network.connectToBootstrap[Effect](remote.toAddress, maxNumOfAttempts = 5)
      // then
      logEff.errors should equal(List("Failed to connect to bootstrap node, exiting..."))
      result.value should equal(Left(couldNotConnectToBootstrap))
    }

    it("should connect smoothly if there are no issues.") {
      // given
      communicationEff.setResponses(kp(generateResponses(fstPhase, sndPhaseSucc)))
      // when
      val result = Network.connectToBootstrap[Effect](remote.toAddress, maxNumOfAttempts = 5)
      // then
      logEff.infos should contain(s"Bootstrapping from $remote.")
      logEff.infos should contain(s"Connected $remote.")
      result.value should equal(Right(()))
    }
  }

  private def value[A](ea: Effect[A]): A = ea.value.right.get

  private val fstPhase: PartialFunction[ProtocolMessage, CommErr[ProtocolMessage]] = {
    case hs @ EncryptionHandshakeMessage(_, _) =>
      hs.response[Effect](ProtocolNode(remote, roundTripNOP), remoteKeys).value.right.get
  }

  private val failEverything = kp(Left[CommError, ProtocolResponse](unknownProtocol("unknown")))

  private val sndPhaseSucc: PartialFunction[ProtocolMessage, CommErr[ProtocolMessage]] = {
    case hs @ FrameMessage(_, _) =>
      Right(
        FrameMessage(frameResponse(ProtocolNode(remote, roundTripNOP),
                                   hs.header.get,
                                   Array.empty[Byte],
                                   Array.empty[Byte]),
                     1))
  }

  private val sndPhaseFailure: PartialFunction[ProtocolMessage, CommErr[ProtocolMessage]] = {
    case hs @ FrameMessage(_, _) => Left(unknownProtocol("unknown"))
  }

  private def generateResponses(
      fstPhase: PartialFunction[ProtocolMessage, CommErr[ProtocolMessage]],
      sndPhase: PartialFunction[ProtocolMessage, CommErr[ProtocolMessage]])
    : ProtocolMessage => CommErr[ProtocolMessage] =
    fstPhase orElse sndPhase

  private val roundTripNOP =
    kp2[ProtocolMessage, ProtocolNode, CommErr[ProtocolMessage]](Left(unknownProtocol("unknown")))
  private def endpoint(port: Int): Endpoint = Endpoint("host", port, port)

  private def peerNode(name: String, port: Int): PeerNode =
    new PeerNode(NodeIdentifier(name.getBytes), endpoint(port))

  private def protocolNode(name: String, port: Int): ProtocolNode =
    ProtocolNode(peerNode(name, port), roundTripNOP)
}
