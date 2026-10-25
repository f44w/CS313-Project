import FrameStreaming.VideoFrameStreamState;
import OldStreamMethod.StreamState;
import WebcamStreaming.WebcamStreamState;
import org.jline.reader.LineReader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import javax.imageio.ImageIO; // allows to check whether file is an image
import javax.swing.*;

public class MessageReceiver implements Runnable {

    // data input stream received from Peer class
   private BufferedReader reader;
   private LineReader lineReader;

   // store pending p2p file transfers and video streams
   private PendingFileTransfer[] pendingTransfer;
   private PendingVideoStream[] pendingVideoStream;


   public MessageReceiver(InputStream in, LineReader lineReader, PendingFileTransfer[] pendingTransfer, PendingVideoStream[] pendingVideoStream) {
       this.reader = new BufferedReader(new InputStreamReader(in));
       this.lineReader = lineReader;
       this.pendingTransfer = pendingTransfer;
       this.pendingVideoStream = pendingVideoStream;
   }

    // Thread function for receiver incoming messages (used by 'server' and 'client')
    @Override
    public void run() {
        try {
         String message;

         while ((message = reader.readLine()) != null) {

             // p2p reciever for file
             if (message.startsWith("/receive-file ")) {

                 String[] split = message.split(" ", 5);

                 if (split.length == 5) {
                     String senderName = split[1];
                     String ip = split[2];
                     int port = Integer.parseInt(split[3]);
                     String filename = split[4];

                     pendingTransfer[0] = new PendingFileTransfer(senderName, ip, port, filename); // store transfer details

                     lineReader.printAbove(senderName + " is trying to send you " + filename);
                     lineReader.printAbove("Would you like to accept? (y/n)");
                 }
                 continue;
             }

             // p2p reciver for video stream
             if(message.startsWith("/receive-stream")){

                 String[] split = message.split(" ", 5);

                 if (split.length == 5) {
                     String senderName = split[1];
                     String ip = split[2];
                     int port = Integer.parseInt(split[3]);
                     String filename = split[4];

                     pendingVideoStream[0] = new PendingVideoStream(senderName, ip, port, filename); // store stream details

                     lineReader.printAbove(senderName + " wants to start video stream " + filename);
                     lineReader.printAbove("Would you like to accept? (y/n)");
                 }
                 continue;
             }

            lineReader.printAbove(message); // normal chat message
         }
         System.out.println("\nServer disconnected.");
         System.exit(0);

        } catch (IOException e) {
        }
    }

