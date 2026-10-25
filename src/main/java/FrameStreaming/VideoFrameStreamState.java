package FrameStreaming;

public class VideoFrameStreamState {

    private static volatile boolean streaming = false;
    private static volatile byte[] latestFrame = null;
    private static volatile long frameNumber = 0;

    public static synchronized void start() {
        streaming = true;
        latestFrame = null;
        frameNumber = 0;
    }

    public static synchronized void updateFrame(byte[] frameBytes) {
        latestFrame = frameBytes;
        frameNumber++;
    }

    public static synchronized void finishStream() {streaming = false;}

    public static byte[] getLatestFrame() {return latestFrame;}

    public static long getFrameNumber() {return frameNumber;}

    public static boolean isStreaming() {return streaming;}
}