import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import CustomDataStructures.MyConcurrentHashMap;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;


public class Server {
    // both volatile to prevent loops running longer than expected when shutdown() called
    private volatile boolean serverRunning = true;
    private volatile boolean shuttingDown = false;

    private AtomicInteger nextClientID; // store id for the next client that joins - shared between threads
    private final int port;

    private ServerSocket serverSocket; // server socket
    private List<ClientHandler> clients; // list of connected client's handlers
    private List<Channel> channels ;// store list of all known channels

    // stores nicknames for all clients on server
    // custom implementation of ConcurrentHashMap
    private MyConcurrentHashMap<String,ClientHandler> clientNicknameList = new MyConcurrentHashMap<>();

    // hashmap for channel file locks
    // locks for channel history files
    private final MyConcurrentHashMap<String, ReentrantReadWriteLock> channelHistoryFileLocks = new MyConcurrentHashMap<>();
    private final ReentrantReadWriteLock channelListFileLock = new ReentrantReadWriteLock(); // lock for file with names of all channels

    private LineReader reader;


    public Server(int port) {
        this.port = port;
        // ensure client list cannot be accessed simultaneously by multiple threads
        this.clients = Collections.synchronizedList(new ArrayList<>());
        this.nextClientID = new AtomicInteger(1); // startServer counting ID's from 1
        this.channels = Collections.synchronizedList(new ArrayList<>());
    }