    public void downloadPendingFile(PendingFileTransfer transfer) {
        if (transfer == null) {
            return;
        }

        // new download thread -> chat still responsive
        new Thread(() -> {
            File downloadDir = new File("downloads");
            if (!downloadDir.exists()) {
                downloadDir.mkdirs(); // make new download folder in project directory
            }
            File outputFile = new File(downloadDir, transfer.getFilename());

            // connect to sendesr's p2p server socket
            try (Socket p2pSocket = new Socket(transfer.getIp(), transfer.getPort());
                 InputStream p2pIn = p2pSocket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;

                // keep reading until sender closes stream
                while ((bytesRead = p2pIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                lineReader.printAbove("Successfully downloaded '" + transfer.getFilename() + "' to the downloads folder.");

                // if downloaded file is image or video open in browser
                if (MediaUtils.isFileImage(outputFile) || MediaUtils.isFileVideo(outputFile)){
                    lineReader.printAbove("Opening recieved media in browser" + transfer.getFilename());
                    openMediaViewer(outputFile);
                }

                /*
                // if downloaded file video attempt to play it
                if (isVideoFile(outputFile)){
                    lineReader.printAbove("Opening recived video " + transfer.getFilename());
                    playVideo(outputFile);
                }

                // if downloaded file image open in viewer
                if (isFileImage(outputFile)){
                    lineReader.printAbove(("Opening recieved image" + transfer.getFilename()));
                    showImage(outputFile);
                }

                 */

            } catch (IOException e) {
                lineReader.printAbove("Failed to download file from: " + transfer.getSenderName() + ": " + e.getMessage());
            }
        }).start();
    }

    // open the browser media viewer for a downloaded file
    private void openMediaViewer(File mediaFile) {
        try {
            // build the local viewer URL
            String viewerUrl = "http://localhost:" + MediaWebServer.PORT + "/viewer?file=" + mediaFile.getName();

            lineReader.printAbove("Opening media viewer for '" + mediaFile.getName() + "'");

            // open the default browser if desktop support exists
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                if (desktop.isSupported(Desktop.Action.BROWSE)){

                    desktop.browse(new URI(viewerUrl)); // need to convert into URI object, cant use raw string
                    return;
                }
            }

            // if above fails use this for linux systems
            new ProcessBuilder("xdg-open", viewerUrl).start();

        } catch (Exception e) {
            lineReader.printAbove("Failed to open browser media viewer: " + e.getMessage());
        }
    }

    // read incoming frames and open browser to display
    public void receivePendingVideoFrameStream(PendingVideoStream stream) {


        if (stream == null) {
            return;
        }

        // run receiver in background thread
        new Thread(() -> {

            VideoFrameStreamState.start();
            boolean browserOpened = false;

            try (Socket p2pSocket = new Socket(stream.getIp(), stream.getPort());
                 DataInputStream dis = new DataInputStream(p2pSocket.getInputStream())) {

                lineReader.printAbove("Receiving video frame stream " + stream.getFilename() + "...");

                // keep reading frames until sender closes stream
                while (true) {

                    int frameSize = dis.readInt(); // sender writes frame size first

                    // invalid frame size
                    if (frameSize <= 0 || frameSize > 10 * 1024 * 1024) {
                        throw new IOException("Invalid frame size: " + frameSize);
                    }

                    byte[] frameBytes = new byte[frameSize]; // allocate buffer

                    dis.readFully(frameBytes); // read entire JPEG frame

                    VideoFrameStreamState.updateFrame(frameBytes);

                    // open browser after first received frame
                    if (!browserOpened) {
                        openVideoFrameViewer();
                        browserOpened = true;
                    }
                }
            } catch (IOException e) {
                lineReader.printAbove("Video frame stream ended: " + e.getMessage());
            } finally {
                VideoFrameStreamState.finishStream();
            }
        }).start();
    }

    // open browser
    private void openVideoFrameViewer() {
        try {

            String viewerUrl = "http://localhost:" + MediaWebServer.PORT + "/viewer?videoframes=true";

            lineReader.printAbove("Opening video frame stream viewer");

            // use desktop browser if supported
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(viewerUrl));
                    return;
                }
            }

