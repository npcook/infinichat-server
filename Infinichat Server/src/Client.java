import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.List;

import org.json.simple.*;


@SuppressWarnings("unchecked")
public class Client implements Runnable {

	Thread thread;
	Socket socket;
	String username;
	String displayname;
	String status;
	boolean isRunning;
	
	public Client(Socket socket, String username, String display){
		
		this.socket = socket;
		this.username = username;
		this.displayname = display;
		status = "Available";
		thread = new Thread(this);
		
	}
	
	public List<Client> getFriends(){
		
		//TODO: Fix with actual friends list
		
		return Server.getClients();
		
	}
	
	public void sendMessage(JSONObject o){
		
		try {
			Server.log(username + " sending\r\n" + o.toJSONString());
			socket.getOutputStream().write((o.toJSONString() + "\r\n").getBytes("UTF8"));
			socket.getOutputStream().flush();
		} catch (Exception e) {
			e.printStackTrace();
			e.printStackTrace(Server.getLogger().getWriter());
			Server.log("Error sending message to client " + username + "\r\n" + o.toJSONString());
		}
		
		
	}
	
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
				chatMsg.put("timestamp", o.get("timestamp"));
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
			
		} else if (o.get("message").equals("logout")){
			
			Server.log("Client " + username + " logged out with \"" + o.get("reason") + "\".");
			disconnect();
			
		} else if (o.get("message").equals("me.status")){
			
			status = (String) o.get("status");
			
			JSONObject statusMsg = new JSONObject(), data = new JSONObject();
			JSONArray dataArray = new JSONArray();
			data.put("username", username);
			data.put("display_name", displayname);
			data.put("status", status);
			data.put("friend", true);
			dataArray.add(data);
			statusMsg.put("message", "detail.users");
			statusMsg.put("users", dataArray);
			
			//TODO: Change to relevant users
			Server.getClients().remove(this);
			List<Client> clients = getFriends();
			for (Client c : clients)
				if (!c.equals(this))
					c.sendMessage(statusMsg);
			
			JSONObject obj = Server.defaultReply(o);
			sendMessage(obj);
			
		} else if (o.get("message").equals("me.name")){
			
			displayname = (String) o.get("display_name");
			
			boolean failed = (Server.executeUpdate("UPDATE `users` SET display = ? WHERE name = ?", displayname, username) == 0);
			JSONObject obj;
			
			if (!failed){
				JSONObject statusMsg = new JSONObject(), data = new JSONObject();
				JSONArray dataArray = new JSONArray();
				data.put("username", username);
				data.put("display_name", displayname);
				data.put("status", o.get("status"));
				data.put("friend", true);
				dataArray.add(data);
				statusMsg.put("message", "detail.users");
				statusMsg.put("users", dataArray);
				
				//TODO: Change to relevant users
				Server.getClients().remove(this);
				List<Client> clients = getFriends();
				for (Client c : clients)
					if (!c.equals(this))
						c.sendMessage(statusMsg);			
				obj = Server.defaultReply(o);
			} else {
				obj = Server.defaultReply(o);
				obj.put("result", 500);
				obj.put("result_message", "Unable to update your display name.");
			}
			sendMessage(obj);
			
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
			disconnect();
		}
		
		while (isRunning){
			
			try {
				
				byte[] data = new byte[1024];
				String message = reader.readLine();
				
				if (message == null){
					Server.log("No data recieved from client " + username + ", terminating.");
					disconnect();
					continue;
				}
				
				Server.log(username + " received\r\n" + message);
				
				JSONObject obj = Server.parseMsg(message);
				
				handleMessage(obj);
				
			} catch(Exception e){
				Server.log("Exception in client " + username);
				disconnect();
				e.printStackTrace();
				e.printStackTrace(Server.getLogger().getWriter());
			}
			
		}
		
	}
	
	void disconnect(){
		
		if (!isRunning)
			return;
		
		isRunning = false;
		JSONObject logoutMsg = new JSONObject(), data = new JSONObject();
		JSONArray dataArray = new JSONArray();
		data.put("username", username);
		data.put("display_name", displayname);
		data.put("status", "Offline");
		data.put("friend", true);
		dataArray.add(data);
		logoutMsg.put("message", "detail.users");
		logoutMsg.put("users", dataArray);
		
		Server.getClients().remove(this);
		List<Client> clients = getFriends();
		for (Client c : clients)
			c.sendMessage(logoutMsg);
		
	}

	public boolean equals(Object o){
		
		if (o instanceof Client)
			return ((Client) o).username.equals(username);
		
		return false;
		
	}
	
	public JSONObject toDetails(){
		
		JSONObject user = new JSONObject();
		user.put("username", username);
		user.put("display_name", displayname);
		user.put("status", status);
		user.put("friend", true);
		return user;
		
	}
	
	public void start(){
		isRunning = true;
		thread.start();
	}
	
}
