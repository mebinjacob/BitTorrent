import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
/**
 * @author Mebin
 * Java class to read configuration files...
 */
public class Configuration {
	private static final Logger LOGGER = Logger.getLogger(Configuration.class.getName());
	
	private static Map<Integer, String> peerProp = null;
	private static Map<String, String> commonProp = null;
	private static Configuration conf = null;
	public static Map<String, String> getComProp(){
		if(conf == null){
			conf = new Configuration();
		}
		return commonProp;
	}
	
	public static Map<Integer, String> getPeerProp(){
		if(conf == null){
			conf = new Configuration();
		}
		return peerProp;
	}
	
	
	private String peerInfoFileName = "PeerInfo.cfg";
	private String commonFileName = "Common.cfg";
	public Configuration() {
		FileInputStream fis;
		try {
			peerProp = new HashMap<Integer, String>();
			commonProp = new HashMap<String, String>();
			File file = new File(commonFileName);
			fis = new FileInputStream(new File(commonFileName));
			//Construct BufferedReader from InputStreamReader
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		 
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] split = line.split(" ");
				commonProp.put(split[0], split[1]);
			}
		 
			br.close();
			
			fis = new FileInputStream(new File(peerInfoFileName));
			br = new BufferedReader(new InputStreamReader(fis));
			 
			String line1 = null;
			while ((line1 = br.readLine()) != null) {
				String[] split = line1.split(" ");
				System.out.println("value being put is " + split[0]);
				peerProp.put(Integer.parseInt(split[0]), line1);
			}
		 
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 
		
	}
	
	
	
}



