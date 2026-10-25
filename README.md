## Commands:
- All prefixed with `/`

### Client:
    - `/exit` - closes connection to server
    - `/list` - view all channels on server
    - `/away` - changes status to offline
    - `/join <channel>` - join specific channel
    - `/part` - returns to main channel
    - `/who` - returns list of all users in current channel
    - `/where` - returns name of current channel
    - `/status-change <new-status>` - changes client status message
### Server:
    - `/shutdown` - shutdown server
    - `/create *X*` - create new channel with name *X*
    - `/remove <clientID> - forcefully removes a client from server
    

## General Note :
- Server starts new client handler for each client that joins (*X* client handlers total, where *X* is the number of clients)
    - Requires thread safety/caution
- Keep track of all ClientHandlers in an array
- `ClientHandler` reads the message typed by the user and appends the id/username with the message  
- Message & ID sent to **server** for broadcasting to other clients
- Iterates over the `ClientHandler` array and sends the message to all clients (except the one that sent the message)
- Clients that exit have their `ClientHandler` killed and are removed from the array

> [!NOTE]
> `ClientHandler` is a **one-way** between Server and Client
> `ClientHandler` listens for Client input/message and then returns this (as well as the ID/username) to the server for broadcast

## Testing:
Server:
```
mvn compile exec:java -Dexec.mainClass="<package_name>.<package_server_name>" 
mvn compile exec:java -Dexec.mainClass="ClientHistoryTest.HistoryTestServerUnsafe" 
```

Main:
```
mvn compile exec:java -Dexec.mainClass="<package_name>.Main" 
mvn compile exec:java -Dexec.mainClass="ClientHistoryTest.Main" 
```
