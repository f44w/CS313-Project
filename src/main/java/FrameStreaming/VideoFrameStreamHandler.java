package FrameStreaming;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;

public class VideoFrameStreamHandler implements HttpHandler {

    // build and send response in here
    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // tell browser its a mulitpart stream and not to cache old frames
        exchange.getResponseHeaders().set("Content-Type", "multipart/x-mixed-replace; boundary=frame");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");

        exchange.sendResponseHeaders(200, 0); // tells browser to keep reading until we stop sending

        try (OutputStream os = exchange.getResponseBody()) {

            long lastFrameNumber = -1;

            while (VideoFrameStreamState.isStreaming()) {

                // read latest frame and frame counter
                byte[] frame = VideoFrameStreamState.getLatestFrame();
                long frameNumber = VideoFrameStreamState.getFrameNumber();

                // only send if a new frame has arrived
                if (frame != null && frameNumber != lastFrameNumber) {

                     /*
                    browser receives this:
                        --frame
                        Content-Type: image/jpeg
                        Content-Length: 12345
                     */
                    os.write(("--frame\r\n").getBytes());
                    os.write(("Content-Type: image/jpeg\r\n").getBytes());
                    os.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());

                    // write and push frame to browser
                    os.write(frame);
                    os.write(("\r\n").getBytes()); // blank line after
                    os.flush();

                    lastFrameNumber = frameNumber;

                } else {

                    // no new frame yet pause then try again
                    try {
                        Thread.sleep(15);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

        } catch (IOException e) {
            // client disconnected, stream ended, or browser closed
        }
    }
}