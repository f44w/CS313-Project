package OldStreamMethod;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

// handles browser requests for streams
public class StreamHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException{

        File streamFile = new File("downloads","stream.mp4"); // path used by receiver

        // stream file doesnt exist yet
        if (!streamFile.exists()){

            byte[] body = "No stream yet".getBytes(StandardCharsets.UTF_8); // response body

            exchange.sendResponseHeaders(404,body.length);

            // write error
            try(OutputStream os = exchange.getResponseBody()){
                os.write(body);
            }

            return;
        }

        // tell browser file type and not to store old data
        exchange.getResponseHeaders().set("Content-Type","video/mp4");
        exchange.getResponseHeaders().set("Cache-Control","no-cache");

        exchange.sendResponseHeaders(200,0); // 0 - chunked transfer encoding keeps connection open sending in chunks

        long sentBytes = 0;
        int chunkCount = 0;

        byte[] buffer = new byte[64 * 1024]; // read 64KB chunks at a time

        try(OutputStream os = exchange.getResponseBody()){

            // continue streaming while there is data left in file
            while (StreamState.isStreamActive() || streamFile.length() > sentBytes){

                long fileLength = streamFile.length();

                if(fileLength > sentBytes){ // still new bytes not been sent

                    try (RandomAccessFile raf = new RandomAccessFile(streamFile, "r")) {  // allows to read from specified point
                        raf.seek(sentBytes); // skip bytes already sent

                        while (sentBytes < fileLength) {
                            int bytesRead = raf.read(buffer);

                            if (bytesRead == -1) {
                                break;
                            }

                            // send to browser now
                            os.write(buffer, 0, bytesRead);

                            chunkCount ++;
                            sentBytes = sentBytes + bytesRead;

                            // TESTING
                            if(chunkCount % 4 ==0){
                                os.flush();
                            }
                        }
                    }
                } else { // nothing new in file wait and check again

                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // interuppted restore flag and stop loop
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (IOException e) {
        }
    }
}