import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;

// one handler per connected client
public class ClientHandler implements Runnable {
    private Server server; // server client belongs to
    private Socket socket; // client socket for TCP comm
    private int clientID;
    private String clientNickname; // optional nickname - default as null

    private volatile String status;
    private volatile long lastActivityTime; // last time client had any input

    private Channel currentChannel; // store channel user is currently in
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean runningClientHandler = true; // need to volatile here so handler thread sees updates to it e.g. from shutdown()

    public ClientHandler(Server server, Socket socket, int clientID,String status, Channel currentChannel) {
        this.server = server;
        this.socket = socket;
        this.clientID = clientID;
        this.clientNickname = null;
        this.status = status;
        this.currentChannel = currentChannel;
        this.lastActivityTime = System.currentTimeMillis();
    }


    // Thread function for handling client connections
    @Override
    public void run() {
        try {

            status = "Online"; // client has connected set status
            lastActivityTime = System.currentTimeMillis(); // set current activity time

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            startIdleCheck();

	    // thread reading loop
            while (runningClientHandler & !socket.isClosed()) {
                String message = in.readLine(); // read client message from MessageSender

                if (!runningClientHandler) break;
                // null exit
                if  (message == null) {
                    break;
                }

                // updates last activity time
                // deals with status change
                recordActivity();

                if (message.isEmpty()) {
                    continue;
                }
                // client exit condition
                if (message.equalsIgnoreCase("/exit")) {
                    break;
                }

                // create method in Server, passing in client id and the client message over to server which will then distribute message to all other clients
                server.handleIncomingMessage(clientID, message,getStatus(),this.currentChannel);

            }
        } catch (IOException e) {
            if (socket != null && !socket.isClosed()) {
                System.out.println("Client(" + clientID + ") error: " + e.getMessage());
            }
        } finally {
            closeConnection(); // close socket
            status = "Offline";
            // remove from server list and pass channel they were in
            server.removeClient(this,currentChannel);
        }
    }

    // allows server to write messages to client
    // when server broadcasting will loop through all handlers and call handler.send(message)
    public void send(String message){
        synchronized (this) { // prevents 2 threads writing to same client at same time
            if (out != null){ // check stream exists
                out.println(message);
            }
        }
    }


    // finish the connection
    public void closeConnection(){
        try{
            if (in !=null){
                in.close(); // close input stream
            }
        } catch (IOException e){
            System.out.println("Closing of Input Stream Failed: " +e.getMessage());
        }

        if (out != null){
            out.close(); // close output stream
        }

        try{
            if(socket != null && !socket.isClosed()){
                socket.close(); // close socket
            }
        } catch (IOException e){
            System.out.println("Closing of Socket Failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        try {
            runningClientHandler = false;
            if (socket != null && !socket.isClosed()) {
                socket.shutdownInput();
                socket.shutdownOutput();
                socket.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean updateStatus(String newStatus){

        if (newStatus == null){
            return false;
        }

        String s = newStatus.trim().toLowerCase(); // normalise input from user

        if(s.equals("busy")){
            setStatusBusy();
            return true;
        }

        return false;
    }

    public synchronized void setStatusIdle(){
        this.status = "Idle";
    }

    // background thread checking if user status should change to idle
    private void startIdleCheck(){
        Thread idleThread = new Thread(() ->{
            while (runningClientHandler){
                try{
                    Thread.sleep(Constants.CLIENT_IDLE_CHECK_INTERVAL_MS); // wait before next check
                } catch (InterruptedException e){
                    break;
                }

                // stop thread if handler stopped
                if (!runningClientHandler){
                    break;
                }

                long inactiveTime = System.currentTimeMillis() - lastActivityTime; // check client idle time

                // if inactive for idle time change status to idle if not already
                if (inactiveTime >= Constants.CLIENT_IDLE_TIMEOUT_MS && status.equals("Online")) {
                    setStatusIdle();
                    send("Status changed to Idle");
                }
            }
        });

        idleThread.setDaemon(true); // does not keep program alive on its own
        idleThread.start();
    }

    private synchronized void recordActivity() {
        // update activity time
        lastActivityTime = System.currentTimeMillis();

        // client back online
        if (status.equals("Idle")){
            status = "Online";
        }
    }


    public String getStatus(){return this.status;}
    public void setStatusBusy(){this.status = "Busy";}

    public void changeChannel(Channel newChannel) {
        this.currentChannel = newChannel;
    }

    public Channel getCurrentChannel() {
        return currentChannel;
    }

    public int getClientID(){return clientID;}

    // can return null
    public String getClientNickname(){return this.clientNickname;}
    public void setClientNickname(String newNickname){this.clientNickname = newNickname;}

}
