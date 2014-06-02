import java.net.*;

public class Server {

	private static ServerSocket socket;
	
	public static void main(String[] args){
		
		
		/*TODO: Add in config settings, NOOB :3*/
		
		try {
			server = new ServerSocket(); 
			server.bind(new InetSocketAddress("0.0.0.0", 49520));
			
		} catch (IOException e) {
			log("Failed to bind to the port 49520 at 0.0.0.0, shutting down.");
			e.printStackTrace(writer);
			e.printStackTrace();
			System.exit(0);
		}
		
	}
	
	
}
