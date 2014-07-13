import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.*;


@SuppressWarnings("unchecked")
public class Client implements Runnable {

	/*
	 * SQL Tables
	 * users : id, name, password, display
	 * groups: id, name, display
	 * group_members: id, group_id, member_id
	 * friends: id, first_id, second_id, accepted
	 */

	Thread thread;
	Socket socket;
	String username;
	String displayname;
	String status;
	int ID;
	boolean isRunning;

	public Client(Socket socket, String username, String display, int ID){

		this.socket = socket;
		this.username = username;
		this.displayname = display;
		this.ID = ID;
		status = "Available";
		thread = new Thread(this);

	}

	public List<User> getFriends(){

		List<User> friendsList = new ArrayList<User>();

		ResultSet friends = Server.executeQuery("SELECT users.* FROM friends INNER JOIN users ON friends.second_id = users.id WHERE (friends.first_id = ?)", ID);
		try {
			while (friends.next())
				friendsList.add(new User(Server.getClient(friends.getString("name")), friends.getInt("id"), friends.getString("name"), friends.getString("display")));
			friends.close();
		} catch (SQLException e) {
			e.printStackTrace();
			e.printStackTrace(Server.getLogger().getWriter());
			Server.log("Error finding client's friends: " + username);
		}

		return friendsList;
	}

	public List<Group> getGroups(){

		List<Group> groupList = new ArrayList<Group>();
		ResultSet groups = Server.executeQuery("SELECT groups.* FROM group_members INNER JOIN groups ON group_members.member_id = ?", ID);
		try {
			while (groups.next()){
				ResultSet members = Server.executeQuery("SELECT users.* FROM group_members INNER JOIN users ON group_members.member_id = users.id WHERE (group_members.group_id = ?)", groups.getInt("id"));
				List<User> memberList = new ArrayList<User>();
				while (members.next())
					memberList.add(new User(Server.getClient(members.getString("name")), members.getInt("id"), members.getString("name"), members.getString("display")));
				groupList.add(new Group(memberList, groups.getInt("id"), groups.getString("name"), groups.getString("display")));
				members.close();
			}
			groups.close();
		} catch (SQLException e) {
			e.printStackTrace();
			e.printStackTrace(Server.getLogger().getWriter());
			Server.log("Error finding client's groups: " + username);
		}

		return groupList;
	}

	public Group getGroup(String name){

		Group group = null;
		ResultSet groups = Server.executeQuery("SELECT * FROM groups WHERE name = ?", name);
		try {
			if (groups.next()){
				ResultSet members = Server.executeQuery("SELECT users.* FROM group_members INNER JOIN users ON group_members.member_id = users.id WHERE (group_members.group_id = ?)", groups.getInt("id"));
				List<User> memberList = new ArrayList<User>();
				while (members.next())
					memberList.add(new User(Server.getClient(members.getString("name")), members.getInt("id"), members.getString("name"), members.getString("display")));
				group = new Group(memberList, groups.getInt("id"), groups.getString("name"), groups.getString("display"));
				members.close();
			}
			groups.close();
		} catch (SQLException e) {
			e.printStackTrace();
			e.printStackTrace(Server.getLogger().getWriter());
			Server.log("Error finding client " + username + "'s group: " + name);
		}

		return group;
	}

	public void sendMessage(JSONObject o){

		try {
			//Server.log(username + " sending\r\n" + o.toJSONString());
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
				if (c != null){
					chatMsg = new JSONObject();
					chatMsg.put("message", "chat.user");
					chatMsg.put("from", username);
					chatMsg.put("body", o.get("body"));
					chatMsg.put("font", o.get("font"));
					chatMsg.put("timestamp", o.get("timestamp"));
					chatMsg.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));
	
					c.sendMessage(chatMsg);
				}
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

		} else if (o.get("message").equals("chat.group")){

			try {
				JSONObject reply = Server.defaultReply(o), chatMsg = new JSONObject();

				Group g = getGroup((String) o.get("to"));
				if (g != null){
					chatMsg.put("message", "chat.group");
					chatMsg.put("from", username);
					chatMsg.put("via", g.groupname);
					chatMsg.put("body", o.get("body"));
					chatMsg.put("font", o.get("font"));
					chatMsg.put("timestamp", o.get("timestamp"));
					chatMsg.put("tag", "_" + Double.toString((Math.random() * 0xDEADBEEF)));
					
					for (User user : g.users)
						if (!user.username.equals(username) && user.client != null)
							user.client.sendMessage(chatMsg);
				}
				
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

			List<User> firendss = getFriends();
			for (User friend : firendss)
				if (friend.client != null)
					friend.client.sendMessage(statusMsg);

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
				data.put("status", status);
				data.put("friend", true);
				dataArray.add(data);
				statusMsg.put("message", "detail.users");
				statusMsg.put("users", dataArray);

				List<User> friends = getFriends();
				for (User friend : friends)
					if (friend.client != null)
						friend.client.sendMessage(statusMsg);			
				obj = Server.defaultReply(o);
			} else {
				obj = Server.defaultReply(o);
				obj.put("result", 500);
				obj.put("result_message", "Unable to update your display name.");
			}
			sendMessage(obj);

		} else if (o.get("message").equals("list.friends")){

			List<User> friends = getFriends();
			JSONArray dataArray = new JSONArray();
			for (User friend : friends){
				JSONObject data = new JSONObject();
				data.put("username", friend.username);
				data.put("display_name", friend.display);
				data.put("status", friend.client == null ? "Offline" : friend.client.status);
				data.put("friend", true);
				dataArray.add(data);
			}

			JSONObject message = new JSONObject();
			message.put("message", "detail.users");
			message.put("users", dataArray);

			sendMessage(message);

			message = Server.defaultReply(o);
			sendMessage(message);

		} else if (o.get("message").equals("list.groups")){

			List<Group> groups = getGroups();
			JSONArray dataArray = new JSONArray();
			for (Group group : groups){
				JSONArray memberArray = new JSONArray();
				JSONObject data = new JSONObject();
				data.put("groupname", group.groupname);
				data.put("display_name", group.display);

				for (User member : group.users)
					memberArray.add(member.username);

				data.put("members", memberArray);
				data.put("member", true);
				dataArray.add(data);
			}

			JSONObject message = new JSONObject();
			message.put("message", "detail.groups");
			message.put("groups", dataArray);

			sendMessage(message);

			message = Server.defaultReply(o);
			sendMessage(message);

		} else if (o.get("message").equals("typing.user")){
			
			JSONObject defaultReply = Server.defaultReply(o);
			JSONObject message = new JSONObject();
			
			Client client = Server.getClient((String) o.get("to"));
			if (client != null){
				message.put("message", "typing.user");
				message.put("from", username);
				message.put("starting", o.get("starting"));
				client.sendMessage(message);
			}
			sendMessage(defaultReply);
			
		} else if (o.get("message").equals("typing.group")){
			
			JSONObject defaultReply = Server.defaultReply(o);
			JSONObject message = new JSONObject();
			
			Group group = getGroup((String) o.get("to"));
			if (group != null){
				message.put("message", "typing.group");
				message.put("from", username);
				message.put("via", o.get("to"));
				message.put("starting", o.get("starting"));
				for (User u : group.users)
					if (!u.username.equals(username) && u.client != null)
						u.client.sendMessage(message);
			}
			sendMessage(defaultReply);
			
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

				//Server.log(username + " received\r\n" + message);

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
		List<User> friends = getFriends();
		for (User friend : friends)
			if (friend.client != null)
				friend.client.sendMessage(logoutMsg);

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
