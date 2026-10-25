package OldStreamMethod;

import java.io.File;

public class StreamState {

    private static volatile boolean streaming = false; // true while reciever still getting bytes
    private static volatile File currentFile = null;

    public static synchronized void startStream(File file, String contentType){
        streaming = true;
        contentType = "video/mp4";
    }
    public static void finishStream(){streaming = false;}
    public static boolean isStreamActive(){return  streaming;}
}
