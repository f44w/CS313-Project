import FrameStreaming.VideoFrameStreamHandler;
import WebcamStreaming.WebcamStreamHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class MediaWebServer {

    private static HttpServer server;
    public static final int PORT = 1234;

    // local web server for displaying downloaded media files
    public static void startMediaServer(){
        try{
            // if already running dont start again
            if (server != null){
                return;
            }

            server = HttpServer.create(new InetSocketAddress(PORT), 0); // create on localhost

            // file routes
            server.createContext("/viewer", new MediaViewerHandler()); // decides what kind of page to display
            server.createContext("/media", new MediaFileHandler()); // media file bytes from download folder

            // live stream routes
            server.createContext("/webcam-stream", new WebcamStreamHandler());
            server.createContext("/video-frame-stream", new VideoFrameStreamHandler());

            //server.createContext("/stream-current", new OldStreamMethod.StreamHandler());

            server.start();

            System.out.println("Web Server started on local http://localhost:" + PORT);
        } catch (IOException e){
            System.out.println("Failed to start media viewer server:" + e.getMessage());
        }
    }
}
