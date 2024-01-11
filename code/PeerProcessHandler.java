package code;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import code.MessagePassing;
import code.Handshake;
import code.PeerMain;

public class PeerProcessHandler implements Runnable {
    private Socket listener;
    private PeerMain peerAdmin;
    private String endPeerID;
    private boolean connectionEstablished = false;
    private boolean initializer = false;
    private Handshake hsm;
    private volatile int downloadRate = 0;
    private volatile ObjectOutputStream out;
    private volatile ObjectInputStream in;
    private final Lock streamInitLock = new ReentrantLock();

    public PeerProcessHandler(Socket listener, PeerMain admin) {
        this.listener = listener;
        this.peerAdmin = admin;
        initStreams();
        this.hsm = new Handshake(this.peerAdmin.getPeerID());
    }

    public PeerMain getPeerAdmin() {
        return this.peerAdmin;
    }

    public void initStreams() {
        try {
            streamInitLock.lock();
            this.out = new ObjectOutputStream(this.listener.getOutputStream());
            this.out.flush();
            this.in = new ObjectInputStream(this.listener.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            streamInitLock.unlock();
        }
    }

    public synchronized Socket getListener() {
        return this.listener;
    }

    public void setEndPeerID(String pid) {
        this.endPeerID = pid;
        this.initializer = true;
    }

    public void run() {
        try {
            byte[] msg = this.hsm.buildHandshakeMsg();
            this.out.write(msg);
            this.out.flush();
            while (1 != 2) {
                if (!this.connectionEstablished) {
                    byte[] response = new byte[32];
                    this.in.readFully(response);
                    this.processHandShakeMessage(response);
                    if (this.peerAdmin.hasFile() || this.peerAdmin.getIfAvailable(this.peerAdmin.getPeerID())
                            .cardinality() > 0) {
                        this.sendBitField();
                    }
                } else {
                    while (this.in.available() < 4) {
                    }
                    int respLen = this.in.readInt();
                    byte[] response = new byte[respLen];
                    this.in.readFully(response);
                    char messageType = (char) response[0];
                    MessagePassing am = new MessagePassing();
                    am.readMessagePassing(respLen, response);
                    handleMessageType(messageType, am);
                }
            }
        } catch (SocketException e) {
            System.out.println("Socket exception");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleMessageType(char messageType, MessagePassing am) {
        switch (messageType) {
            case '0':
                handleChokeResponse();
                break;
            case '1':
                handleUnchokeMessage();
                break;
            case '2':
                handleInterestedMessage();
                break;
            case '3':
                handleNotInterestedMessage();
                break;
            case '4':
                handleHaveMessage(am);
                break;
            case '5':
                handleBitFieldMessage(am);
                break;
            case '6':
                handleRequest(am);
                break;
            case '7':
                handlePieceResponse(am);
                break;
            default:
                System.out.println("Received other message");
        }
    }

    public void handleChokeResponse() {
        this.peerAdmin.resetRequested(this.endPeerID);
        this.peerAdmin.getLogger().chokingNeighbor(this.endPeerID);
    }

    public void handleUnchokeMessage() {
        int requestindex = this.peerAdmin.checkForRequested(this.endPeerID);
        if (requestindex == -1) {
            this.sendNotInterestedMessage();
        } else {
            this.sendRequestMessage(requestindex);
        }
        this.peerAdmin.getLogger().unchokedNeighbor(this.endPeerID);
    }

    public void handleInterestedMessage() {
        this.peerAdmin.addToInterestedList(this.endPeerID);
        this.peerAdmin.getLogger().receiveInterested(this.endPeerID);
    }

    public void handleNotInterestedMessage() {
        this.peerAdmin.removeFromInterestedList(this.endPeerID);
        this.peerAdmin.getLogger().receiveNotInterested(this.endPeerID);
    }

    public void handleHaveMessage(MessagePassing am) {
        int pieceIndex = am.getPieceIndexPayload();
        this.peerAdmin.updatePieceAvailability(this.endPeerID, pieceIndex);
        if (this.peerAdmin.checkIfAllPeersAreDone()) {
            this.peerAdmin.cancelChokes();
        }
        if (this.peerAdmin.checkInterest(this.endPeerID)) {
            this.sendInterestedMessage();
        } else {
            this.sendNotInterestedMessage();
        }
        this.peerAdmin.getLogger().receiveHave(this.endPeerID, pieceIndex);
    }

    public void handleBitFieldMessage(MessagePassing am) {
        BitSet bset = am.getMessageBitField();
        this.processBitFieldMessage(bset);
        if (!this.peerAdmin.hasFile()) {
            if (this.peerAdmin.checkInterest(this.endPeerID)) {
                this.sendInterestedMessage();
            } else {
                this.sendNotInterestedMessage();
            }
        }
    }

    public void handleRequest(MessagePassing am) {
        if (this.peerAdmin.getUnchokedList().contains(this.endPeerID)
                || (this.peerAdmin.getOptimisticUnchokedPeer() != null
                        && this.peerAdmin.getOptimisticUnchokedPeer().compareTo(this.endPeerID) == 0)) {
            int pieceIndex = am.getPieceIndexPayload();
            this.sendPieceMessage(pieceIndex, this.peerAdmin.readFromFile(pieceIndex));
        }
    }

    public void handlePieceResponse(MessagePassing am) {
        int pieceIndex = am.getPieceIndexPayload();
        byte[] piece = am.getPiecePayload();
        this.peerAdmin.writeToFile(piece, pieceIndex);
        this.peerAdmin.updatePieceAvailability(this.peerAdmin.getPeerID(), pieceIndex);
        this.downloadRate++;
        Boolean alldone = this.peerAdmin.checkIfAllPeersAreDone();
        this.peerAdmin.getLogger().downloadPiece(this.endPeerID, pieceIndex,
                this.peerAdmin.getCompletedPieceCount());
        this.peerAdmin.setRequestedInfo(pieceIndex, null);
        this.peerAdmin.broadcastIfHave(pieceIndex);
        if (this.peerAdmin.getIfAvailable(this.peerAdmin.getPeerID()).cardinality() != this.peerAdmin
                .getPieceCount()) {
            int requestindex = this.peerAdmin.checkForRequested(this.endPeerID);
            if (requestindex != -1) {
                this.sendRequestMessage(requestindex);
            } else {
                this.sendNotInterestedMessage();
            }
        } else {
            this.peerAdmin.getLogger().downloadComplete();
            if (alldone) {
                this.peerAdmin.cancelChokes();
            }
            this.sendNotInterestedMessage();
        }
    }

    public synchronized void send(byte[] obj) {
        try {
            streamInitLock.lock();
            this.out.write(obj);
            this.out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            streamInitLock.unlock();
        }
    }

    public void sendChokedMessage() {
        sendMessage('0', null);
    }

    public void sendUnChokedMessage() {
        sendMessage('1', null);


    }

    public void sendInterestedMessage() {
        sendMessage('2', null);
    }

    public void sendNotInterestedMessage() {
        sendMessage('3', null);
    }

    public void sendHaveMessage(int pieceIndex) {
        try {
            byte[] bytes = ByteBuffer.allocate(4).putInt(pieceIndex).array();
            sendMessage('4', bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBitField() {
        try {
            BitSet myAvailability = this.peerAdmin.getIfAvailable(this.peerAdmin.getPeerID());
            sendMessage('5', myAvailability.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendRequestMessage(int pieceIndex) {
        try {
            byte[] bytes = ByteBuffer.allocate(4).putInt(pieceIndex).array();
            sendMessage('6', bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendPieceMessage(int pieceIndex, byte[] payload) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            byte[] bytes = ByteBuffer.allocate(4).putInt(pieceIndex).array();
            stream.write(bytes);
            stream.write(payload);
            sendMessage('7', stream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(char messageType, byte[] payload) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.write((byte) messageType);
            if (payload != null) {
                stream.write(payload);
            }
            MessagePassing am = new MessagePassing();
            am.readMessagePassing(stream.size(), stream.toByteArray());
            this.send(am.buildMessagePassing());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processHandShakeMessage(byte[] message) {
        try {
            this.hsm.readHandshakeMsg(message);
            this.endPeerID = this.hsm.getPeerID();
            this.peerAdmin.initiJoinedPeer(this, this.endPeerID);
            this.peerAdmin.addJoinedThreads(this.endPeerID, Thread.currentThread());
            this.connectionEstablished = true;
            if (this.initializer) {
                this.peerAdmin.getLogger().genTCPConnLogSender(this.endPeerID);
            } else {
                this.peerAdmin.getLogger().genTCPConnLogReceiver(this.endPeerID);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processBitFieldMessage(BitSet b) {
        this.peerAdmin.updateBitset(this.endPeerID, b);
    }

    public int getDownloadRate() {
        return this.downloadRate;
    }

    public void resetDownloadRate() {
        this.downloadRate = 0;
    }
}
