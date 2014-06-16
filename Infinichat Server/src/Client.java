import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.List;

import org.json.simple.*;


public class Client implements Runnable {

	Thread thread;
	Socket socket;
	String username;
	boolean isRunning;
	
	public Client(Socket socket, String username){
		
		this.socket = socket;
		this.username = username;
		thread = new Thread(this);
		
	}
	
	public void sendMessage(JSONObject o){
		
		try {
			socket.getOutputStream().write((o.toJSONString() + "\r\n").getBytes("UTF8"));
			socket.getOutputStream().flush();
		} catch (Exception e) {
			e.printStackTrace();
			e.printStackTrace(Server.getLogger().getWriter());
			Server.log("Error sending message to client " + username + "\r\n" + o.toJSONString());
		}
		
		
	}
	
	@SuppressWarnings("unchecked")
	public void handleMessage(JSONObject o){	
		
		if (o.get("message").equals("logout")){
			
			sendMessage(Server.defaultReply(o));
			
		} else if (o.get("message").equals("chat.user")){
			
			try {
				JSONObject reply = Server.defaultReply(o), chatMsg;
				
				Client c = Server.getClient((String) o.get("to"));
				chatMsg = new JSONObject();
				chatMsg.put("message", "chat.user");
				chatMsg.put("from", username);
				chatMsg.put("body", o.get("body"));
				chatMsg.put("font", o.get("font"));
				chatMsg.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));
				
				c.sendMessage(chatMsg);
				sendMessage(reply);
			} catch (Exception e){
				e.printStackTrace(Server.getLogger().getWriter());
				e.printStackTrace();
				Server.log("Chat message error");
				JSONObject reply = Server.defaultReply(o);
				reply.put("result", 500);
				reply.put("result_message", "Something happened");
				sendMessage(reply);
			}
			
		} else if (o.get("message").equals("detail.users")){
			
			try {
				
				JSONArray usersToGet = (JSONArray) o.get("users");
				
				JSONObject message = new JSONObject();
				message.put("message", "detail.users");
				JSONArray users = new JSONArray();
				
				List<Client> clients = Server.getClients();
				
				for (Client c : clients){
					if (usersToGet.contains(c.username)){
						users.add(c.toDetails());
					}
				}
				message.put("users", users);
				message.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));
				sendMessage(message);
				
			} catch (Exception e){
				e.printStackTrace(Server.getLogger().getWriter());
				e.printStackTrace();
				Server.log("Chat message error");
				JSONObject reply = Server.defaultReply(o);
				reply.put("result", 500);
				reply.put("result_message", "Something happened");
				sendMessage(reply);
			}
			
		}
		
	}
	
	@Override
	public void run(){
		
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF8"));
		} catch (Exception e) {
			e.printStackTrace(Server.getLogger().getWriter());
			e.printStackTrace();
			Server.log("Error initing reader on client " + username);
			isRunning = false;
		}
		
		while (isRunning){
			
			try {
			
				Server.log("Waiting for input");
				
				byte[] data = new byte[1024];
				String message = reader.readLine();
				
				
				if (message == null){
					isRunning = false;
					Server.log("No data recieved from client.");
					continue;
				}
				
				Server.log("Got some stuff");
				Server.log(message);
				JSONObject obj = Server.parseMsg(message);
				
				handleMessage(obj);
				
			} catch(Exception e){
				Server.log("Exception in client " + username);
				isRunning = false;
				e.printStackTrace();
			}
			
		}
		
	}

	public JSONObject toDetails(){
		
		JSONObject user = new JSONObject();
		user.put("username", username);
		user.put("display_name", "C9." + username + ".HyperX<CRUMBLING>");
		user.put("status", "Available");
		user.put("friend", true);
		return user;
		
	}
	
	public void start(){
		isRunning = true;
		thread.start();
	}
	
}
