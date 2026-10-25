package WebcamStreaming;

public class WebcamStreamState {
    private static volatile boolean streaming = false;
    private static volatile byte[] latestFrame = null; // most recent JPEG frame
    private static volatile long frameNumber = 0; // counts how many frames we have received

    public static synchronized void start() {
        streaming = true;
        latestFrame = null;
        frameNumber = 0;
    }

    public static synchronized void updateFrame(byte[] frameBytes) {
        latestFrame = frameBytes;
        frameNumber ++;
    }
    public static byte[] getLatestFrame(){return latestFrame;}
    public static long getFrameNumber(){return frameNumber;}
    public static synchronized void finishWebcamStream() {streaming = false;}
    public static boolean isWebcamStreaming() {return streaming;}
}
