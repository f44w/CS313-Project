import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

// returns HTML page for displaying image or playing video file or video stream
public class MediaViewerHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String query = exchange.getRequestURI().getQuery();
        String html;

        // build webcam livestream page
        if (query != null && query.equals("webcam=true")){
            html =
                    "<!DOCTYPE html>" +
                            "<html>" +
                            "<head><title>Webcam Stream</title></head>" +
                            "<body style='margin:0; background:#111; display:flex; justify-content:center; align-items:center; height:100vh;'>" +
                            "<img src='/webcam-stream' style='max-width:100%; max-height:100%;' />" +
                            "</body>" +
                            "</html>";
            // build video streaming page
        } else if (query != null && query.equals("videoframes=true")) {

            html =
                    "<!DOCTYPE html>" +
                            "<html>" +
                            "<head><title>Video Frame Stream</title></head>" +
                            "<body style='margin:0; background:#111; display:flex; justify-content:center; align-items:center; height:100vh;'>" +
                            "<img src='/video-frame-stream' style='max-width:100%; max-height:100%;' />" +
                            "</body>" +
                            "</html>";
            // build live stream page
            // NOT IN USE OLD METHOD
        } else if (query != null && query.equals("live=true")) {

            html =
                    "<!DOCTYPE html>" +
                            "<html>" +
                            "<head><title>Live Stream</title></head>" +
                            "<body style='margin:0; background:#111; display:flex; justify-content:center; align-items:center; height:100vh;'>" +
                            "<video controls autoplay style='max-width:100%; max-height:100%;'>" +
                            "<source src='/stream-current' type='video/mp4'>" +
                            "Your browser does not support video playback." +
                            "</video>" +
                            "</body>" +
                            "</html>";

            // build static file pages depending on whether img or video
        } else if (query != null && query.startsWith("file=")) {

            // decode the filename from the query string
            String filename = query.substring("file=".length());
            filename = URLDecoder.decode(filename, StandardCharsets.UTF_8);

            File mediaFile = new File("downloads", filename); // build file

            // build image display page for image file extensions
            if (MediaUtils.isFileImage(mediaFile)) {

                html =
                        "<!DOCTYPE html>" +
                                "<html>" +
                                "<head><title>Media Viewer</title></head>" +
                                "<body style='margin:0; background:#111; display:flex; justify-content:center; align-items:center; height:100vh;'>" +
                                "<img src='/media/" + filename + "' style='max-width:100%; max-height:100%;' />" +
                                "</body>" +
                                "</html>";

                // build video display page for video file extensions
            } else if (MediaUtils.isFileVideo(mediaFile)) {

                html =
                        "<!DOCTYPE html>" +
                                "<html>" +
                                "<head><title>Media Viewer</title></head>" +
                                "<body style='margin:0; background:#111; display:flex; justify-content:center; align-items:center; height:100vh;'>" +
                                "<video controls autoplay style='max-width:100%; max-height:100%;'>" +
                                "<source src='/media/" + filename + "'>" +
                                "Your browser does not support video playback." +
                                "</video>" +
                                "</body>" +
                                "</html>";

            } else { // build page for unsupported file type
                html =
                        "<!DOCTYPE html>" +
                                "<html>" +
                                "<head><title>Media Viewer</title></head>" +
                                "<body style='font-family:sans-serif; background:#111; color:white; padding:20px;'>" +
                                "<p>Unsupported media type for file: " + filename + "</p>" +
                                "</body>" +
                                "</html>";
            }
        } else {
            // bad request
            byte[] badRequest = "Missing file or live parameter".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, badRequest.length);

            // send bad response
            OutputStream os = exchange.getResponseBody();
            os.write(badRequest);
            os.close();
            return;
        }

        // convert to bytes and send response
        byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");  // tell browser this is HTML
        exchange.sendResponseHeaders(200, responseBytes.length);

        // send page to browser and close response
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}