            // linux fallback
            new ProcessBuilder("xdg-open", viewerUrl).start();

        } catch (Exception e) {
            lineReader.printAbove("Failed to open video frame stream viewer: " + e.getMessage());
        }
    }



    // // read incoming frames and open browser to display
    public void receivePendingWebcamStream(PendingVideoStream stream) {

        if (stream == null) {
            return;
        }

        // run receiver in background thread
        new Thread(() -> {

            WebcamStreamState.start();
            boolean browserOpened = false;

            try (Socket p2pSocket = new Socket(stream.getIp(), stream.getPort());
                 DataInputStream dis = new DataInputStream(p2pSocket.getInputStream())) {

                lineReader.printAbove("Receiving webcam stream...");

                // keep reading frames until sender closes stream
                while (true) {
                    int frameSize = dis.readInt();  // sender writes frame size first

                    // invalid frame size
                    if (frameSize <= 0 || frameSize > 10 * 1024 * 1024) {
                        throw new IOException("Invalid frame size: " + frameSize);
                    }

                    byte[] frameBytes = new byte[frameSize]; // allocate buffer

                    dis.readFully(frameBytes); // read entire JPEG frame

                    WebcamStreamState.updateFrame(frameBytes);

                    // open browser after first received frame
                    if (!browserOpened) {
                        openWebcamViewer();
                        browserOpened = true;
                    }
                }

            } catch (IOException e) {
                lineReader.printAbove("Webcam stream ended: " + e.getMessage());
            } finally {
                WebcamStreamState.finishWebcamStream();
            }
        }).start();
    }

    // open browser
    private void openWebcamViewer() {
        try {
            String viewerUrl = "http://localhost:" + MediaWebServer.PORT + "/viewer?webcam=true";

            lineReader.printAbove("Opening webcam viewer");

            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(viewerUrl));
                    return;
                }
            }

            new ProcessBuilder("xdg-open", viewerUrl).start();
        } catch (Exception e){
            lineReader.printAbove("Failed to open webcam viewer: " + e.getMessage());
        }
    }

    /*

    public void receivePendingVideoStream(PendingVideoStream stream){

       if (stream == null){
           return;
       }

        // new stream thread -> chat still responsive
        new Thread(() -> {
            File downloadDir = new File("downloads");
            if (!downloadDir.exists()) {
                downloadDir.mkdirs(); // make new download folder in project directory
            }

            File outputFile = new File(downloadDir, "stream.mp4"); // store into file so browser can try and read, is able to grow as we read more bytes

            StreamState.startStream(outputFile, MediaUtils.getVideoContentType(outputFile)); // mark stream as active before browser starts reading

            boolean browserOpened = false;

            // connect to sendesr's p2p streaming socket
            try (Socket p2pSocket = new Socket(stream.getIp(), stream.getPort());

                 InputStream p2pIn = p2pSocket.getInputStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[64 * 1024]; // 64 KB
                int bytesRead;

                lineReader.printAbove("Recieving stream" + stream.getFilename() + "...");

                long totalBytesWritten = 0;
                int chunkCount = 0;

                // keep adding bytes while sender is still streaming
                while ((bytesRead = p2pIn.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    //fos.flush(); // push recived bytes to the disk immediatly as we receive them

                    totalBytesWritten = totalBytesWritten + bytesRead;
                    chunkCount ++;

                    // testing trying to get stream smoother
                    if (chunkCount % 4 == 0){
                        fos.flush();
                    }
                    // open once we have enough data
                    if (!browserOpened && totalBytesWritten >= 512 * 1024){
                        fos.flush();
                        openLiveStreamViewer();
                        browserOpened = true;
                    }
                }

                fos.flush();

                lineReader.printAbove("Stream finished");

            } catch (IOException e) {
                lineReader.printAbove("Failed stream connection with: " + stream.getSenderName() + ": " + e.getMessage());
            } finally {
                StreamState.finishStream();
            }
        }).start();

    }


    // open transferred video in the system's default video player
    private void playVideo(File videoFile){
        try {
            // check system supports java's desktop FEATURES
            if (Desktop.isDesktopSupported()) {

                Desktop desktop = Desktop.getDesktop(); // java object used to interact with OS

                // check if system supports open action
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(videoFile); // open video using OS default application
                    return;
                }
            }

            // if above fails use this for linux systems
            new ProcessBuilder("xdg-open", videoFile.getAbsolutePath()).start();

        } catch (IOException e){
            lineReader.printAbove("Failed to open video player: " + e.getMessage());
            lineReader.printAbove("Video saved at: " + videoFile.getAbsolutePath());
        }
    }

    // opens swing window showing recieved image
    private void showImage(File imageFile){
       try {
           BufferedImage image = ImageIO.read(imageFile);

           if (image == null){
               lineReader.printAbove("Failed to open received image");
               return;
           }

           // create image viewing window
           JFrame frame = new JFrame("Recived Image " + imageFile.getName());
           JLabel imageLabel = new JLabel(new ImageIcon(image)); // label containing image
           JScrollPane scrollPane = new JScrollPane(imageLabel); // allow scrolling for large images

           frame.setLayout(new BorderLayout());
           frame.add(scrollPane, BorderLayout.CENTER); // center image area
           frame.setSize(800,600);
           frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // prevent accidentally closing, chat
           frame.setLocationRelativeTo(null); // center window on screen
           frame.setVisible(true);


       } catch (IOException e){
           lineReader.printAbove("Failed to open image viewer:" + e.getMessage());
       }
    }



    // open browser live stream viewer
    private void openLiveStreamViewer(){
        try {
            // build the local viewer URL
            String viewerUrl = "http://localhost:" + MediaWebServer.PORT + "/viewer?live=true";

            lineReader.printAbove("Opening live stream viewer");

            // open the default browser if desktop support exists
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                if (desktop.isSupported(Desktop.Action.BROWSE)){

                    desktop.browse(new URI(viewerUrl)); // need to convert into URI object, cant use raw string
                    return;
                }
            }

            // if above fails use this for linux systems
            new ProcessBuilder("xdg-open", viewerUrl).start();

        } catch (Exception e) {
            lineReader.printAbove("Failed to open live stream viewer: " + e.getMessage());
        }
    }

     */


}
