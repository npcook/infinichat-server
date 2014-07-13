
public class User {

	Client client;
	int ID;
	String username;
	String display;
	
	public User(Client client, int ID, String username, String display){
		this.client = client;
		this.ID = ID;
		this.username = username;
		this.display = display;
	}
	
}
