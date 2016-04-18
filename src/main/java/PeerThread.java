import java.io.*;
import java.net.Socket;
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
    private  Thread initializeThread = null;
    public Peer getPeer() {
        return peerConnected;
    }

    public boolean isStop() {
        return stop;
    }

    public void setStop(boolean v) {
        stop = v;
        if(v == true){
            try {
                if(!socket.isClosed())
                {
                    socket.close();
                }
            } catch (IOException e) {
                System.out.println("Cannot close socket for peer " + peerConnected.getId());
                e.printStackTrace();
            }
        }
    }

    public PeerThread(Socket s, boolean client, int id) {
        this.socket = s;
        this.isClient = client;
        peerConnected = new Peer(socket);
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
        initializeThread = new Thread() {
            public void run() {
                System.out.println("Peerconnected is initialized " + peerConnected.isInitialized());
                peerConnected.sendBitfieldMsg();
                peerConnected.readBitfieldMsg();

                if (peerConnected.isInterested()) {
                    System.out.println("Sending interested msg ");
                    peerConnected.sendInterestedMsg();
                } else {
                    System.out.println("Sending not interested msg ");
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
                peerConnected.setInitialized(true);
            }

        };
        System.out.println("Is initialized before thread start " + peerConnected.isInitialized());
        initializeThread.start();


    }

    @Override
    public void run() {
        // Populate list of peers who are interested in my data..
        // all real time communication to be handled here for every peer.

        // thread runs till not asked to stop

        peerConnected.initialized();
        System.out.println("peerConnected.isInitialized() = " + peerConnected.isInitialized());
        System.out.println("The id for initialization is " + peerConnected.getId());
        System.out.println("Initializaed completed!!");
            try {
                InputStream inputStream = new BufferedInputStream(socket.getInputStream());
                while (!isStop()) {
                    byte[] msgBytesStat = new byte[5];
                    msgBytesStat = Util.readBytes(inputStream, msgBytesStat, 5);
//                inputStream.read(msgBytesStat);
                    Constants.ActualMessageTypes msgType = MessagesUtil
                            .getMsgType(msgBytesStat);
                    switch (msgType) {
                        case BITFIELD:
                            // sent initially
                            // should be handled in acceptConnection
                            break;
                        case HAVE:
                            System.out.println("Have msg received from " + peerConnected.getId());
                            byte[] readPieceIndexBytes = new byte[4];
//                        inputStream.read(readPieceIndexBytes);
                            readPieceIndexBytes = Util.readBytes(inputStream, readPieceIndexBytes, 4);
                            int pieceIndex = Util.byteArrayToInt(readPieceIndexBytes);
                            //int byteIndex = pieceIndex / 8;
                            //int position = pieceIndex % 8;
                            //position = 7-position;
                            byte[] myBitField = Peer.getMyBitField();
                            byte myByte = myBitField[pieceIndex / 8];

                            if ((myByte & (1 << (7 - (pieceIndex % 8)))) == 0) {
                                // I don't have this piece
                                peerConnected.sendInterestedMsg();
                            }
                            peerConnected.updateBitFieldMsg(pieceIndex);
                            LOGGER.info("Peer " + Peer.myId
                                    + " received the have message from " + peerConnected.getId());
                            break;
                        case CHOKE:
                            System.out.println("choke msg received from " + peerConnected.getId());

                            int requestedIndex = peerConnected.getRequestedIndex();
                            // remove from requestedIndex
                            Peer.removeSetBitFieldRequested(requestedIndex / 8,
                                    requestedIndex % 8);
                            Peer.peersChokedMeMap.put(peerConnected.getId(), peerConnected);
                            //Peer.peersChokedMeMap.remove(peerConnected.getId());
                            LOGGER.info("Peer " + Peer.myId + " is choked by "
                                    + peerConnected.getId());
                            break;
                        case INTERESTED:
                            System.out.println("interested msg received " + peerConnected.getId());
                            System.out.println(peerConnected.getId());
                            boolean isPresent = false;
                            for(Peer p : Peer.interestedNeighboursinMe){
                                if(p.getId() == peerConnected.getId()){
                                    isPresent = true;
                                }
                            }
                            if(!isPresent){
                                Peer.interestedNeighboursinMe.add(peerConnected);
                            }
                            //Peer.interestedNeighboursinMe.add(peerConnected);
                            LOGGER.info("Peer " + Peer.myId
                                    + " received the interested message from "
                                    + peerConnected.getId());
                            break;
                        case NOT_INTERESTED:
                            System.out.println("not interested msg received "  + peerConnected.getId());
                            Peer.notInterestedNeighboursinMe.put(peerConnected.getId(), peerConnected);
                            LOGGER.info("Peer " + Peer.myId
                                    + " received the not interested message from "
                                    + peerConnected.getId());
                            break;
                        case PIECE:
                            System.out.println("piece msg received "  + peerConnected.getId());
                            byte[] sizeByteArray = new byte[4];
                            for (int i = 0; i < 4; i++) {
                                sizeByteArray[i] = msgBytesStat[i];
                            }
                            int sizeOfMsg = Util.byteArrayToInt(sizeByteArray);
                            byte[] pieceIndexBytes = new byte[4];
                            pieceIndexBytes = Util.readBytes(inputStream, pieceIndexBytes, 4);
//                        inputStream.read(pieceIndexBytes);
                            int sizeOfPieceMsg = sizeOfMsg - 1;
                            int sizeOfPiecePayLoad = sizeOfPieceMsg - 4;
                            byte[] piece = new byte[sizeOfPiecePayLoad];
                            piece = Util.readBytes(inputStream, piece, sizeOfPiecePayLoad);
//                        inputStream.read(piece);
                            Long downTime = System.nanoTime()
                                    - Peer.requestTime.get(peerConnected.getId());

                            Peer.downloadTime.put(peerConnected.getId(), downTime);
                            peerConnected.setDownloadingRate(downTime);
                            int pieceI = Util.byteArrayToInt(pieceIndexBytes);
                            System.out.println("The piece index value is " + pieceI);
                            int stdPieceSize = Integer.parseInt(Configuration
                                    .getComProp().get("PieceSize"));
                            for (int i = 0; i < sizeOfPiecePayLoad; i++) {
                                Peer.dataShared[pieceI * stdPieceSize + i] = piece[i];
                            }
                            LOGGER.info("Peer " + Peer.myId
                                    + " has downloaded the piece " + pieceI + " from "
                                    + peerConnected.getId());

                            // send have message to rest of the peers
                            int index = pieceI / 8;
                            int pos = pieceI % 8;
                            Peer.setMyBitFieldRequested(index, pos);
                            Peer.removeSetBitFieldRequested(index, pos);
                            for (PeerThread peerThread : peerProcess.peersList) {
                                System.out.println("Reached Inside Piece Have check ");
                                //if (peerThread.getPeer() == peerConnected) {
                                System.out.println("Check Passed Sending Have Message ");
                                peerThread.getPeer().sendHaveMsg(pieceI);
                                //}
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
                            System.out.println("req msg received " +  peerConnected.getId());
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
                            System.out.println("unchoke msg rec " +   peerConnected.getId());
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
                        default:
                            System.out.println("something was received");
                    }
                }

            } catch (IOException e) {
                if(!isStop()) {
                    e.printStackTrace();
                }
            }finally {
                System.out.println("Exiting!! " + peerConnected.getId());
                initializeThread.interrupt();
            }

    }
}

