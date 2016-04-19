import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;

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
                System.out.println("Arrays.toString(myBitField) = " + Arrays.toString(myBitField));
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

                        //lets write it to a file


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
                    System.out.println("Remember peer selected should come after this---");
                    int randIndex = ThreadLocalRandom.current().nextInt(0, size);
                    Peer peer = chokeList.remove(randIndex);
                    System.out.println("selecting an optimistcally neighbor");
                    System.out.println("randIndex = " + randIndex);
                    System.out.println("Peer selected is " + peer.getId());
                    if (peer != null && peer != previousOptimisticallyUnchokedPeer) {
                        System.out.println("selecting a new  optimistcally neighbor");
                        peer.setOptimisticallyUnchoked(true);
                        peer.sendUnChokeMsg();
                        if (previousOptimisticallyUnchokedPeer != null) {
                            previousOptimisticallyUnchokedPeer.setOptimisticallyUnchoked(false);
                            if (previousOptimisticallyUnchokedPeer.isChoked()) {
                                System.out.println("Sending Choke msg from Optimistcally");
                                peer.sendChokeMsg();
                            }
                        }
                        previousOptimisticallyUnchokedPeer = peer;
                        log("Peer " + Peer.myId + " has the optimistically unchoked neighbor " + "Peer " + Peer.myId);
                        System.out.println("Peer " + Peer.myId + " has the optimistically unchoked neighbor " + "Peer " + Peer.myId);
                    }
                }

            }

        };
        scheduler.scheduleAtFixedRate(
                optimisticallyUnchockedNeighbourDeterminer, m, m, SECONDS);
    }

    public void log(String msg) {
        Logger logger = LOGGER;
        if (logger == null) {
            logger = MyLogger.getMyLogger();
        }
        logger.info(msg);
    }

    /**
     * Determines k preferred neighbors every p seconds
     */
    public void determinePreferredNeighbours(final int k, final int p) {
        try {
            final Runnable kNeighborDeterminer = new Runnable() {
                public void run() {
                    System.out.println("K preferred neighbours called");
                    // select k preferrred neighbours from neighbours that are
                    // interested in my data.
                    // calculate the downloading rate from each peer. set it
                    // initially to 0.

                    // Select k preferred neighbours, when it has all elements too
                    // it should be taken care of..
                    List<Peer> interestedList = Peer.interestedNeighboursinMe;
                    Collections.sort(interestedList, new PeerComparator<Peer>());
                    // select k which has highest download rate
                    if (interestedList != null) {
                        System.out.println("Interested list size is " + interestedList.size());
                        Iterator<Peer> iterator = interestedList.iterator();
                        unchokeList = Collections.synchronizedList(new ArrayList<Peer>());
                        chokeList = Collections.synchronizedList(new ArrayList<Peer>());
                        int count = k;

                        StringBuffer listOfUnchokedNeighbours = new StringBuffer(" ");
                        while (iterator.hasNext()) {
                            Peer next = iterator.next();
                            if (next.isInitialized()) {
                                if (count > 0) {
                                    System.out.println("peerProcess.run unchoked " + next.getId());
                                    unchokeList.add(next);
                                    if (next.isChoked()) {
                                        next.setChoked(false);
                                        if (!next.isOptimisticallyUnchoked()) {
                                            System.out.println("Sending  unchoking msg " + next.getId());
                                            next.sendUnChokeMsg(); // now expect recieve message
                                            //p.setChoked(false);
                                        }
                                    }
                                    listOfUnchokedNeighbours.append(next.getId() + ",");
                                } else {
                                    System.out.println("peerProcess.run choked " + next.getId());
                                    chokeList.add(next);
                                    if (!next.isChoked()) {
                                        next.setChoked(true);
                                        if (!next.isOptimisticallyUnchoked()) {
                                            System.out.println("Sending  choke msg " + next.getId());
                                            next.sendChokeMsg();
                                        }
                                    }
                                }
                            }
                            count--;
                        }

                        String neigh = listOfUnchokedNeighbours.toString();
                        if (!neigh.trim().isEmpty()) {
                            log("Peer " + Peer.myId + " has the preferred neighbors " + neigh);
                        }

                    }
                }
            };
            final ScheduledFuture<?> kNeighborDeterminerHandle = scheduler
                    .scheduleAtFixedRate(kNeighborDeterminer, p, p, SECONDS);
        } catch (Exception e) {
            System.out.println("LOL ho gaya");
            System.out.println(e.getMessage());
        }


    }
}