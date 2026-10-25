import java.io.*;
import java.net.*;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;
import org.jline.terminal.Terminal;
import org.opencv.core.Core;

public class Client implements Runnable {
    private Socket socket;

    public Client(Socket socket) {
        this.socket = socket;
    }

    // Method defining thread work
    @Override
    public void run() {
        try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            // Create a thread for incoming and outgoing messages
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();

            MediaWebServer.startMediaServer();

            PendingFileTransfer[] pendingTransfer = new PendingFileTransfer[1];
            PendingVideoStream[] pendingVideoStream = new PendingVideoStream[1];

            // had to do it this way didnt like that reciever was named twice other way
            MessageReceiver receiverObject = new MessageReceiver(inputStream, lineReader, pendingTransfer, pendingVideoStream); // create message reciver object
            Thread receiver = new Thread(receiverObject);

            Thread sender = new Thread(new MessageSender(outputStream, lineReader, pendingTransfer, receiverObject, pendingVideoStream));

            // startServer both thread (calls their .run() methods)
            receiver.start();
            sender.start();

            // client waits for sender to finish execution
            sender.join();

            // close and exit
            // null term message sent outputted to receiver causing receiver to terminate
            socket.close();

            System.out.println("\nConnection Closed.");

        } catch (IOException e) {
            System.out.println("Failed to startServer peer.");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws IOException {



        if (args.length != 2) {
            System.out.println("Usage:");
            System.out.println("Client <ip> <port>");
            return;
        }

        String ip = args[0];
        int port = Integer.parseInt(args[1]);

        Socket socket = new Socket(ip, port);

        // PRINT THE CHANNEL THE CLIENT HAS JOINED
        System.out.println("You have connected!\n");
        //printServerUsage();

        // spin up new thread
        new Thread(new Client(socket)).start();
    }
}
