import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
/**
 * @author Mebin Jacob
 */
public class PeerThread extends Thread {
	private Socket socket = null;

	public PeerThread(Socket s) {
		this.socket = s;
	}

	@Override
	public void run() {
		try(
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader buffReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				){
			String input, output;
			while((input = buffReader.readLine()) != null){
				System.out.println(input);
				if(input.equals("Bye"))
					break;
			}
			socket.close();
		} catch (IOException e) {
			System.out.println("socket exception!!!");
			e.printStackTrace();
		}
	}
}
