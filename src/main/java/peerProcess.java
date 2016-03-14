import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Mebin Jacob
 */
// This is our client entrypoint
// naming convention violated due to project
// requirement..
public class peerProcess {

	PeerComparator peerComparator = new PeerComparator();

	private TreeSet<PeerThread> peersTrees = new TreeSet<PeerThread>(peerComparator);

	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		int peerId = Integer.valueOf(args[0]);
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
		peerProcess p = new peerProcess();
		p.clientConnect(peerId);
		p.acceptConnection(Integer.valueOf(portNo));
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
				peersTrees.add(peerThread);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Connects to all available clients. PeerId is self peerid as to not to
	 * connect to self.
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
					peersTrees.add(peerThread);
				} catch (NumberFormatException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
	}

	private final ScheduledExecutorService scheduler = Executors
			.newScheduledThreadPool(1);

	/**
	 * Determines k preferred neighbors every p seconds
	 */
	public void determinePreferredNeighbours(final int k, final int p) {

		final Runnable kNeighborDeterminer = new Runnable() {
			public void run() {
				// determine k preferred neighbors
				Iterator<PeerThread> descendingIterator = peersTrees
						.descendingIterator();
				int i = 0;
				List<PeerThread> kPeerThreads = new ArrayList<PeerThread>();
				while (descendingIterator.hasNext() && i < k) {
					PeerThread nextPeer = descendingIterator.next();
					kPeerThreads.add(nextPeer);
					i++;
				}
				System.out.println("beep");
			}
		};
		final ScheduledFuture<?> kNeighborDeterminerHandle = scheduler
				.scheduleAtFixedRate(kNeighborDeterminer, 2, p, SECONDS);// 2 is
																			// initial
																			// delay
		//need to test
		scheduler.schedule(new Runnable() {
			public void run() {
				// check if file has downloaded
				byte[] myBitField = Peer.getMyBitField();
				boolean result = false;
				Integer one = new Integer(1);
				
				for(byte b:myBitField){
					if((b & one.byteValue()) != 1)
					{
						result = false;
						break;
					}
					result = true;
				}
				if(result == true){
					kNeighborDeterminerHandle.cancel(true);
					//stop all threads
					Iterator<PeerThread> descendingIterator = peersTrees
							.descendingIterator();
					while(descendingIterator.hasNext()){
						PeerThread p = descendingIterator.next();
						// ask threads to stop gracefully
						p.setStop(true);
						
					}
				}
				
			}
		}, 4 * 60 * 60, SECONDS);
	}

	@Override
	public void finalize() {

	}
}
