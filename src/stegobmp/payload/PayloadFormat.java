package stegobmp.payload;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import stegobmp.StegoException;

// Formato oculto: tamaño real (4 bytes big endian) || datos || extensión con punto || \0.
// Con cifrado se oculta: tamaño del cifrado (4 bytes big endian) || cifrado(formato anterior).
// ByteBuffer usa big endian por defecto, que es como guardan el tamaño los archivos de la cátedra.
public final class PayloadFormat {

    private static final int SIZE_BYTES = Integer.BYTES;
    private static final int TERMINATOR_BYTES = 1;
    // Un bloque interior mínimo es un archivo vacío sin extensión: tamaño cero y terminador.
    private static final int MIN_PLAIN_BLOCK_BYTES = SIZE_BYTES + TERMINATOR_BYTES;
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

    // El tamaño real puede ser cero; después de los datos tiene que quedar lugar al menos para el terminador.
    public static HiddenFile unpack(byte[] bytes) {
        long size = readSize(bytes);
        long available = bytes.length - SIZE_BYTES;
        if (size > available - TERMINATOR_BYTES) {
            throw new NoHiddenDataException("el tamaño leído (" + size + " bytes) no es válido, porque después de él"
                    + " solo quedan " + available + " bytes para los datos y la extensión");
        }
        int end = SIZE_BYTES + (int) size;
        return new HiddenFile(Arrays.copyOfRange(bytes, SIZE_BYTES, end), readExtension(bytes, end));
    }

    // El bloque cifrado nunca está vacío: contiene al menos el tamaño real y el terminador cifrados.
    public static byte[] encryptedBlock(byte[] bytes) {
        long size = readSize(bytes);
        long available = bytes.length - SIZE_BYTES;
        if (size < MIN_PLAIN_BLOCK_BYTES || size > available) {
            throw new NoHiddenDataException("el tamaño del bloque cifrado leído (" + size + " bytes) no es válido:"
                    + " debe ser de al menos " + MIN_PLAIN_BLOCK_BYTES + " bytes y después de él solo hay lugar para "
                    + available + " bytes");
        }
        return Arrays.copyOfRange(bytes, SIZE_BYTES, SIZE_BYTES + (int) size);
    }

    private static long readSize(byte[] bytes) {
        if (bytes.length < SIZE_BYTES) {
            throw new NoHiddenDataException("no hay espacio ni para el tamaño");
        }
        return Integer.toUnsignedLong(ByteBuffer.wrap(bytes).getInt());
    }

    private static byte[] terminatedExtension(String extension) {
        extensionProblem(extension).ifPresent(problem -> {
            throw new StegoException("no se puede ocultar la extensión \"" + extension + "\": " + problem + ".");
        });
        byte[] text = extension.getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(text, text.length + TERMINATOR_BYTES);
    }

    // La extensión se busca en todo lo recuperable, sin un largo máximo arbitrario.
    private static String readExtension(byte[] bytes, int start) {
        for (int index = start; index < bytes.length; index++) {
            if (bytes[index] == EXTENSION_TERMINATOR) {
                String extension = decodeUtf8(bytes, start, index - start);
                extensionProblem(extension).ifPresent(problem -> {
                    throw new NoHiddenDataException("después de los datos no hay una extensión válida: " + problem);
                });
                return extension;
            }
        }
        throw new NoHiddenDataException("después de los datos no hay una extensión terminada en \\0");
    }

    private static String decodeUtf8(byte[] bytes, int start, int length) {
        try {
            CharBuffer text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, length));
            return text.toString();
        } catch (CharacterCodingException e) {
            throw new NoHiddenDataException("después de los datos no hay una extensión en texto UTF-8 válido");
        }
    }

    // Se rechazan separadores de carpetas para que la extensión no pueda escribir fuera del destino pedido.
    private static Optional<String> extensionProblem(String extension) {
        if (extension.isEmpty()) {
            return Optional.empty();
        }
        if (!extension.startsWith(EXTENSION_START)) {
            return Optional.of("no empieza con \"" + EXTENSION_START + "\"");
        }
        boolean invalidCharacter = extension.chars()
                .anyMatch(c -> c == '/' || c == '\\' || Character.isISOControl(c));
        if (invalidCharacter) {
            return Optional.of("contiene separadores de carpetas o caracteres de control");
        }
        return Optional.empty();
    }
}
