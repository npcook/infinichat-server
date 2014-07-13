import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.text.*;
import java.util.Date;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException;

@SuppressWarnings("unchecked")
public class Server implements Runnable {

	/*
	 * SQL Tables
	 * users : id, name, password, display
	 * groups: id, name, display
	 * group_members: id, group_id, member_id
	 * friends: id, first_id, second_id, accepted
	 * 
	 */

	private static boolean silentMode = false;
	private static ServerSocket server;
	private static Thread thread;
	private static boolean isRunning = false;
	private static List<Client> clients;
	private static JSONParser parser;
	private static Logger logger;
	private static Connection con;

	public static void main(String[] args){

		logger = new Logger(null);
		parser = new JSONParser();
		clients = new ArrayList<Client>();
		try {
			logger.loadConfig();
		} catch (IOException e) {
			System.out.println("Failed to load config file.");
			e.printStackTrace();
		}

		if (args.length > 0 && args[0].equals("silent"))
			silentMode = true;

		log("Starting up server...\r\nUse\"silent\" to turn off console logging.");

		try {
			server = new ServerSocket(); 
			server.bind(new InetSocketAddress("0.0.0.0", 49520));
			log("Bound server to port 49520");
			con = DriverManager.getConnection(logger.getDatabaseURL(), logger.getDatabaseUser(), logger.getDatabasePass());
			log("Connected to database succesfully");
		} catch (IOException e) {
			log("Failed to bind to the port 49520 at 0.0.0.0, shutting down.");
			e.printStackTrace(logger.getWriter());
			e.printStackTrace();
			System.exit(0);
		} catch (SQLException e) {
			log("Failed to connect to database.");
			e.printStackTrace(logger.getWriter());
			e.printStackTrace();
			System.exit(0);
		}

		isRunning = true;

		thread = new Thread(new Server());
		thread.start();

		while (true){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

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

		if (print && !silentMode)
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

	public static ResultSet executeQuery(String sql, Object... args){

		try {
			PreparedStatement ps = con.prepareStatement(sql);
			for (int i = 0; i < args.length; ++i)
				ps.setObject(i + 1, args[i]);
			return ps.executeQuery();
		} catch (Exception e) {
			if (e instanceof CommunicationsException || e instanceof SQLTransientConnectionException){
				try {
					con.close();
				} catch(Exception ee){ }
				try {
					con = DriverManager.getConnection(logger.getDatabaseURL(), logger.getDatabaseUser(), logger.getDatabasePass());
					log("Reconnection to SQL database successful.");
					return executeQuery(sql, args);
				} catch (Exception ee) {
					log("Attempted to reconnect to database after timeout, but failed:");
					ee.printStackTrace(logger.getWriter());
					ee.printStackTrace();
				}
			} else {
				log("Error when executing SQL : " + sql);
				e.printStackTrace(logger.getWriter());
				e.printStackTrace();
			}
		}

		return null;
	}

	public static int executeUpdate(String sql, Object... args){

		try {
			PreparedStatement ps = con.prepareStatement(sql);
			for (int i = 0; i < args.length; ++i)
				ps.setObject(i + 1, args[i]);
			return ps.executeUpdate();
		} catch (Exception e) {
			if (e instanceof CommunicationsException || e instanceof SQLTransientConnectionException){
				try {
					con.close();
				} catch(Exception ee){ }
				try {
					con = DriverManager.getConnection(logger.getDatabaseURL(), logger.getDatabaseUser(), logger.getDatabasePass());
					log("Reconnection to SQL database successful.");
					return executeUpdate(sql, args);
				} catch (Exception ee) {
					log("Attempted to reconnect to database after timeout, but failed:");
					ee.printStackTrace(logger.getWriter());
					ee.printStackTrace();
				}
			} else {
				log("Error when executing SQL : " + sql);
				e.printStackTrace(logger.getWriter());
				e.printStackTrace();
			}
		}

		return -1;
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

	/**
	 * Converts a string message to a JSONObject
	 * 
	 * @param msg String to be converted
	 * @return JSONObject represented by the string
	 * @throws ParseException If there's an error parsing
	 */
	public static JSONObject parseMsg(String msg) throws ParseException{

		return (JSONObject) parser.parse(msg);

	}

	/**
	 * Creates a JSONObject that is the default reply to a message (result 200, "Success")
	 * 
	 * @param replyObj Message to reply to
	 * @return Reply message
	 */
	public static JSONObject defaultReply(JSONObject replyObj){


		JSONObject reply = new JSONObject();
		reply.put("reply", replyObj.get("message"));
		reply.put("result", 200);
		reply.put("result_message", "Success");
		reply.put("tag", replyObj.get("tag"));

		return reply;

	}

	/**
	 * Creates a JSONObject that is a reply to the given object using the message and tag fields
	 * 
	 * @param o Message to reply to
	 * @return Reply message
	 */
	public static JSONObject reply(JSONObject o){

		JSONObject reply = new JSONObject();
		reply.put("reply", o.get("message"));
		reply.put("tag", o.get("tag"));

		return reply;

	}

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

				log("Login from " + obj.get("username"));

				//UUID.randomUUID().toString()

				String username = (String) obj.get("username");
				ResultSet rs = executeQuery("SELECT * FROM `users` WHERE name = ?", username);
				boolean hasUsername = rs.next(), isLoggedIn = false;

				if (hasUsername){

					String password = rs.getString("password");

					if (password.equals(obj.get("password"))){

						reply = reply(obj);

						for (Client c : clients)
							if (c.username.equals(username)){
								isLoggedIn = true;
								break;
							}

						if (!isLoggedIn){

							//Reply to login giving the client it's display name
							JSONObject loginData = new JSONObject();
							loginData.put("username", username);
							loginData.put("display_name", rs.getString("display"));
							loginData.put("status", obj.get("initial_status"));

							reply.put("result", 200);
							reply.put("result_message", "Success");
							reply.put("me", loginData);

							Client client = new Client(connection, username, (String) loginData.get("display_name"), rs.getInt("id"));

							client.sendMessage(reply);

							clients.add(client);

							log("Clients on server " + clients.size());

							client.start();

							for (Group g : client.getGroups())
								log(g.groupname + "(" + g.display + ") - " + g.users.size() + " - ID " + g.ID);
							
							
							JSONObject selfInfo = new JSONObject();
							JSONArray selfArray = new JSONArray();
							selfInfo.put("message", "detail.users");
							selfArray.add(client.toDetails());
							selfInfo.put("users", selfArray);
							selfInfo.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));

							List<User> frends = client.getFriends();

							for (User friend : frends)
								if (friend.client != null)
									friend.client.sendMessage(selfInfo);

						} else {

							log("Second login attempt.");
							JSONObject error = new JSONObject();
							error.put("reply", "login");
							error.put("result", 407);
							error.put("result_message", "You are already logged in.");
							error.put("tag", obj.get("tag"));
							connection.getOutputStream().write((error.toJSONString() + "\r\n").getBytes("UTF8"));
							connection.getOutputStream().flush();

						}

					} else {

						log("Incorrect password given.");
						JSONObject error = new JSONObject();
						error.put("reply", "login");					
						error.put("result", 406);
						error.put("result_message", "The password you put in is incorrect.");
						error.put("tag", obj.get("tag"));
						connection.getOutputStream().write((error.toJSONString() + "\r\n").getBytes("UTF8"));
						connection.getOutputStream().flush();

					}

				} else {

					log("Username doesn't exist.");
					JSONObject error = new JSONObject();
					error.put("reply", "login");					
					error.put("result", 405);
					error.put("result_message", "The username doesn't exist.");
					error.put("tag", obj.get("tag"));
					connection.getOutputStream().write((error.toJSONString() + "\r\n").getBytes("UTF8"));
					connection.getOutputStream().flush();

				}

			} catch (Exception e) {

				log("Exception thrown when trying to log in client.", false);
				try {
					JSONObject error = new JSONObject();
					error.put("reply", "login");
					error.put("result", 500);
					error.put("result_message", "Server got screwed up.");
					error.put("tag", obj.get("tag"));
					connection.getOutputStream().write((error.toJSONString() + "\r\n").getBytes("UTF8"));
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
