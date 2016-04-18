import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

public class MyLogger {
	static private FileHandler logTxt;
	static private LogFormatter formatterText;
	private static Logger logger;

	static public void setup() throws IOException {

		logger = Logger.getLogger(MyLogger.class.getName());
		logger.setUseParentHandlers(false);

		// suppress the logging output to the console
		// Logger rootLogger = Logger.getGlobal();
		Handler[] handlers = logger.getHandlers();
		if (handlers != null && handlers.length > 0 && handlers[0] instanceof ConsoleHandler) {
			logger.removeHandler(handlers[0]);
		}

		logger.setLevel(Level.INFO);

        File directory = new File("peer_" + Configuration.getComProp().get("peerId"));
        if(!directory.isDirectory())
            directory.mkdir();
        logTxt = new FileHandler( directory.getPath() + File.separator + "log_peer_"
                + Configuration.getComProp().get("peerId") + ".log");
		formatterText = new LogFormatter();
		logTxt.setFormatter(formatterText);
		logger.addHandler(logTxt);
	}

	public static Logger getMyLogger() {
		return logger;
	}
}
