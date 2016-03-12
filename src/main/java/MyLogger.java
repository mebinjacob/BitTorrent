import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class MyLogger {
	static private FileHandler logTxt;
	static private LogFormatter formatterText;
	
	static public void setup() throws IOException {
		
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
		
		logTxt = new FileHandler("log_peer_" + Configuration.getComProp().get("peerId") + ".log");
		formatterText = new LogFormatter();
		logTxt.setFormatter(formatterText);
		logger.addHandler(logTxt);
	}
}
