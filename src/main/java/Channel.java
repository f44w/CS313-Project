import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class Channel{
    private final String channelName;
    private final String channelDescription;
    private List<ClientHandler> clients;

    // init the channel
    public Channel(String name, String channelDescription){
        this.channelName = name;
        this.channelDescription = (channelDescription == null) ? "" : channelDescription;
        this.clients = Collections.synchronizedList(new ArrayList<>());
    }

    // add new user to this channel
    // Both methods are internally synchronized by 'synchronizedList'
    public void addNewUser(ClientHandler newClientHandler){clients.add(newClientHandler);}
    public void removeUser(ClientHandler newClientHandler){clients.remove(newClientHandler);}


    // return all the clients that are in this channel
    public List<ClientHandler> getChannelClients(){
        return this.clients;
    }

    // return name of channel
    public String getChannelName(){return this.channelName;}
    public String getChannelDescription(){return this.channelDescription;}

    // atomic
    public int getNumClients(){
        synchronized (clients){
            return clients.size();
        }
    }
}
