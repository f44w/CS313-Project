public class Message {
    private String username;
    private String channelName;
    private String message;
    private String timestamp;
    private String clientStatus;

    private int clientID;

    // defines if message was an announcement of user leaving/joining or message (user said X)
    private boolean announcementMessage;

    public Message() {}

    public Message(String username, String channelName, String clientStatus, int clientID, String message,
                   boolean announcementMessage, String timestamp){
        this.username = username;
        this.channelName = channelName;
        this.clientStatus = clientStatus;
        this.clientID = clientID;
        this.message = message;
        this.timestamp = timestamp;
        this.announcementMessage = announcementMessage;
    }

    public String getChannelName() { return channelName; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public String getClientStatus(){return clientStatus;}
    public boolean isAnnouncementMessage(){return announcementMessage;}
    public int getClientID(){return clientID;}

    public void setIsAnnouncementMessage(boolean announcementMessage) {this.announcementMessage = announcementMessage;}
}