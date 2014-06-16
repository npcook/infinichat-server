import java.io.*;

public class Logger {

	private File config, log;
	private PrintWriter writer;
	private String defaultPath;
	private String offlinePath, logPath;
	private String dbUser, dbPass, dbURL;
	
	public Logger(String configPath){
		
		if (configPath == null)
		{
			defaultPath = System.getProperty("user.dir") + "/config/";
			config = new File(defaultPath);
		}
		config.mkdirs();
		config = new File(config.getPath() + "/infinity.config");
		if (!config.exists())
			try {config.createNewFile();} catch (Exception e){ }
		offlinePath = logPath = dbUser = dbPass = dbURL = null;
	}
	
	public void loadConfig() throws IOException {
		
		BufferedReader reader = new BufferedReader(new FileReader(config));
		
		String line = null;
		
		while ((line = reader.readLine()) != null){

			if (line.startsWith("offline-path=")){
				offlinePath = defaultPath + line.substring(13) + (line.substring(13).endsWith("/") ? "" : "/");
			} else if (line.startsWith("log-path="))
				logPath = defaultPath + line.substring(9) + (line.substring(9).endsWith("/") ? "" : "/");
			else if (line.startsWith("database-user="))
				dbUser = line.substring(14);
			else if (line.startsWith("database-pass="))
				dbPass = line.substring(14);
			else if (line.startsWith("database-url="))
				dbURL = line.substring(13);
		}
		
		reader.close();
		
		if (logPath == null)
			logPath = defaultPath;
		log = new File(logPath);
		writer = new PrintWriter(log);
		if (offlinePath == null)
			offlinePath = defaultPath;
		
	}
	
	public File getLogFile(){ return log; }
	
	public PrintWriter getWriter(){ return writer; }
	
	public String getOfflinePath(){ return offlinePath; }

	public String getLogPath() { return logPath; }

	public String getDatabaseUser() { return dbUser; }

	public String getDatabasePass() { return dbPass; }

	public String getDatabaseURL() { return dbURL; }
	
}
