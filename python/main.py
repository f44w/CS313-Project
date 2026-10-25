import socket
import threading
import sys

def receive(connection):
    try:
        while True:
            ## recieves data from the socket
            data = connection.recv(1024)
            ## if no data then break loop and close connection
            if not data:
                break
            ## decode from utf8 and strip newline character
            print("[Peer]: " + data.decode("utf-8").rstrip())
    except OSError:
        pass
    print("Connection closed.")

def send(connection):
    try:
        while True:
            message = input()
            ## sends and encdes message to socket
            connection.sendall((message + "\n").encode("utf-8"))
            ## if message is exit then close the connection
            if message.lower() == "exit":
                connection.close()
                break
    except OSError:
        pass

if __name__ == "__main__":
    ## if not valid amount of arguments exit program
    if len(sys.argv) < 3:
        print("Usage:")
        print("listen <port>")
        print("connect <ip> <port>")
        sys.exit(0)

    mode = sys.argv[1].lower()

    ## if hosting on the python side
    if mode == "listen":
        port = int(sys.argv[2])
        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.bind(("127.0.0.1", port))
        server.listen(1)

        print("Waiting for peer...")
        connection, addr = server.accept()
        print("Peer connected!")

    ## if connecting on python side
    elif mode == "connect":
        ip = sys.argv[2]
        port = int(sys.argv[3])
        connection = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        connection.connect((ip, port))
        print("Connected to peer!")

    ## invalid mode
    else:
        print("Invalid mode. Use 'listen' or 'connect'.")
        sys.exit(0)


## sets up our threads to listen and to send messages to other client
receiver = threading.Thread(target=receive, args=(connection,))
sender = threading.Thread(target=send, args=(connection,))

## starts our threads to constantly be listening
receiver.start()
sender.start()