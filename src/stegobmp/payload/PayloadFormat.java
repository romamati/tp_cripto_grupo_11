package stegobmp.payload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import stegobmp.StegoException;

// ByteBuffer usa big endian por defecto, que es como guardan el tamaño los archivos de la cátedra.
public final class PayloadFormat {

    private static final int SIZE_BYTES = Integer.BYTES;
    private static final int MAX_EXTENSION_BYTES = 16;
    private static final byte EXTENSION_TERMINATOR = 0;
    private static final String EXTENSION_START = ".";

    private PayloadFormat() {
    }

    public static byte[] pack(HiddenFile file) {
        byte[] content = file.content();
        byte[] extension = terminatedExtension(file.extension());
        return ByteBuffer.allocate(SIZE_BYTES + content.length + extension.length)
                .putInt(content.length)
                .put(content)
                .put(extension)
                .array();
    }

    public static byte[] withSizePrefix(byte[] block) {
        return ByteBuffer.allocate(SIZE_BYTES + block.length)
                .putInt(block.length)
                .put(block)
                .array();
    }

    public static HiddenFile unpack(byte[] bytes) {
        byte[] content = sizePrefixedBlock(bytes);
        return new HiddenFile(content, readExtension(bytes, SIZE_BYTES + content.length));
    }

    public static byte[] sizePrefixedBlock(byte[] bytes) {
        if (bytes.length < SIZE_BYTES) {
            throw new NoHiddenDataException("no hay espacio ni para el tamaño");
        }
        long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes).getInt());
        long available = bytes.length - SIZE_BYTES;
        if (size == 0 || size > available) {
            throw new NoHiddenDataException("el tamaño leído (" + size + " bytes) no es válido, porque después de él"
                    + " solo hay lugar para " + available + " bytes");
        }
        return Arrays.copyOfRange(bytes, SIZE_BYTES, SIZE_BYTES + (int) size);
    }

    private static byte[] terminatedExtension(String extension) {
        byte[] text = extension.getBytes(StandardCharsets.UTF_8);
        if (text.length + 1 > MAX_EXTENSION_BYTES) {
            throw new StegoException("la extensión \"" + extension + "\" es demasiado larga: se admiten hasta "
                    + (MAX_EXTENSION_BYTES - 1) + " bytes contando el punto.");
        }
        return Arrays.copyOf(text, text.length + 1);
    }

    private static String readExtension(byte[] bytes, int start) {
        int end = Math.min(bytes.length, start + MAX_EXTENSION_BYTES);
        for (int index = start; index < end; index++) {
            if (bytes[index] == EXTENSION_TERMINATOR) {
                return validExtension(new String(bytes, start, index - start, StandardCharsets.UTF_8));
            }
        }
        throw new NoHiddenDataException("después de los datos no hay una extensión terminada en \\0");
    }

    private static String validExtension(String extension) {
        if (!extension.isEmpty() && !extension.startsWith(EXTENSION_START)) {
            throw new NoHiddenDataException("después de los datos no hay una extensión que empiece con \""
                    + EXTENSION_START + "\"");
        }
        return extension;
    }
}
