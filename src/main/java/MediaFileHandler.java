import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

// serves downloaded media files from the downloads folder
public class MediaFileHandler implements HttpHandler {

    // runs everytime browser requests media file
    @Override
    public void handle(HttpExchange exchange) throws IOException {

        // get path of request and extract filename
        String path = exchange.getRequestURI().getPath();
        String filename = path.replaceFirst("/media/?", "");

        File file = new File("downloads", filename); // build full file path inside downloads folder

        if (!file.exists() || !file.isFile()) {
            // sends error response to browser and closes response
            byte[] notFound = "File not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, notFound.length);
            OutputStream os = exchange.getResponseBody();
            os.write(notFound);
            os.close();
            return;
        }

        // determine what HTTP header to send (filetype)
        String mediaType = Files.probeContentType(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", mediaType);

        // read entire file and send success response
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        exchange.sendResponseHeaders(200, fileBytes.length);

        // write file bytes to browser
        OutputStream os = exchange.getResponseBody();
        os.write(fileBytes);
        os.close();
    }
}