import java.util.List;

public class Group {

	List<User> users;
	int ID;
	String groupname;
	String display;
	
	public Group(List<User> users, int ID, String groupname, String display){
		this.users = users;
		this.ID = ID;
		this.groupname = groupname;
		this.display = display;
	}
	
}
