import java.io.*;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.jline.reader.LineReader;
import com.github.sarxos.webcam.Webcam;
import com.github.eduramiba.webcamcapture.drivers.NativeDriver;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

// Implement Runnable as defines thread work
public class MessageSender implements Runnable {

   private PrintWriter out;
   private LineReader lineReader;
   private PendingFileTransfer[] pendingTransfer;
   private PendingVideoStream[] pendingVideoStream;
   private MessageReceiver receiver;

    public MessageSender(OutputStream out, LineReader lineReader, PendingFileTransfer[] pendingTransfer, MessageReceiver receiver, PendingVideoStream[] pendingVideoStream) {
        Webcam.setDriver(new NativeDriver());
      this.out = new PrintWriter(out, true);
      this.lineReader = lineReader;
      this.pendingTransfer = pendingTransfer;
      this.receiver = receiver;
      this.pendingVideoStream = pendingVideoStream;
      printServerUsage();
    }

    // Method to define what message sending thread will perform
    @Override
    public void run() {
        // open scanner
        // infinite loop waiting for user input to send
        while (true) {
            String message = lineReader.readLine("> ");

            if (message == null) {
                break;
            }

            // handle any pending file offers first
            if (pendingTransfer[0] != null){
                // user accepted file transfer
                if(message.equalsIgnoreCase("y")){

                    // copy transfer details and clear pending
                    PendingFileTransfer transfer = pendingTransfer[0];
                    pendingTransfer[0] = null;

                    lineReader.printAbove("Accepting file " + transfer.getFilename());

                    receiver.downloadPendingFile(transfer); // download in reciever

                    continue;
                }
                // user rejected file transfer
                if (message.equalsIgnoreCase("n")){
                    lineReader.printAbove("File transfer rejected");

                    pendingTransfer[0] = null; // clear pending

                    continue;
                }
            }

            // handle any pending stream requests
            if (pendingVideoStream[0] != null){
                // user accepted stream request
                if(message.equalsIgnoreCase("y")){

                    // copy transfer details and clear pending
                    PendingVideoStream stream = pendingVideoStream[0];
                    pendingVideoStream[0] = null;

                    lineReader.printAbove("Accepting stream request: " + stream.getFilename());

                   // launch correct reciever
                    if ("webcam".equalsIgnoreCase(stream.getFilename())) {
                        receiver.receivePendingWebcamStream(stream);
                    } else {
                        receiver.receivePendingVideoFrameStream(stream);
                    }

                    continue;
                }
                // user rejected stream request
                if (message.equalsIgnoreCase("n")){
                    lineReader.printAbove("Stream request rejected");
                    pendingVideoStream[0] = null; // clear pending
                    continue;
                }
            }


           // p2p handshaking
            if (message.startsWith("/send-file ")) {
                String[] split = message.split(" ", 3);
                // incorrect params
                if (split.length != 3) {
                    lineReader.printAbove("Usage: /send-file <target user> <filepath> ");
                    continue;
                }

                String targetUser = split[1];
                String filepath = split[2];
                File filetosend = new File(filepath);
                // file is incorrect or doesnt exist
                if (!filetosend.exists() || !filetosend.isFile()) {
                    lineReader.printAbove("File doesn't exist or is not a file ");
                    continue;
                }

                try {
                    // open a temporary p2p server on random port
                    ServerSocket p2pServ = new ServerSocket(0);
                    p2pServ.setSoTimeout(30000); // if not accepted within 30 seconds throw timeout exception

                    int port = p2pServ.getLocalPort();
                    String nIp;

                    try (final java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                        socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 6767);
                        nIp = socket.getLocalAddress().getHostAddress();
                    } catch (Exception e) {
                        nIp = "127.0.0.1"; // fallback to local
                    }

                    out.println("/signal-file " + targetUser + " " + nIp + " " + port + " " + filetosend.getName());
                    lineReader.printAbove("Waiting for recipient to accept file transfer ... ");

                    // new thread so chat isint waiting for this to finish
                    new Thread(() -> {
                        try (Socket p2pSocket = p2pServ.accept()) {
                            FileInputStream fis = new FileInputStream(filetosend);
                            OutputStream os = p2pSocket.getOutputStream();

                            lineReader.printAbove("Sending file " + filetosend.getName());

                            byte[] buffer = new byte[1024];
                            int bytesRead;

                            while ((bytesRead = fis.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                            lineReader.printAbove("File '" + filetosend.getName() + "' sent ");

                        } catch (java.net.SocketTimeoutException e) {
                            lineReader.printAbove("File transfer request timed out or was rejected: " + e.getMessage());
                        } catch (IOException e) {
                            lineReader.printAbove("Error:" + e.getMessage());
                        } finally {
                            try { p2pServ.close();
                            } catch (IOException ignored) {}
                        }
                    }).start();
                } catch (IOException e) {
                    lineReader.printAbove("Failed to establish peer to peer connection with: " + targetUser + e.getMessage());
                }

                continue;
            }


            // p2p webcam streaming
            if (message.startsWith("/stream-webcam ")) {
                String[] split = message.split(" ", 2);
                if (split.length != 2) {
                    lineReader.printAbove("Usage: /stream-webcam <target user>");
                    continue;
                }

                String targetUser = split[1];
                try {
                    // open a temporary p2p server on a random port
                    ServerSocket p2pServ = new ServerSocket(0);
                    p2pServ.setSoTimeout(30000);

                    int port = p2pServ.getLocalPort();
                    String nIp;

                    try (final java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                        socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 6767);
                        nIp = socket.getLocalAddress().getHostAddress();
                    } catch (Exception e) {
                        nIp = "127.0.0.1"; // fallback to local
                    }

                    // signal the user
                    out.println("/signal-stream " + targetUser + " " + nIp + " " + port + " webcam");
                    lineReader.printAbove("Waiting for User to accept webcam stream  ");

                    // spin up a new thread so we don't block the chat
                    new Thread(() -> {
                        Webcam webcam = null;
                        try (Socket p2pSocket = p2pServ.accept();
                             DataOutputStream dos = new DataOutputStream(p2pSocket.getOutputStream())) {

                            lineReader.printAbove("User accepted, Starting webcam stream");

                            webcam = Webcam.getDefault(); // get camera
                            if (webcam == null) {
                                lineReader.printAbove("Error: No webcam detected.");
                                return;
                            }

                            webcam.open();
                            lineReader.printAbove("Streaming webcam to " + targetUser + ". Close terminal or type /exit to stop.");

                            // keep capturing and sending frames while socket and webcam are open
                            while (!p2pSocket.isClosed() && webcam.isOpen()) {
                                BufferedImage image = webcam.getImage();

                                if (image != null) {
                                    // compress the frame into a JPEG byte array
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    ImageIO.write(image, "JPG", baos);
                                    byte[] imageBytes = baos.toByteArray();

                                    // send frame size then bytes
                                    dos.writeInt(imageBytes.length);
                                    dos.write(imageBytes);
                                    dos.flush();
                                }

                                // sleep briefly to target roughly 30 FPS
                                Thread.sleep(30);
                            }

                        } catch (java.net.SocketTimeoutException e) {
                            lineReader.printAbove("Webcam stream request timed out or was rejected.");
                        } catch (Exception e) {
                            lineReader.printAbove("Webcam streaming ended: " + e.getMessage());
                        } finally {
                            // webcam off when yup
                            if (webcam != null && webcam.isOpen()) {
                                webcam.close();
                            }
                            try {
                                p2pServ.close();
                            } catch (IOException ignored) {}
                        }
                    }).start();

                } catch (IOException e) {
                    lineReader.printAbove("Failed to establish webcam stream setup: " + e.getMessage());
                }

                continue;
            }

            // p2p video streaming
            if (message.startsWith("/stream-video ")) {

                String[] split = message.split(" ", 3); // split into command, target user, file path

                if (split.length != 3) {
                    lineReader.printAbove("Usage: /stream-video <target user> <filepath> ");
                    continue;
                }


                String targetUser = split[1];
                String filepath = split[2];

                File videoToStream = new File(filepath);

                // file is incorrect or doesnt exist
                if (!videoToStream.exists() || !videoToStream.isFile()) {
                    lineReader.printAbove("Video File doesn't exist or is not a file ");
                    continue;
                }

                if (!MediaUtils.isFileVideo(videoToStream)){
                    lineReader.printAbove("File is not a supported video file type ");
                    continue;
                }

                try {
                    // open a temporary p2p server on random port
                    ServerSocket p2pServ = new ServerSocket(0);
                    p2pServ.setSoTimeout(30000); // if not accepted within 30 seconds throw timeout exception

                    int port = p2pServ.getLocalPort();
                    String nIp; // sender IP

                    try (final java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                        socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 6767);
                        nIp = socket.getLocalAddress().getHostAddress();
                    } catch (Exception e) {
                        nIp = "127.0.0.1"; // fallback to local
                    }

                    out.println("/signal-stream " + targetUser + " " + nIp + " " + port + " " + videoToStream.getName()); // server notifies recipient about streaming request
                    lineReader.printAbove("Waiting for recipient to accept video stream ... ");

                    // new thread so chat isint waiting for this to finish
                    new Thread(() -> {
                        try (Socket p2pSocket = p2pServ.accept();
                             DataOutputStream dos = new DataOutputStream(p2pSocket.getOutputStream())) {

                            lineReader.printAbove("Streaming video frames from " + videoToStream.getName());

                            VideoCapture capture = new VideoCapture(videoToStream.getAbsolutePath()); // capture allows to streat video like a sequence of frames

                            // make sure video opened correctly
                            if (!capture.isOpened()) {
                                lineReader.printAbove("Failed to open video file for frame streaming");
                                capture.release();
                                return;
                            }

                            double fps = capture.get(Videoio.CAP_PROP_FPS);  // get frame rate (time between frames)

                            // fallback if FPS is missing or invalid
                            if (fps <= 0 || Double.isNaN(fps)) {
                                fps = 25.0;
                            }
                            // convert FPS to frame delay in milliseconds
                            // e.g. 1000/25 = 40ms between frames
                            long frameDelayMs = Math.max(1L, Math.round(1000.0 / fps));

                            Mat frame = new Mat(); // frame buffer -> holds one decoded frame at a time
                            MatOfByte jpegBuffer = new MatOfByte(); // encoded version of frame ready to send

                            // read frames until end of file
                            while (capture.read(frame)) {

                                boolean ok = Imgcodecs.imencode(".jpg", frame, jpegBuffer); // encode to JPEG bytes

                                // encoding failed skip frame
                                if (!ok) {
                                    continue;
                                }

                                // convert encoded frame to byte array send frame bytes
                                byte[] jpegBytes = jpegBuffer.toArray();
                                dos.writeInt(jpegBytes.length);
                                dos.write(jpegBytes);
                                dos.flush();

                                // wait roughly one frame duration
                                try {
                                    Thread.sleep(frameDelayMs);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }

                            // cleanup
                            capture.release();
                            frame.release();
                            jpegBuffer.release();

                            // tell user stream completed
                            lineReader.printAbove("Finished video frame streaming of '" + videoToStream.getName() + "'");

                        } catch (java.net.SocketTimeoutException e) {
                            lineReader.printAbove("Video stream request timed out or was rejected: " + e.getMessage());
                        } catch (IOException e) {
                            lineReader.printAbove("Streaming Error: " + e.getMessage());
                        } finally {
                            try {
                                p2pServ.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }).start();

                } catch (IOException e) {
                    lineReader.printAbove("Failed to establish video stream with: " + targetUser + e.getMessage());
                }

                continue;
            }

           out.println(message);

           // destorys only the MessageSender object
            if (message.equalsIgnoreCase("/exit")) {
                break;
            }
        }
    }

    private void printServerUsage(){
        System.out.println("Welcome! ");
        System.out.println(Constants.CLIENT_COMMAND_HELP_STRING);
    }
}
