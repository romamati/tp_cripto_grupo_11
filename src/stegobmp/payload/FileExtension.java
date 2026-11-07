package stegobmp.payload;

import java.nio.file.Path;

public final class FileExtension {

    private static final char DOT = '.';
    private static final String NONE = "";

    private FileExtension() {
    }

    public static String of(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return NONE;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf(DOT);
        boolean hasExtension = dot > 0 && dot < name.length() - 1;
        return hasExtension ? name.substring(dot) : NONE;
    }
}
