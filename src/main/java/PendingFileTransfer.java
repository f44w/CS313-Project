public class PendingFileTransfer {
    private String senderName;
    private String ip;
    private int port;
    private String filename;

    public PendingFileTransfer(String senderName, String ip, int port, String filename) {
        this.senderName = senderName;
        this.ip = ip;
        this.port = port;
        this.filename = filename;
    }

    public String getSenderName() {return senderName;}
    public String getIp() {return ip;}
    public int getPort() {return port;}
    public String getFilename() {return filename;}
}