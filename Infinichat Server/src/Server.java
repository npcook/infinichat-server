import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.Date;

import javax.xml.bind.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Server implements Runnable {

	private static ServerSocket server;
	private static Thread thread;
	private static boolean isRunning = false;
	private static List<Client> clients;
	private static JSONParser parser;
	private static Logger logger;
	
	public static void log(String str){

		log(str, true);

	}
	
	public static Logger getLogger(){ return logger; }
	
	static final String HEXES = "0123456789ABCDEF";
	public static String getHex( byte [] raw ) {
		if ( raw == null ) {
			return null;
		}
		final StringBuilder hex = new StringBuilder( 2 * raw.length );
		for ( final byte b : raw ) {
			hex.append(HEXES.charAt((b & 0xF0) >> 4))
			.append(HEXES.charAt((b & 0x0F)));
		}
		return hex.toString();
	}
	
	private final static char[] ALPHABET = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
	"abcdefghijklmnopqrstuvwxyz0123456789+/").toCharArray();
	public static String encodeBase64(String str){
		byte[] buf = str.getBytes();
		int size = buf.length;
		char[] ar = new char[((size + 2) / 3) * 4];
		int a = 0;
		int i=0;
		while(i < size){
			byte b0 = buf[i++];
			byte b1 = (i < size) ? buf[i++] : 0;
			byte b2 = (i < size) ? buf[i++] : 0;

			int mask = 0x3F;
			ar[a++] = ALPHABET[(b0 >> 2) & mask];
			ar[a++] = ALPHABET[((b0 << 4) | ((b1 & 0xFF) >> 4)) & mask];
			ar[a++] = ALPHABET[((b1 << 2) | ((b2 & 0xFF) >> 6)) & mask];
			ar[a++] = ALPHABET[b2 & mask];
		}
		switch(size % 3){
		case 1: ar[--a]  = '=';
		case 2: ar[--a]  = '=';
		}
		return new String(ar);
	}
	
	public static void log(String str, boolean print){

		if (print)
			System.out.println(str);

		try {
			FileWriter writer = new FileWriter(logger.getLogFile(), true);

			writer.write(new SimpleDateFormat("[MM/dd/yy HH:mm:ss]").format(new Date()) + str + "\r\n");

			writer.close();
		} catch(Exception e){
			e.printStackTrace(logger.getWriter());
			e.printStackTrace();
		}


	}
	
	public static List<Client> getClients(){
		
		return clients;
		
	}
	
	public static Client getClient(String username){
		
		Iterator<Client> citer = clients.iterator();
		while (citer.hasNext()){
			Client c = citer.next();
			if (c.username.equals(username))
				return c;
		}
		
		return null;
		
	}
	
	public static JSONObject parseMsg(String msg) throws ParseException{
		
		return (JSONObject) parser.parse(msg);
		
	}
	
	public static JSONObject defaultReply(JSONObject replyObj){
		

		JSONObject reply = new JSONObject();
		reply.put("reply", replyObj.get("message"));
		reply.put("result", 200);
		reply.put("result_message", "Success");
		reply.put("tag", replyObj.get("tag"));
		
		return reply;
		
	}
	
	public static JSONObject reply(JSONObject o){
		
		JSONObject reply = new JSONObject();
		reply.put("reply", o.get("message"));
		reply.put("tag", o.get("tag"));
		
		return reply;
		
	}
	
	public static void main(String[] args){
		
		/*TODO: Add in config settings, NOOB :3*/
		
		Server s = new Server();
		
		try {
			server = new ServerSocket(); 
			server.bind(new InetSocketAddress("0.0.0.0", 49520));
			
		} catch (IOException e) {
			log("Failed to bind to the port 49520 at 0.0.0.0, shutting down.");
			e.printStackTrace(logger.getWriter());
			e.printStackTrace();
			System.exit(0);
		}
		
		isRunning = true;
		
		thread = new Thread(s);
		thread.start();
		
		while (true){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public Server(){
		
		logger = new Logger(null);
		parser = new JSONParser();
		clients = new ArrayList<Client>();
		try {
			logger.loadConfig();
		} catch (IOException e) {
			System.out.println("Failed to load config file.");
			e.printStackTrace();
		}
		
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void run(){
		
		log("We Did It Seerver!!!");
		
		while (isRunning){

			Socket connection = null;

			try {
				connection = server.accept();
			} catch (Exception e) {

				log("Exception thrown", false);
				//e.printStackTrace(writer);
				e.printStackTrace();
				continue;

			}

			if (connection == null)
				continue;

			log("Accepted new connection from " + connection.getInetAddress().getHostAddress());

			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			byte[] data = new byte[1024];
			int len = 0;

			try {

				len = connection.getInputStream().read(data);
				if (len > 0)
					stream.write(data, 0, len);
				else
					continue;

			} catch (Exception e) {

				log("Exception thrown", false);
				//e.printStackTrace(writer);
				e.printStackTrace();
				continue;

			}

			data = null;
			String message = null;
			JSONObject obj = null, reply = null;
			
			try {
				
				message = new String(stream.toByteArray(), "UTF8");
				stream.close();
				obj = (JSONObject) parser.parse(message);
				
				log("Login from " + obj.get("username") + "\nPW: " + obj.get("password") + "\nstatus: " + obj.get("initial_status"));
				
				reply = reply(obj);
				
				JSONObject loginData = new JSONObject();
				loginData.put("username", obj.get("username"));
				
				/* TODO: Actually do this thing                =========================           */
				
				loginData.put("display_name", "C9." + obj.get("username") + ".HyperX<CRUMBLING>");
				loginData.put("status", obj.get("initial_status"));
				
				reply.put("result", 200);
				reply.put("result_message", "Success");
				reply.put("me", loginData);
				
				log(reply.toJSONString());
				
				connection.getOutputStream().write((reply.toJSONString() + "\r\n").getBytes("UTF8"));
				connection.getOutputStream().flush();
				
				Client client = new Client(connection, (String) obj.get("username"));
				
				clients.add(client);
				
				log("Clients on server " + clients.size());
				
				client.start();
				
				Thread.sleep(2000);
				
				JSONObject jmessage = new JSONObject(), selfInfo = new JSONObject();
				JSONArray users = new JSONArray(), selfArray = new JSONArray();
				selfInfo.put("message", "detail.users");
				selfArray.add(client.toDetails());
				selfInfo.put("users", selfArray);
				selfInfo.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));
				jmessage.put("message", "detail.users");
				
				for (Client c : clients){
					if (!c.username.equals(client.username)){
						users.add(c.toDetails());
						log("Sending my info to " + c.username + "\r\n" + selfInfo.toJSONString());
						c.sendMessage(selfInfo);
					}
				}
				jmessage.put("users", users);
				jmessage.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));
				client.sendMessage(jmessage);
				
				log("Sending client info to " + client.username + "\r\n" + jmessage.toJSONString());
				
			} catch (Exception e) {

				log("Exception thrown when trying to log in client.", false);
				try {
					JSONObject error = new JSONObject();
					error.put("reply", "login");
					error.put("result", 500);
					error.put("result_message", "Server got screwed up.");
					error.put("tag", obj.get("tag"));
					connection.getOutputStream().write(error.toJSONString().getBytes("UTF8"));
					connection.getOutputStream().flush();
				} catch (Exception e1){
					e.printStackTrace();
					e.printStackTrace(logger.getWriter());
				}
				e.printStackTrace(logger.getWriter());
				e.printStackTrace();
				continue;

			}

			
			
		}
		
	}
	
}
