import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PropertyFileReader {
	private static final Logger LOGGER = Logger.getLogger(PropertyFileReader.class.getName());
	Properties prop = new Properties();
	public PropertyFileReader(File p){
		try (FileInputStream input = new FileInputStream("config.prioperties"))
		{
			prop.load(input);
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.SEVERE, "File not found:- configuration file!");
			e.printStackTrace();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "IO Exception while reading file!!");
			e.printStackTrace();
		}
		
	}
}
