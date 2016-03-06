import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single peer to which I am connected too for a single file..
 */
public class Peer {
	
	//All global variables are data representing the peer running in the current machine that is shared with it's connecting peers.
	
	/**
	 * Map to store the peers with which handshake has been made successfully. Global entity.
	 */
	private static Map<Peer, Boolean> map = new HashMap<Peer, Boolean>();
	
	/**
	 * Bitfields corresponding to a data file that is being shared.
	 */
	private static byte[] bitfields = null; 
	
	//static block 
	static {
		String peerId = Configuration.commonProp.get("peerId");
		File f = new File(peerId);
		long noOfPieces = 0;
		if(f.exists()) // This peer is the original uploader..
		{
			int pieceSize = Integer.parseInt(Configuration.peerProp.get("PieceSize"));
			if(f.length()%pieceSize == 0){
				noOfPieces = f.length()/pieceSize;
			}
			else{
				noOfPieces = f.length()/pieceSize + 1;
			}
		}
		double bl = Math.ceil(noOfPieces/8.0f);
		bitfields = new byte[(int)bl];
	}
	
	private Socket socket = null;
	private DataOutputStream out = null;

	
/*	BufferedReader buffReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
){
String input, output;
while((input = buffReader.readLine()) != null){
System.out.println(input);
if(input.equals("Bye"))
	break;
}*/
	
	public Peer(Socket s) {
		this.socket = s;
		try {
			out = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			System.out.println("socket exception!!!");
			e.printStackTrace();
		}
	}

	public void close(){
		try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void finalize()
	{
		this.close();
	}
	

	public void sendHandshakeMsg() {
		synchronized (map) {
			if (map.containsKey(this) && map.get(this) == false) {
				map.put(this, true);
			} else {
				map.put(this, false);
			}
		}

	}

	public void receiveHandshakeMsg(Peer B) {
		// TODO: check whether Peer B is the right neighbor.
		synchronized (map) {
			if (map.containsKey(this) && map.get(this) == false) {
				map.put(this, true);
			} else {
				map.put(this, false);
			}
		}
	}


	public void sendBitfieldMsg() {
		try {
			byte[] actualMessage = MessagesUtil.getActualMessage(bitfields, Constants.ActualMessageTypes.BITFIELD);
			out.write(actualMessage);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		};
	}


}
