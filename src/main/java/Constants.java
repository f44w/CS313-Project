import java.util.regex.Pattern;

public class Constants{

    // no instantiation
    public Constants(){}

    public static final String CHANNEL_LIST_FILE_NAME = "channels.json";

    public static final long CLIENT_IDLE_TIMEOUT_MS = 300_000 ; // how long before client is idle, 5 mins
    public static final long CLIENT_IDLE_CHECK_INTERVAL_MS = 30_000; // time between idle check 30 seconds

    // ANSI Terminal Colour Macros
    public static final String RESET = "\033[0m";  // Text Reset
    public static final String BLACK = "\033[0;30m";   // BLACK
    public static final String RED = "\033[0;31m";     // RED
    public static final String GREEN = "\033[0;32m";   // GREEN
    public static final String YELLOW = "\033[0;33m";  // YELLOW
    public static final String BLUE = "\033[0;34m";    // BLUE
    public static final String PURPLE = "\033[0;35m";  // PURPLE
    public static final String CYAN = "\033[0;36m";    // CYAN
    public static final String WHITE = "\033[0;37m";   // WHITE


    // channel names between 3 and 16 chars
    // lower case letters only
    public static final Pattern VALID_CHANNEL_NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,16}$");
    public static final Pattern VALID_CLIENT_NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{2,15}$");

    public static final String CLIENT_COMMAND_HELP_STRING = ("""
        Commands:
        \t'/join <channel name>' to join a new channel
        \t'/nick <name>' to set nickname
        \t'/status-change <busy>' to change your status
        \t'/send-file <taget user> <file path>' sends an image or video file to a user
        \t'/list' to view all channels
        \t'/where' to view current channel
        \t'/part' returns to main channel
        \t'/who' returns list of all users in current channel
        \t'/stream-video <target user> <filepath>' starts video stream with a user
        \t'/stream-webcam <target user>' starts webcam stream with a user
        \t'/help' prints this usage text
        \t'/exit' to exit the server
        """);
}

