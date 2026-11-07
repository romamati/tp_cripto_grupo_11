package stegobmp.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import stegobmp.StegoException;

public final class FileAccess {

    private FileAccess() {
    }

    public static byte[] read(Path path, String description) {
        rejectDirectory(path, description);
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new StegoException("no existe " + description + " " + quoted(path) + ".");
        } catch (AccessDeniedException e) {
            throw new StegoException("no hay permiso para leer " + description + " " + quoted(path) + ".");
        } catch (IOException e) {
            throw new StegoException("no se pudo leer " + description + " " + quoted(path) + ": " + e.getMessage());
        }
    }

    public static void write(Path path, String description, byte[]... parts) {
        rejectDirectory(path, description);
        try (OutputStream output = Files.newOutputStream(path)) {
            for (byte[] part : parts) {
                output.write(part);
            }
        } catch (NoSuchFileException e) {
            throw new StegoException("no se pudo crear " + description + " " + quoted(path)
                    + ": no existe la carpeta de destino.");
        } catch (AccessDeniedException e) {
            throw new StegoException("no hay permiso para escribir " + description + " " + quoted(path) + ".");
        } catch (IOException e) {
            throw new StegoException("no se pudo escribir " + description + " " + quoted(path) + ": " + e.getMessage());
        }
    }

    public static String quoted(Path path) {
        return "\"" + path + "\"";
    }

    private static void rejectDirectory(Path path, String description) {
        if (Files.isDirectory(path)) {
            throw new StegoException(description + " " + quoted(path) + " es una carpeta, no un archivo.");
        }
    }
}
