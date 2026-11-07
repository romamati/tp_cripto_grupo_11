package stegobmp.bmp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import stegobmp.Messages;
import stegobmp.StegoException;
import stegobmp.io.FileAccess;

public final class BmpFile {

    private static final byte[] SIGNATURE = "BM".getBytes(StandardCharsets.US_ASCII);
    private static final int PIXEL_DATA_OFFSET_POSITION = 10;
    private static final int BITS_PER_PIXEL_POSITION = 28;
    private static final int COMPRESSION_POSITION = 30;
    private static final int HEADER_SIZE = 54;
    private static final int SUPPORTED_BITS_PER_PIXEL = 24;
    private static final long NO_COMPRESSION = 0;

    private final byte[] header;
    private final byte[] body;

    private BmpFile(byte[] header, byte[] body) {
        this.header = header;
        this.body = body;
    }

    public static BmpFile read(Path path) {
        byte[] bytes = FileAccess.read(path, "el portador");
        int pixelDataOffset = validate(bytes, "el portador " + FileAccess.quoted(path));
        return new BmpFile(
                Arrays.copyOfRange(bytes, 0, pixelDataOffset),
                Arrays.copyOfRange(bytes, pixelDataOffset, bytes.length));
    }

    public byte[] body() {
        return body;
    }

    public void write(Path path) {
        FileAccess.write(path, "la imagen de salida", header, body);
    }

    private static int validate(byte[] bytes, String name) {
        if (bytes.length < HEADER_SIZE || !hasSignature(bytes)) {
            throw new StegoException(name + " no es un archivo BMP válido.");
        }
        int bitsPerPixel = readUnsignedShort(bytes, BITS_PER_PIXEL_POSITION);
        if (bitsPerPixel != SUPPORTED_BITS_PER_PIXEL) {
            throw new StegoException(name + " tiene " + bitsPerPixel + " bits por píxel y solo se admiten BMP de "
                    + SUPPORTED_BITS_PER_PIXEL + " bits.");
        }
        long compression = readUnsignedInt(bytes, COMPRESSION_POSITION);
        if (compression != NO_COMPRESSION) {
            throw new StegoException(name + " está comprimido (tipo de compresión " + compression
                    + ") y solo se admiten BMP sin compresión.");
        }
        return validatePixelDataOffset(readUnsignedInt(bytes, PIXEL_DATA_OFFSET_POSITION), bytes.length, name);
    }

    private static int validatePixelDataOffset(long offset, int fileLength, String name) {
        if (offset < HEADER_SIZE || offset >= fileLength) {
            throw new StegoException(name + " no es un BMP válido: indica que los píxeles empiezan en el byte "
                    + offset + " y el archivo mide " + fileLength + " bytes.");
        }
        if (offset != HEADER_SIZE) {
            Messages.warn("en " + name + " el encabezado mide " + offset + " bytes en lugar de " + HEADER_SIZE
                    + "; los píxeles se toman desde el byte " + offset + ".");
        }
        return (int) offset;
    }

    private static boolean hasSignature(byte[] bytes) {
        return Arrays.equals(bytes, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length);
    }

    private static int readUnsignedShort(byte[] bytes, int position) {
        return Short.toUnsignedInt(littleEndian(bytes).getShort(position));
    }

    private static long readUnsignedInt(byte[] bytes, int position) {
        return Integer.toUnsignedLong(littleEndian(bytes).getInt(position));
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
