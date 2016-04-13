import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
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
	private static final Logger LOGGER = Logger
			.getLogger(Logger.GLOBAL_LOGGER_NAME);
	public static PeerComparator<Peer> peerComparator = new PeerComparator<Peer>();

	public static List<PeerThread> peersList = new ArrayList<PeerThread>();

	List<Peer> unchockeList = null; // interested and unchoked peers
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
		peer.acceptConnection(Integer.valueOf(portNo));
		peerProcess peerProcessObj = new peerProcess();
		Map<String, String> comProp = Configuration.getComProp();
		int m = Integer.parseInt(comProp.get("OptimisticUnchokingInterval"));
		int k = Integer.parseInt(comProp.get("NumberOfPreferredNeighbors"));
		int p = Integer.parseInt(comProp.get("UnchokingInterval"));
		peerProcessObj.determineOptimisticallyUnchokedNeighbour(m);
		peerProcessObj.determinePreferredNeighbours(k, p);
	}

	/**
	 * Accepts connection for every peer in a separate thread..
	 * 
	 * @param portNumber
	 */
	public void acceptConnection(int portNumber) {
		boolean listening = true;

		try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
			while (listening) {
				PeerThread peerThread = new PeerThread(serverSocket.accept(),
						false, -1);
				peerThread.start();
				peersList.add(peerThread);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Connects to all available clients. PeerId is self peerid as to not to
	 * connect to self or anyone with greater peer id.
	 */
	public void clientConnect(int peerId) {
		Map<Integer, String> peerProp = Configuration.getPeerProp();
		for (Integer s : peerProp.keySet()) {
			if (s < peerId) {
				String line = peerProp.get(s);
				String[] split = line.split(" ");
				String host = split[1];
				String port = split[2];
				String peerid = split[0];
				try {
					Socket socket = new Socket(host, Integer.parseInt(port));
					PeerThread peerThread = new PeerThread(socket, true,
							Integer.parseInt(peerid));
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
			.newScheduledThreadPool(2);

	/**
	 * Determine optimistically unchocked neighbour every m seconds.
	 */
	public void determineOptimisticallyUnchokedNeighbour(final int m) {
		final Runnable optimisticallyUnchockedNeighbourDeterminer = new Runnable() {

			@Override
			public void run() {
				// select optimistically unchocked neighbour from
				int randIndex = ThreadLocalRandom.current().nextInt(0,
						chokeList.size());
				Peer peer = chokeList.get(randIndex);
				peer.sendUnChokeMsg();
				peer.setChoked(false); // so that it can expect request message
				LOGGER.info("Peer " + Peer.myId + " has the optimistically unchoked neighbor " + randIndex);
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
				// select k preferrred neighbours from neighbours that are
				// interested in my data.
				// calculate the downloading rate from each peer. set it
				// initially to 0.

				// Select k preferred neighbours, when it has all elements too
				// it should be taken care of..
				PriorityBlockingQueue<Peer> interestedList = Peer.interestedNeighboursinMe;
				// select k which has highest download rate
				Iterator<Peer> iterator = interestedList.iterator();
				unchockeList = new ArrayList<Peer>();
				chokeList = new ArrayList<Peer>();
				int count = k;
				
				
				StringBuilder listOfUnchokedNeighbours = new StringBuilder();
				while (iterator.hasNext()) {
					Peer next = iterator.next();
					if (count > 0) {
						unchockeList.add(next);
						listOfUnchokedNeighbours.append(next.getId() + ",");
					} else {
						next.sendChokeMsg();
						chokeList.add(next);
					}

					count--;
				}
				LOGGER.info("Peer " + Peer.myId + " has the preferred neighbors " + listOfUnchokedNeighbours);
				
				for (Peer p : unchockeList) {
					if (p.isChocked()) {
						p.sendUnChokeMsg(); // now expect recieve message
						p.setChoked(false);
					}
				}

				for (Peer p : chokeList) {
					if (!p.isChocked()) {
						p.sendChokeMsg();
						p.setChoked(true);
					}
				}

			}
		};
		final ScheduledFuture<?> kNeighborDeterminerHandle = scheduler
				.scheduleAtFixedRate(kNeighborDeterminer, p, p, SECONDS);// 2 is
																			// initial
																			// delay
		// need to test
		scheduler.schedule(new Runnable() {
			public void run() {
				// check if file has downloaded
				byte[] myBitField = Peer.getMyBitField();
				boolean result = false;
				Integer one = new Integer(1);

				for (byte b : myBitField) {
					if ((b & one.byteValue()) != 1) {
						result = false;
						break;
					}
					result = true;
				}
				if (result == true) {
					kNeighborDeterminerHandle.cancel(true);
					// stop all threads
					Iterator<PeerThread> descendingIterator = peersList
							.iterator();
					while (descendingIterator.hasNext()) {
						PeerThread p = descendingIterator.next();
						// ask threads to stop gracefully
						p.setStop(true);

					}
				}

			}
		}, 4 * 60 * 60, SECONDS);
	}
}
