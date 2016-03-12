import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Represents a single peer to which I am connected too for a single file..
 */
public class Peer {
	private static final Logger LOGGER = Logger
			.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private boolean client = false;

	public void setClient(boolean v) {
		client = v;
	}

	public boolean isClient() {
		return client;
	}

	// All global variables are data representing the peer running in the
	// current machine that is shared with it's connecting peers.

	/**
	 * Map to store the peers with which handshake has been made successfully.
	 * Global entity.
	 */
	private static Map<Integer, Boolean> map = new HashMap<Integer, Boolean>();

	private HashSet<Peer> set = new HashSet<Peer>(); // Set of Peers that have
														// shown interest in my
														// data.

	private HashSet<Peer> preferredNeighbors = new HashSet<Peer>();

	private int id;

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	/**
	 * Bitfields corresponding to a data file that is being shared.
	 */
	private static byte[] bitfields = null;

	// static block
	static {
		String peerId = Configuration.getComProp().get("peerId");
		File f = new File(peerId);
		long noOfPieces = 0;
		if (f.exists()) // This peer is the original uploader..
		{
			int pieceSize = Integer.parseInt(Configuration.getPeerProp().get(
					"PieceSize"));
			if (f.length() % pieceSize == 0) {
				noOfPieces = f.length() / pieceSize;
			} else {
				noOfPieces = f.length() / pieceSize + 1;
			}
		}
		double bl = Math.ceil(noOfPieces / 8.0f);
		bitfields = new byte[(int) bl];
	}

	private Socket socket = null;
	private DataOutputStream out = null;
	private DataInputStream in = null;

	/*
	 * BufferedReader buffReader = new BufferedReader(new
	 * InputStreamReader(socket.getInputStream())); ){ String input, output;
	 * while((input = buffReader.readLine()) != null){
	 * System.out.println(input); if(input.equals("Bye")) break; }
	 */

	public Peer(Socket s) {
		this.socket = s;
		try {
			out = new DataOutputStream(socket.getOutputStream());
			in = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {
			System.out.println("socket exception!!!");
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void finalize() {
		this.close();
	}

	public void sendHandshakeMsg() {
		synchronized (map) {
			// now send handshake to this peer
			byte[] concatenateByteArrays = Util.concatenateByteArrays(Util
					.concatenateByteArrays(Constants.HANDSHAKE_HEADER,
							Constants.ZERO_BITS), Configuration.getComProp()
					.get("peerId").getBytes());
			try {
				String s = new String(concatenateByteArrays);
				out.write(concatenateByteArrays);
				out.flush();
				map.put(id, false);
			} catch (IOException e) {
				LOGGER.severe("Send handshake failed !! " + e.getMessage());
				e.printStackTrace();
			}
			System.out.println("This t");
		}

	}

	public int receiveHandshakeMsg() {
		try {
			byte[] b = new byte[32];
			in.read(b);
			byte[] copyOfRange = Arrays.copyOfRange(b, 28, 32);
			String peer = new String(copyOfRange);
			Integer peerId = Integer.parseInt(peer);
			System.out.println("The peer id is " + peerId);
			if (client) {
				// then verify the expected neighbour
				if (map.containsKey(peerId) && map.get(peerId) == false) {
					map.put(peerId, true);
					System.out.println("verified for id " + peerId);
				}
				else{
					System.out.println("Screwed!!!");
				}
			}
			return peerId;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return -1;
	}

	public void sendBitfieldMsg() {
		try {
			byte[] actualMessage = MessagesUtil.getActualMessage(bitfields,
					Constants.ActualMessageTypes.BITFIELD);
			out.write(actualMessage);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		;
	}

}
