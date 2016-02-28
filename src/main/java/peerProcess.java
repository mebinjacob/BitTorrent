import java.net.ServerSocket;
import java.util.Scanner;
/**
 * @author Mebin Jacob
 */
public class peerProcess { // naming convention violated due to project requirement..
	
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		int portNumber = scan.nextInt();
		int peerID = scan.nextInt();
		scan.close();
	}
	
	/**
	 * Accepts connection for every peer in a separate thread..
	 * 
	 * @param portNumber
	 */
	public void acceptConnection(int portNumber) {
		boolean listening = true;

		try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
			while(listening){
				new PeerThread(serverSocket.accept()).start();
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}
