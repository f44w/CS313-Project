import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MediaUtils {

    // check whether the downloaded file looks like a video
    public static boolean isFileVideo(File file){

        String name = file.getName().toLowerCase();

        // accept common video extensions
        // accepting only mp4 for testing right now
        return name.endsWith(".mp4");
    }

    public static boolean isFileImage(File file){
        try{
            BufferedImage image = ImageIO.read(file); // return null if file not image
            return image != null;
        } catch (IOException e){
            return false; // read failure treat as not image
        }
    }

    // return HTTp content type for video file
    public static String getVideoContentType(File file){
        String name = file.getName().toLowerCase();

        // only accepting mp4 right now
        if (name.endsWith(".mp4")){
            return "video/mp4";
        }

        return null;
    }
}