    public void startServer() {
        // default lobby channel - always exists on server
        String mainChannelName = "main";
        String mainChannelDescription = "default hangout spot";
        try {
            // creates server socket on port
            serverSocket = new ServerSocket(port);
            loadChannels();
            // check we have a main channel
            if (getChannelByName(mainChannelName) == null){
                // file was non-empty but main wasn't in it
                // create main channel
                Channel main = new Channel(mainChannelName, mainChannelDescription);
                channels.add(main);
                saveChannel(main);
            }

            System.out.println("Server started on port:" + port);
            System.out.println("Waiting for a client...");

            listenForServerCommands();

            // startServer listening for new clients
            while (serverRunning) {

                Socket socket = serverSocket.accept();

                // assign id and increment (atomic)
                int clientID = nextClientID.getAndIncrement();

                String status = "Online";

                // create handler for this client and dump them into main channel
                Channel mainChannel = this.getChannelByName(mainChannelName);
                // client not online
                ClientHandler handler = new ClientHandler(this, socket, clientID,status,mainChannel);

                // add this user to channel's user list
                // auto update size
                mainChannel.addNewUser(handler);

                clients.add(handler); // add new client handler to list

                serverLog("Client (" + clientID + ") connected to channel ["+mainChannel.getChannelName()+"] " +
                        "(" + socket.getRemoteSocketAddress()+")");

                broadcast("Client ("+clientID+") joined the server.",clientID,mainChannel);

                // create new thread for handler and startServer
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            if (serverRunning){
                System.out.println("NicknameTestServer error: " + e.getMessage());
            }else {
                // server termination message
                System.out.println("NicknameTestServer stopped.");
            }
        } finally {
            closeServerSocket();
        }
    }

    // ClientHandler calls this when it recieve user input
    public void handleIncomingMessage(int clientID, String message, String status,Channel broadcastChannel) {
        if(message.startsWith("/join")){
            String[] split = message.split(" ");
            if (split.length != 2) {
                broadcastToClient("Usage: /join <channelName>", clientID, broadcastChannel);
                return;
            }
            String newChannelName = split[1];
            Channel channelToJoin = getChannelByName(newChannelName);
            if (channelToJoin == null) {
                broadcastToClient("Channel does not exist.", clientID, broadcastChannel);
                return;
            }

            ClientHandler joiningClient = getClientByID(clientID);
            if (joiningClient == null) return;

            // client already in channel check
            if (!changeClientChannel(joiningClient, channelToJoin)) return;

//            Object channelFileLock = getChannelFileLock(newChannelName);
            String channelFileName = newChannelName +".json";
            LinkedList<String> historyMessages = null;
            File channelFile = new File(channelFileName);

            ReentrantReadWriteLock readWriteLock = getChannelFileLock(newChannelName);

            // obtain lock - MyConcurrentHashMap is thread safe
            readWriteLock.readLock().lock();

            // critical
            try {
                // last 10 (pre-formatted) messages
                // excludes most recent user joining message
                historyMessages = getLastTenHistoryItems(channelFile);
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                readWriteLock.readLock().unlock();
            }
            // end critical section


            // possible null if IOException thrown by getLastTenItems
            if (historyMessages != null){
                StringBuilder stringBuilder = new StringBuilder();

                stringBuilder.append("\n");
                stringBuilder.append(Constants.RED); // history coloured red

                for (String msg : historyMessages){
                    stringBuilder.append(msg).append('\n'); // "<formatted message>\n, ...."
                }

                stringBuilder.append(Constants.RESET); // rest term colour

                String singleHistoryMessage = stringBuilder.toString();
                broadcastToClient(singleHistoryMessage,clientID,channelToJoin);

            }else{
                broadcastToClient("No history messages available.",clientID,channelToJoin);
            }
            return;

            // Status change
        }else if(message.startsWith("/status-change")){
            // split command and args
            String [] split = message.trim().split("\\s+",2);
            if (split.length != 2){
                broadcastToClient("Usage: /status-change <busy>",clientID,broadcastChannel);
                return;
            }

            String newStatus = split[1].trim();
            ClientHandler client = getClientByID(clientID);

            if (client == null) return;

            // check if valid status
            if (!client.updateStatus(newStatus)){
                broadcastToClient("Invalid status. Use: busy",clientID,broadcastChannel);
                return;
            }

            broadcastToClient("Status set to "+client.getStatus(),clientID,broadcastChannel);
            return;

            // set nickname
        }else if (message.startsWith("/nick")){
            String[] split = message.trim().split("\\s+",2);

            if (split.length != 2){
                broadcastToClient("Usage: /nick <nickname>",clientID,broadcastChannel);
                return;
            }
            String newClientNickname = split[1].trim();

            // all nicknames must conform to pattern
            if (!Constants.VALID_CLIENT_NICKNAME_PATTERN.matcher(newClientNickname).matches()){
                broadcastToClient(
                        "Invalid nickname. Must be unique, between 2 and 15 characters, '-', '_', and numbers allowed."
                        , clientID, broadcastChannel);
                return;
            }

            ClientHandler client = getClientByID(clientID);
            if (client == null) return;

            // may be null
            String oldClientNickname = client.getClientNickname();

            // all nickname must be unique
            ClientHandler existing = clientNicknameList.putIfAbsent(newClientNickname,client);
            if (existing != null){
                broadcastToClient("A user already has this nickname!",clientID,broadcastChannel);
                return;
            }

            // remove old client nick if exists - 1 nick per client
            if (oldClientNickname != null){
                clientNicknameList.remove(oldClientNickname,client);
            }

            client.setClientNickname(newClientNickname);

            String clientNicknameUpdateMessage = (oldClientNickname == null) ?
                    "Client ("+clientID+") changed their nickname to '"+newClientNickname+"'":
                    "'"+ oldClientNickname +"' changed their nickname to '"+newClientNickname+"'";

            String serverNicknameUpdateMessage = (oldClientNickname == null) ?
                    "Client ("+clientID+") changed their nickname to '"+newClientNickname+"'":
                    "Client ("+ clientID +") '"+ oldClientNickname +"' changed their nickname to '"+newClientNickname+"'";

            serverLog(serverNicknameUpdateMessage);

            // set nickname and update server list of nicks
            client.setClientNickname(newClientNickname);
            clientNicknameList.put(newClientNickname,client);

            broadcastToClient("Nickname set to '"+client.getClientNickname()+"'", clientID, broadcastChannel);

            // inform users of client nickname change - context
            broadcast(clientNicknameUpdateMessage,clientID,broadcastChannel);

            // save message for history
            try{
                saveMessage(clientID,newClientNickname,status,clientNicknameUpdateMessage,true,broadcastChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;

            // p2p signals peer to PEER peers peer
        } else if (message.startsWith("/signal-file")){
            String[] split = message.split(" ", 5);
            // I've formatted it to /signal-file user ip port filename
            if (split.length == 5){
                String targetuser = split[1];
                String ip = split[2];
                String port = split[3];
                String filename = split[4];

                // sender info
                ClientHandler sender = getClientByID(clientID);
                if (sender == null) return;
                // get sendername and uhh if they dont have nickname get their id
                String sendername = (sender.getClientNickname() != null) ? sender.getClientNickname() : String.valueOf(clientID);

                // find target on server
                ClientHandler targetclient = null;
                synchronized (clients){
                    for (ClientHandler cl : clients) {
                        if (targetuser.equals(cl.getClientNickname()) || targetuser.equals(String.valueOf(cl.getClientID()))) {
                            targetclient = cl;
                            break;
                        }
                    }
                }

                if (targetclient != null) {
                    targetclient.send("/receive-file " + sendername + " " + ip + " " + port + " " + filename);
                    broadcastToClient("Signaling " + targetuser + " for file transfer ", clientID, broadcastChannel);
                } else {
                    broadcastToClient("User '" + targetuser + "' not found.", clientID, broadcastChannel);
                }
            }
            return;

            // p2p signals video streaming
        } else if (message.startsWith("/signal-stream")){

            String[] split = message.split(" ", 5); // expected: target user, ip, port, filename

            if (split.length == 5){
                String targetuser = split[1];
                String ip = split[2];
                String port = split[3];
                String filename = split[4];

                // sender info
                ClientHandler sender = getClientByID(clientID);
                if (sender == null) {
                    return;
                }

                // use nickname if sender has one otherwise client id
                // dont understand this exactly need to ask shayan to explain
                String sendername = (sender.getClientNickname() != null) ? sender.getClientNickname() : String.valueOf(clientID);

                // find target on server
                ClientHandler targetclient = null;
                synchronized (clients){
                    for (ClientHandler cl : clients) {
                        if (targetuser.equals(cl.getClientNickname()) || targetuser.equals(String.valueOf(cl.getClientID()))) {
                            targetclient = cl;
                            break;
                        }
                    }
                }

                // if found notify of vide stream request
                if (targetclient != null) {
                    targetclient.send("/receive-stream " + sendername + " " + ip + " " + port + " " + filename);
                    broadcastToClient("Signaling " + targetuser + " for video stream ", clientID, broadcastChannel);
                } else {
                    broadcastToClient("User '" + targetuser + "' not found.", clientID, broadcastChannel);
                }
            }
            return;

        }



        // non-arg commands
        switch (message) {
            // Return client to main channel
            case "/part":
                Channel newChannel = getChannelByName("main");
                ClientHandler client = getClientByID(clientID);
                if (client == null) return;
                if (!changeClientChannel(client, newChannel)) return;
                break;

            // Print other clients in channel
            case "/who":
                List<ClientHandler> clients = broadcastChannel.getChannelClients();
                StringBuilder clientStringBuilderList = new StringBuilder();

                synchronized (clients){
                    // create output string
                    for (ClientHandler c : clients) {
                        String clientInfo = getClientInfo(c, c.getClientID()==clientID);
                        clientStringBuilderList.append(clientInfo);
                    }
                }

                String singleClientList = clientStringBuilderList.toString();
                broadcastToClient(singleClientList, clientID, broadcastChannel);
                break;

            // Print Client help script
            case "/help":
                broadcastToClient(Constants.CLIENT_COMMAND_HELP_STRING, clientID, broadcastChannel);
                break;

            // Prints current channel to user
            case "/where":
                String msg = (broadcastChannel.getChannelDescription().isEmpty()) ? "Channel Name: " + broadcastChannel.getChannelName()
                        : "Channel Name: "+broadcastChannel.getChannelName()+"\nDescription: "+broadcastChannel.getChannelDescription();
                broadcastToClient(msg, clientID, broadcastChannel);
                break;

            // Prints list of all channels on server
            case "/list":
                List<Channel> channels = getChannels();
                StringBuilder channelListStringBuilder = new StringBuilder();
                String clientCurrentChannelName = broadcastChannel.getChannelName();

                // synchronise channels to ensure accuracy
                synchronized (channels) {
                    for (Channel channel : channels) {
                        if (channel.getChannelName().equals(clientCurrentChannelName)){
                            // clearly show client current channel in blue
                            channelListStringBuilder.append(Constants.BLUE)
                                    .append("* ")
                                    .append(channel.getChannelName())
                                    .append(Constants.RESET)
                                    .append("\n");
                        }else{
                            channelListStringBuilder.append(channel.getChannelName()).append('\n');
                        }
                    }
                }
                String singleChannelListMessage = channelListStringBuilder.toString();
                broadcastToClient(singleChannelListMessage, clientID, broadcastChannel);
                break;

            default:
                String clientNickname = getClientByID(clientID).getClientNickname();

                try {
                    // save user message
                    // user message so not announcement
                    saveMessage(clientID, clientNickname,status, message, false, broadcastChannel);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // format message
                String outputMessage = (clientNickname == null) ?
                        "Client (" +clientID+ ") ("+status+") ["+broadcastChannel.getChannelName()+"]: " + message :
                        clientNickname + " ("+status+") ["+broadcastChannel.getChannelName()+"]: " + message ;
                serverLog(outputMessage);
                broadcast(outputMessage, clientID,broadcastChannel); // broadcast to all clients except the person who sent the message
                break;
        }
    }


    // helper for /who command output
    private String getClientInfo(ClientHandler c, boolean isCurrentClient){
        StringBuilder singleClientInfo = new StringBuilder();

	if(isCurrentClient){
	    singleClientInfo.append(Constants.BLUE)
		.append(c.getClientNickname() == null
                ? "* Client ("+c.getClientID()+") (" +c.getStatus() + ")"
                : "* "+c.getClientNickname() +" ("+c.getStatus()+")")
		.append(Constants.RESET)
		.append("\n");
	}else{
	    // non-current client - no colour
	    singleClientInfo.append(c.getClientNickname() == null
                        ? "Client ("+c.getClientID()+") (" + c.getStatus() + ")"
                        : c.getClientNickname() +" ("+c.getStatus()+")")
		.append("\n");
	}
	return singleClientInfo.toString();

    }


    // return lock object for specied channel
    private ReentrantReadWriteLock getChannelFileLock(String channelName){
        // insert channelName if not already in map, and associate with new ReentrantReadWriteLock
        // atomic (with concurrent hash map)
        return channelHistoryFileLocks.computeIfAbsent(channelName, key->new ReentrantReadWriteLock());
    }


    /**
     * Return list of last 10 objects from JSON file
     * JSON file of form:
     *[ {
     *   "username" : "1",
     *   "channelName" : "main",
     *   "clientID" : 1,
     *   "message" : "hi",
     *   "timestamp" : "2026-03-03T16:42:34.579214973Z"
     * },
     * ...
     * } ]
     */
    private LinkedList<String> getLastTenHistoryItems(File jsonFile) throws IOException{
        JsonFactory jsonFactory = new JsonFactory();
        JsonParser jsonParser = jsonFactory.createParser(jsonFile);

        // array list for messages
        LinkedList<String> historyMessages = new LinkedList<>();

        // check missing initial [
        if (jsonParser.nextToken() != JsonToken.START_ARRAY){
            throw new IOException("Missing JSON start token");
        }

        // loop until end
        while(jsonParser.nextToken() != JsonToken.END_ARRAY){

            if (jsonParser.currentToken() == JsonToken.START_OBJECT){
                // initialise variables for this object
                String username = null;
                String channelName = null;
                String clientID = null ;
                String message = null;
                String status = null;
                boolean announcementMessage = false;

                // parse object
                while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = jsonParser.getCurrentName();


                    if ("username".equals(fieldName)) {
                        jsonParser.nextToken();
                        username = jsonParser.getText();

                    }else if ("channelName".equals(fieldName)) {
                        jsonParser.nextToken();
                        // get value from message
                        channelName = jsonParser.getText();

                    }else if ("clientID".equals(fieldName)) {
                        jsonParser.nextToken();
                        // get value from message
                        clientID = jsonParser.getText();

                    }else if ("message".equals(fieldName)) {
                        jsonParser.nextToken();
                        // get value from message
                        message = jsonParser.getText();

                    }else if ("status".equals(fieldName)) {
                        jsonParser.nextToken();
                        // get value from message
                        status = jsonParser.getText();
                    }else if("announcementMessage".equals(fieldName)) {
                        jsonParser.nextToken();
                        // get value from message
                        announcementMessage = Boolean.parseBoolean(jsonParser.getText());

                    }else{
                        // Add other fields if required
                        // ignore other fields
                        jsonParser.skipChildren();
                    }
                }

                // default values in case of incomplete data
                channelName = (channelName != null) ? channelName : "Unknown";
                clientID = (clientID != null) ? clientID : "Unknown";
                message = (message != null) ? message : "No Message";
                status = (status != null) ? status : "Online";

                String formattedMessage;

                // default username written to file as ""

                if (announcementMessage){
                    // announcement rather than user message
                    formattedMessage = message;
                }else{
                    // user message
                    if (username.isEmpty()){
                        // without quotations for nick
                        formattedMessage = String.format("Client (%s) (%s) [%s]: %s",clientID,status,channelName,message);
                    }else{
                        formattedMessage = String.format("Client (%s) '%s' (%s) [%s]: %s",clientID,username,status,channelName,message);
                    }
                }


                // remove first element elements more than 11
                if (historyMessages.size() >= 11){
                    historyMessages.removeFirst();
                }
                historyMessages.addLast(formattedMessage);
            }
        }
        // remove most recent message from list (user joining message)
        if (!historyMessages.isEmpty()){
            historyMessages.removeLast();
        }
        return historyMessages;
    }


    // NicknameTestServer listening for internal server commands
    private void listenForServerCommands(){
        new Thread(() -> {
            String[] splitText = null;
            try {
                // creates terminal
                Terminal terminal = TerminalBuilder.builder().system(true).build();
                // creates line reader
                reader = LineReaderBuilder.builder().terminal(terminal).build();
                // reads line from user
                while (serverRunning) {
                    String line = reader.readLine("> ");
                    if (line == null) continue;
                    // split input into separate words
                    splitText = line.trim().split(" ");

                    // all commands prefixed with '/':
                    // all commands are case insensitive
                    switch (splitText[0].toLowerCase()){
                        case "/shutdown":
                            // shut server down
                            closeServerSocket();
                            break;

                        case "/create":
                            // create new channel

                            // split input into 3 parts: command, name, newChannelDescription
                            String[] splitInput = line.trim().split("\\s+", 3);

                            if (splitInput.length < 2){
                                System.out.println("Invalid number of args: '/create <channel name> [newChannelDescription]'");
                                break;
                            }
                            // valid args
                            // check name of channel before creation
                            String newChannelName = splitInput[1];

                            // newChannelDescription is everything after name
                            String newChannelDescription = (splitInput.length == 3) ? splitInput[2].trim() : "";

                            if (!Constants.VALID_CHANNEL_NAME_PATTERN.matcher(newChannelName).matches()){
                                // invalid
                                System.out.println("Invalid channel name. Must be between 3 and 16 characters. " +
                                        "Only lower case characters, numbers, '-', '_' allowed.");
                                break;
                            }

                            try{
                                // check new channel is unique
                                if (getChannelByName(newChannelName) != null){
                                    System.out.println("A channel with this name already exists!");
                                    break;
                                }
                                createChannel(this, newChannelName, newChannelDescription);
                                System.out.println("Channel created");
                            } catch (IOException e){
                                System.out.println("Failed to create channel: " + e.getMessage());
                            }
                            //valid
                            break;
                        case "/remove":
                            // remove a client
                            String[] splitInputRemove = line.trim().split("\\s+", 2); // split input into 2 parts: command and client ID to be removed

                            if (splitInputRemove.length < 2){
                                System.out.println("Usage: '/remove <client ID> '");
                                break;
                            }

                            int targetID;

                            try{
                                targetID = Integer.parseInt(splitInputRemove[1].trim()); // parse clientID
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid ClientID. Must Be Valid Integer");
                                break;
                            }
                            forceRemoveClient(targetID);
                            break;

                        default:
                            break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error creating terminal: " + e.getMessage());
            }

        }).start();
    }

    // Return ClientHandler object given it's ID
    private ClientHandler getClientByID(int clientID) {
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.getClientID() == clientID) {
                    return ch;
                }
            }
        }
        return null;
    }



    // change clients channel and inform users of both old and new channel of their joining/departing
    private boolean changeClientChannel(ClientHandler client, Channel newChannel) {
        Channel oldChannel = client.getCurrentChannel();
        if (oldChannel == newChannel) {
            client.send("You are already in this channel.");
            return false;
        }

        int clientID = client.getClientID();
        String clientNickname = client.getClientNickname();
        String clientIDMessage = "Client (" + clientID + ")";
        String clientNicknameMessage = (clientNickname == null) ? clientIDMessage : "'" + clientNickname + "'";

        // thread safe addition/removal
        oldChannel.removeUser(client);
        newChannel.addNewUser(client);

        client.changeChannel(newChannel);
        client.send("You joined channel [" + newChannel.getChannelName() + "] " + newChannel.getChannelDescription());

        // inform users of client leaving and save context to file
        // client simply left and joined another channel - still on server
        String clientLeftChannelMessage = (clientNickname == null) ?
                clientIDMessage + " left the channel." :
                clientNicknameMessage +" left the channel.";
        broadcast(clientLeftChannelMessage, clientID, oldChannel);
        try{
            saveMessage(client.getClientID(),client.getClientNickname(),client.getStatus(),
                    clientLeftChannelMessage,true,oldChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // inform users of client joining and save context to file
        String clientJoinedChannelMessage = (clientNickname == null) ?
                clientIDMessage + " joined the channel." :
                clientNicknameMessage + " joined the channel.";

        broadcast(clientJoinedChannelMessage, client.getClientID(), newChannel);
        try{
            saveMessage(clientID,clientNickname,client.getStatus(), clientJoinedChannelMessage,
                    true,newChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String serverOutputExit = (clientNickname == null) ?
                clientIDMessage + " left channel ["+oldChannel.getChannelName()+"]":
                clientIDMessage + " '"+ clientNickname+"' left channel ["+oldChannel.getChannelName()+"]";
        System.out.println(serverOutputExit);

        String severOutputJoin = (clientNickname == null) ?
                clientIDMessage + " connected to ["+newChannel.getChannelName()+"]":
                clientIDMessage + " '"+ clientNickname+"' connected to ["+newChannel.getChannelName()+"]";
        serverLog(severOutputJoin);

        return true;
    }

    // remove specific client by ID
    private void forceRemoveClient(int targetClientID){

        ClientHandler target = getClientByID(targetClientID); // find handler of target

        if (target == null) {
            System.out.println("No connected Clients with ID: " + targetClientID);
            return;
        }

        Channel channel = target.getCurrentChannel(); // get current channel

        target.send("You have been removed from the server."); // notify client before removal

        channel.removeUser(target);

        synchronized (clients){
            clients.remove(target);
        }

        String broadcastMessage = "Client (" + targetClientID + ") was removed from server ";
        serverLog(broadcastMessage);
        broadcast(broadcastMessage, targetClientID, channel);

        try{
            saveMessage(targetClientID, getClientByID(targetClientID).getClientNickname(),
                    getClientByID(targetClientID).getStatus(), broadcastMessage, true,channel); // write to channel log
        } catch (IOException e) {
            e.printStackTrace();
        }

        target.shutdown(); // close socket and stop handler thread

    }


    // load previously created channels from file
    // called on server init ONLY - no locks
    private void loadChannels() {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File("channels.json");
        if (!file.exists() || file.length() == 0) return;
        try {
            List<Map<String, String>> saved = objectMapper.readValue(file, new TypeReference<List<Map<String, String >>>() {});

            for (Map<String, String> entry : saved){ // loop through saved entries in file
                if (entry == null) continue;
                String name = entry.get("channelName");
                String description = entry.get("description");

                if (name == null) continue; // invalid skip

                channels.add(new Channel(name, description));
            }
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // send message to every connected client using send method in ClientHandler
    public void broadcast(String message, int senderID, Channel sendChannel) {
        List<ClientHandler> channelClientHandlerList = sendChannel.getChannelClients();
        synchronized (channelClientHandlerList) {
            for (ClientHandler ch : channelClientHandlerList) {
                if (ch.getClientID() != senderID) { // only broadcast to clients who did not send the message
                    ch.send(message);
                }
            }
        }
    }

    // individual broadcast for client - used for command results
    public void broadcastToClient(String message, int senderID, Channel sendChannel) {
        List<ClientHandler> channelClientHandlerList = sendChannel.getChannelClients();
        synchronized (channelClientHandlerList) {
            for (ClientHandler ch : channelClientHandlerList) {
                if (ch.getClientID() == senderID) { // only broadcast to clients who did send the message
                    ch.send(message);
                }
            }
        }
    }


    // remove client from server - total removal of client
    public void removeClient(ClientHandler clientHandler,Channel channel) {
        if (shuttingDown)return;
        channel.removeUser(clientHandler);
        if (clients.remove(clientHandler)) {

            int clientID = clientHandler.getClientID();

            String clientNickname = clientHandler.getClientNickname();

            String clientRemovedMessage = (clientNickname == null) ?
                    "Client ("+clientID+") left the server." :
                    "'"+clientNickname+"' left the server.";

            // free client nickname
            if (clientNickname != null){
                clientNicknameList.remove(clientHandler.getClientNickname());
            }

            try {
                //saveMessage(clientID, clientHandler.getClientNickname(), clientHandler.getStatus(),
                saveMessage(clientID, clientNickname, clientHandler.getStatus(),
                        clientRemovedMessage, true,channel);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // inform remaining clients

            // simple to server terminal
            serverLog(clientRemovedMessage);

            // pass channel to inform users in that channel
            broadcast(clientRemovedMessage,clientID,channel);
            printConnectedClients();
        }
        // clause for if client removal is unsuccessful
    }

    // all clients on the server regardless of channel
    public void printConnectedClients() {
        synchronized (clients) {
            if (!clients.isEmpty()) {
                serverLog(clients.size() + " Clients Connected");
            } else {
                serverLog("No Clients connected");
            }
        }
    }


    private void closeServerSocket() {
        serverRunning = false;
        shuttingDown = true;
        // server log
//       System.out.println("NicknameTestServer shutting down.");

        synchronized (clients){
            // inform users of server shutdown
            for (ClientHandler c : clients){
                // add timer
                c.send("\n\nNicknameTestServer shutting down.\nGoodbye...");
                c.shutdown();
            }
            // SHOULD DISCONNECT CLIENTS HERE AND REMOVE THEM
            // CURRENTLY ONLY REMOVES THEM FROM SERVER CLIENT LIST
            clients.clear();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // create new channel and add to server's channel list
    private void createChannel(Server server, String channelName, String description) throws IOException {
        String newChannelFileName = channelName+".json";
        String emptyJSONSkeleton = "[]";
        Path path = Paths.get(newChannelFileName);

        ReentrantReadWriteLock readWriteLock = getChannelFileLock(channelName);
        readWriteLock.writeLock().lock();

        try{
            if (!Files.exists(path)){
                // fill will placeholder JSON
                Files.write(path,emptyJSONSkeleton.getBytes(StandardCharsets.UTF_8));
            }
        }catch (IOException e){
            // failed to create file
            e.printStackTrace();
        }finally {
            readWriteLock.writeLock().unlock();
        }

        Channel channel = new Channel(channelName, description);
        // do not allow main to be created again
        if (!Objects.equals(channelName, "main")) {
            saveChannel(channel);
        }
        server.channels.add(channel);
    }

    // search for a channel
    private Channel getChannelByName(String channelName){
        synchronized (channels) {
            for (Channel channel : channels) {
                if (channel.getChannelName().equals(channelName)) {
                    return channel;
                }
            }
        }
        return null; // not found
    }

    // Write client message to channel file
    private void saveMessage(int clientID, String clientUsername, String status, String message,
                             boolean isAnnouncementMessage, Channel broadcastChannel) throws IOException {
        ReentrantReadWriteLock jsonHistoryFileLock = getChannelFileLock(broadcastChannel.getChannelName());

        // timestamp in zulu/GMT
        String timestamp = Instant.now().toString();

        String username = (clientUsername == null) ? "" : clientUsername;

        Message msg = new Message(username, broadcastChannel.getChannelName(), status, clientID, message,
                isAnnouncementMessage, timestamp);

        ObjectMapper objectMapper = new ObjectMapper();
        String filename = broadcastChannel.getChannelName() + ".json";

        // log file in current location of file running
        File file = new File(filename);
        List<Message> messages = new ArrayList<>();

        // lock - ensure no history readers are currently reading
        jsonHistoryFileLock.writeLock().lock();
        try {
            if (file.exists() && file.length() > 0) {
                try {
                    messages = objectMapper.readValue(file, new TypeReference<List<Message>>() {
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            messages.add(msg);

            // messages.add(msg);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, messages);

        } finally {
            // release write lock
            jsonHistoryFileLock.writeLock().unlock();
        }
    }

    // Safe channels on server to disk
    // called each time server creates a new channel
    private void saveChannel(Channel channel) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(Constants.CHANNEL_LIST_FILE_NAME);
        List<Map<String, String>> saved;

        // lock for whole time - no reader
        channelListFileLock.writeLock().lock();
        try {
            if (file.exists() && file.length() > 0) { // file has content
                // read
                saved = objectMapper.readValue(file, new TypeReference<List<Map<String, String>>>() {
                });

            } else { // file missing/empty
                saved = new ArrayList<>();
            }

            boolean updated = false;

            for (Map<String, String> entry : saved) {
                if (entry == null) continue;

                String name = entry.get("channelName");
                if (Objects.equals(name, channel.getChannelName())) { // match by channel name
                    entry.put("description", channel.getChannelDescription());
                    updated = true;
                    break;
                }
            }

            if (!updated) { // not updated insert new entry
                Map<String, String> obj = new LinkedHashMap<>();
                obj.put("channelName", channel.getChannelName());
                obj.put("description", channel.getChannelDescription());
                saved.add(obj); // add entry
            }

            // write to file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, saved);
        }finally {
            // release write lock
            channelListFileLock.writeLock().unlock();
        }
    }


    private void serverLog(String message) {
        // checks if the server is currently typing
        if (reader != null) {
            // prints message above the prompt
            reader.printAbove(message);

        } else {
            // if server not typing print normally
            System.out.println(message);
        }
    }

    private List<Channel> getChannels(){return channels;}


    public static void main(String args[]){
        // usage instructions
        if (args.length != 1) {
            System.out.println("USAGE: NicknameTestServer <port>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        Server server = new Server(port);
        server.startServer();
    }
}
