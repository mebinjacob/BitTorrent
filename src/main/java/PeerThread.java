import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * @author Mebin Jacob The threads may come and go but the Peer shall maintain
 *         it's state.
 */
public class PeerThread extends Thread {
    private static final Logger LOGGER = MyLogger.getMyLogger();
    private Socket socket = null;
    private boolean isClient = false;
    private boolean stop = false;
    private Peer peerConnected = null;

    public Peer getPeer() {
        return peerConnected;
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean v) {
        stop = v;
    }

    public PeerThread(Socket s, boolean client, int id) {
        this.socket = s;
        this.isClient = client;
        peerConnected = new Peer(socket);
        System.out.println(peerConnected.getId());
        if (isClient) {
            peerConnected.setId(id);
            peerConnected.setClient(true);
            peerConnected.sendHandshakeMsg();
            peerConnected.receiveHandshakeMsg();
        } else {
            int peerId = peerConnected.receiveHandshakeMsg();
            peerConnected.setId(peerId);
            peerConnected.sendHandshakeMsg();

        }
        peerConnected.sendBitfieldMsg();
        peerConnected.readBitfieldMsg();
        if (peerConnected.isInterested()) {
            peerConnected.sendInterestedMsg();
        } else {
            peerConnected.sendNotInterestedMsg();
        }

        System.out.println("It comes here 3 ");
        // should have all peerid's of receiver and sender here, hence do
        // logging

        if (isClient == true) {
            LOGGER.info("Peer " + Configuration.getComProp().get("peerId")
                    + " makes a connection to Peer " + peerConnected.getId());
        } else {
            LOGGER.info(Configuration.getComProp().get("peerId")
                    + " is connected from " + peerConnected.getId());
        }
    }

    @Override
    public void run() {
        // Populate list of peers who are interested in my data..
        // all real time communication to be handled here for every peer.

        // thread runs till not asked to stop
        try {
            InputStream inputStream = socket.getInputStream();
            while (!isStop()) {
                System.out.println("Crap!!");
                byte[] msgBytesStat = new byte[5];
                inputStream.read(msgBytesStat);
                Constants.ActualMessageTypes msgType = MessagesUtil
                        .getMsgType(msgBytesStat);
                switch (msgType) {
                    case BITFIELD:
                        // sent initially
                        // should be handled in acceptConnection
                        break;
                    case HAVE:
                        byte[] readPieceIndexBytes = new byte[4];
                        inputStream.read(readPieceIndexBytes);
                        int pieceIndex = Util.byteArrayToInt(readPieceIndexBytes);
                        byte[] myBitField = Peer.getMyBitField();
                        byte myByte = myBitField[pieceIndex / 8];
                        if ((myByte & (1 << pieceIndex << pieceIndex % 8)) != 1) {
                            // I don't jhave this piece
                            peerConnected.sendInterestedMsg();
                            peerConnected.updateBitFieldMsg(pieceIndex);
                        }
                        LOGGER.info("Peer " + Peer.myId
                                + " received the have message from " + peerConnected.getId());
                        break;
                    case CHOKE:
                        int requestedIndex = peerConnected.getRequestedIndex();
                        // remove from requestedIndex
                        Peer.removeSetBitFieldRequested(requestedIndex / 8,
                                requestedIndex % 8);
                        Peer.peersChokedMeMap.remove(peerConnected.getId());
                        LOGGER.info("Peer " + Peer.myId + " is choked by "
                                + peerConnected.getId());
                        break;
                    case INTERESTED:
                        System.out.println(peerConnected.getId());
                        Peer.interestedNeighboursinMe.add(peerConnected);
                        LOGGER.info("Peer " + Peer.myId
                                + " received the interested message from "
                                + peerConnected.getId());
                        break;
                    case NOT_INTERESTED:
                        Peer.notInterestedNeighboursinMe.put(peerConnected.getId(), peerConnected);
                        LOGGER.info("Peer " + Peer.myId
                                + " received the not interested message from "
                                + peerConnected.getId());
                        break;
                    case PIECE:
                        byte[] sizeByteArray = new byte[4];
                        for (int i = 0; i < 4; i++) {
                            sizeByteArray[i] = msgBytesStat[i];
                        }
                        int size = Util.byteArrayToInt(sizeByteArray);
                        byte[] pieceIndexBytes = new byte[4];
                        inputStream.read(pieceIndexBytes);
                        int sizeOfPiece = size - 1;
                        byte[] piece = new byte[sizeOfPiece];
                        inputStream.read(piece);
                        Long downTime = System.nanoTime()
                                - Peer.requestTime.get(peerConnected.getId());

                        Peer.downloadTime.put(peerConnected.getId(), downTime);
                        peerConnected.setDownloadingRate(downTime);
                        int pieceI = Util.byteArrayToInt(pieceIndexBytes);
                        System.out.println("The piece index value is " + pieceI);
                        int stdPieceSize = Integer.parseInt(Configuration
                                .getComProp().get("PieceSize"));
                        for (int i = 0; i < sizeOfPiece && i < Peer.dataShared.length; i++) {
                            Peer.dataShared[pieceI * stdPieceSize + i] = piece[i];
                        }
                        LOGGER.info("Peer " + Peer.myId
                                + " has downloaded the piece " + pieceI + " from "
                                + peerConnected.getId());
                        // TODO: get the piece and make the file

                        // send have message to rest of the peers
                        for (PeerThread peerThread : peerProcess.peersList) {
                            if (peerThread.getPeer() != peerConnected) {
                                peerThread.getPeer().sendHaveMsg(pieceI);
                            }
                        }
                        int i = peerConnected.getNextBitFieldIndexToRequest();
                        System.out.println("next index requested is " + i);
                        if (i != -1
                                && Peer.peersUnchokedMeMap.containsKey(peerConnected.getId())) {
                            System.out.println("Yes its being requested" + i);
                            peerConnected.sendRequestMsg(i);
                        }
                        if (i == -1) {
                            //lets write it to a file
                            File file = new File(Configuration.getComProp().get("FileName"));
                            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                                fileOutputStream.write(Peer.dataShared);
                            }

                        }
                        break;
                    case REQUEST:
                        byte[] ind = new byte[4];
                        inputStream.read(ind);
                        int pIndex = Util.byteArrayToInt(ind);
                        System.out.println("unscrew it dude!!!");
                        if (!peerConnected.isChoked()) {
                            System.out.println("sending piece message from request the piece index requested is " + pIndex);
                            peerConnected.sendPieceMsg(pIndex);
                        }
                        // in request the id will be returned
                        // send piece msg if in unchoked list
                        break;
                    case UNCHOKE:
                        Peer.peersUnchokedMeMap.put(peerConnected.getId(), peerConnected);
                        // request a piece that I do not have and have not requested
                        // from other neighbors, selection
                        // of piece should happen randomly
                        LOGGER.info("Peer " + Peer.myId + " is unchoked by "
                                + peerConnected.getId());
                        int nextIndex = peerConnected.getNextBitFieldIndexToRequest();
                        System.out.println("Next index requested " + nextIndex);
                        if (nextIndex != -1) {
                            peerConnected.sendRequestMsg(nextIndex);
                        }
                        break;
                }
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
