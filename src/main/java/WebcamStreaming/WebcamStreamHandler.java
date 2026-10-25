package WebcamStreaming;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

// sends MJPEG stream to browser
public class WebcamStreamHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException{

        exchange.getResponseHeaders().set("Content-Type","multipart/x-mixed-replace; boundary=frame"); // tell browser this is an MJPEG stream

        exchange.getResponseHeaders().set("Cache-Control", "no-cache"); // prevent caching

        exchange.sendResponseHeaders(200,0);

        try (OutputStream os = exchange.getResponseBody()){
            long lastFrameNumber = -1;

            while (WebcamStreamState.isWebcamStreaming()){

                byte [] frame = WebcamStreamState.getLatestFrame();
                long frameNumber = WebcamStreamState.getFrameNumber();

                // only send when new frame exists
                if (frame != null && frameNumber != lastFrameNumber){

                    os.write(("--frame\r\n").getBytes());
                    os.write(("Content-Type: image/jpeg\r\n").getBytes());
                    os.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());
                    os.write(frame);
                    os.write(("\r\n").getBytes());
                    os.flush();

                    lastFrameNumber = frameNumber;
                } else {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (IOException e){

        }
    }
}
