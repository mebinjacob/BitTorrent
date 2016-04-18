import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * @author Mebin Jacob
 */
// This is our client entrypoint
// naming convention violated due to project
// requirement..
public class peerProcess {
    private static final Logger LOGGER = MyLogger.getMyLogger();

    public static List<PeerThread> peersList = Collections.synchronizedList(new ArrayList<PeerThread>());

    List<Peer> unchokeList = null; // interested and unchoked peers
    List<Peer> chokeList = null; // interested and chocked peers

    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        int peerId = Integer.valueOf(args[0]);
        Peer.myId = peerId;
        Configuration.getComProp().put("peerId", String.valueOf(peerId));
        scan.close();
        // create a server socket
        String string = Configuration.getPeerProp().get(peerId);
        // Initialize our own custom logger
        try {
            MyLogger.setup();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Problems with creating the log files");
        }
        String portNo = string.split(" ")[2];
        peerProcess peer = new peerProcess();
        peer.clientConnect(peerId);
        peer.acceptConnection(peerId, Integer.valueOf(portNo));
        peerProcess peerProcessObj = new peerProcess();
        Map<String, String> comProp = Configuration.getComProp();
        int m = Integer.parseInt(comProp.get("OptimisticUnchokingInterval"));
        int k = Integer.parseInt(comProp.get("NumberOfPreferredNeighbors"));
        int p = Integer.parseInt(comProp.get("UnchokingInterval"));
        peerProcessObj.determinePreferredNeighbours(k, p);
        peerProcessObj.determineOptimisticallyUnchokedNeighbour(m);
        peerProcessObj.determineShutdownScheduler();
    }

    /**
     * Accepts connection for every peer in a separate thread..
     *
     * @param portNumber
     */
    int greaterPeerCount = 0;

    public void acceptConnection(int myPeerId, final int portNumber) {
        // TODO : Determine to shut down this thread.

        Map<Integer, String> peerProp = Configuration.getPeerProp();
        for (Integer s : peerProp.keySet()) {
            if (s > myPeerId) {
                greaterPeerCount++;
            }
        }
        Thread connectionAcceptThread = new Thread() {
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
                    while (greaterPeerCount > 0) {
                        Socket acceptedSocket = serverSocket.accept();
                        if (acceptedSocket != null) {
                            PeerThread peerThread = new PeerThread(acceptedSocket, false, -1);
                            peerThread.start();
                            peersList.add(peerThread);
                            greaterPeerCount--;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println(" culprit " + e.getMessage());
                }
            }
        };
        connectionAcceptThread.setName("Connection Accepting Thread ");
        connectionAcceptThread.start();
    }

    /**
     * Connects to all available clients. PeerId is self myPeerId as to not to
     * connect to self or anyone with greater peer id.
     */
    public void clientConnect(int myPeerId) {
        Map<Integer, String> peerProp = Configuration.getPeerProp();
        for (Integer s : peerProp.keySet()) {
            if (s < myPeerId) {
                String line = peerProp.get(s);
                String[] split = line.split(" ");
                String host = split[1];
                String port = split[2];
                String peerId = split[0];
                try {
                    Socket socket = new Socket(host, Integer.parseInt(port));
                    PeerThread peerThread = new PeerThread(socket, true,
                            Integer.parseInt(peerId));
                    peerThread.start();
                    peersList.add(peerThread);
                } catch (NumberFormatException | IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        }
    }

    private final ScheduledExecutorService scheduler = Executors
            .newScheduledThreadPool(3);

    /**
     * Determine when to shutdown...
     */
    public void determineShutdownScheduler() {
        final Runnable shutDownDeterminer = new Runnable() {
            @Override
            public void run() {
                byte[] myBitField = Peer.getMyBitField();
                if (peersList.size() > 0) {
                    boolean shutDown = true;
                    for (PeerThread p : peersList) {
                        byte[] pBitFieldMsg = p.getPeer().getPeerBitFieldMsg();
                        if (Arrays.equals(pBitFieldMsg, myBitField) == false) {
                            // do not shutdown
                            shutDown = false;
                            break;
                        }
                    }
                    if (shutDown) {
                        for (PeerThread p : peersList) {
                            p.setStop(true);
                            p.interrupt();
//                            listening = false; // to stop listening for socket connection
                        }
                        scheduler.shutdown();
                        if (!scheduler.isShutdown()) {
                            System.out.println("Shutdown nahi hua");
                        }
                        try {
                            scheduler.awaitTermination(5, SECONDS);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        };
        scheduler.scheduleAtFixedRate(shutDownDeterminer, 3, 3, SECONDS);

    }


    /**
     * Determine optimistically unchocked neighbour every m seconds.
     */
    Peer previousOptimisticallyUnchokedPeer;

    public void determineOptimisticallyUnchokedNeighbour(final int m) {
        final Runnable optimisticallyUnchockedNeighbourDeterminer = new Runnable() {

            @Override
            public void run() {
                System.out.println("inside optimistically unchoked neighbour!!");
                // select optimistically unchocked neighbour from
                int size = chokeList.size();
                System.out.println("size = " + size);
//                int randIndex = Util.getRandInt(0, chokeList.size()-1);
                if (size != 0) {
                    int randIndex = ThreadLocalRandom.current().nextInt(0, size);
                    System.out.println("randIndex = " + randIndex);
                    Peer peer = chokeList.get(randIndex);
                    if (peer != null && peer != previousOptimisticallyUnchokedPeer) {
                        peer.setOptimisticallyUnchoked(true);
                        peer.sendUnChokeMsg();
                        if (previousOptimisticallyUnchokedPeer != null) {
                            previousOptimisticallyUnchokedPeer.setOptimisticallyUnchoked(false);
                            if (previousOptimisticallyUnchokedPeer.isChoked()) {
                                peer.sendChokeMsg();
                            }
                        }
                        previousOptimisticallyUnchokedPeer = peer;
                        LOGGER.info("Peer " + Peer.myId + " has the optimistically unchoked neighbor " + randIndex);
                        System.out.println("Peer " + Peer.myId + " has the optimistically unchoked neighbor " + randIndex);
                    }
                }

            }

        };
        scheduler.scheduleAtFixedRate(
                optimisticallyUnchockedNeighbourDeterminer, m, m, SECONDS);
    }

    /**
     * Determines k preferred neighbors every p seconds
     */
    public void determinePreferredNeighbours(final int k, final int p) {

        final Runnable kNeighborDeterminer = new Runnable() {
            public void run() {
                System.out.println("K preferred neighbours called");
                // select k preferrred neighbours from neighbours that are
                // interested in my data.
                // calculate the downloading rate from each peer. set it
                // initially to 0.

                // Select k preferred neighbours, when it has all elements too
                // it should be taken care of..
                PriorityBlockingQueue<Peer> interestedList = Peer.interestedNeighboursinMe;
                // select k which has highest download rate
                Iterator<Peer> iterator = interestedList.iterator();
                unchokeList = new ArrayList<Peer>();
                chokeList = Collections.synchronizedList(new ArrayList<Peer>());
                int count = k;

                StringBuffer listOfUnchokedNeighbours = new StringBuffer(" ");
                while (iterator.hasNext()) {
                    Peer next = iterator.next();
                    if (next.isInitialized()) {
                        if (count > 0) {
                            System.out.println("peerProcess.run unchoked " + next.getId());
                            unchokeList.add(next);
                            listOfUnchokedNeighbours.append(next.getId() + ",");
                        } else {
                            System.out.println("peerProcess.run choked " + next.getId());
                            chokeList.add(next);
                        }

                    }

                    count--;
                }
//                LOGGER.info("Peer " + Peer.myId + " has the preferred neighbors " + listOfUnchokedNeighbours.toString());
                System.out.println(listOfUnchokedNeighbours.toString());
                for (Peer p : unchokeList) {
                    System.out.println("Inside unchoked list");
                    if (p.isChoked()) {
                        p.setChoked(false);
                        if (!p.isOptimisticallyUnchoked()) {
                            System.out.println("inside unchoking");
                            p.sendUnChokeMsg(); // now expect recieve message
                            //p.setChoked(false);
                        }
                    }

                }

                for (Peer p : chokeList) {
                    if (!p.isChoked()) {
                        p.setChoked(true);
                        if (!p.isOptimisticallyUnchoked()) {
                            p.sendChokeMsg();
                        }
                    }
                }

            }
        };
        final ScheduledFuture<?> kNeighborDeterminerHandle = scheduler
                .scheduleAtFixedRate(kNeighborDeterminer, p, p, SECONDS);

    }
}
