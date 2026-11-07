package stegobmp;

public final class Messages {

    private Messages() {
    }

    public static void info(String message) {
        System.out.println(message);
    }

    public static void warn(String message) {
        System.err.println("Aviso: " + message);
    }

    public static void error(String message) {
        System.err.println("Error: " + message);
    }
}
