package stegobmp.payload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import stegobmp.StegoException;

public final class PayloadFormatTest {

    private static final HexFormat HEX = HexFormat.of();

    private PayloadFormatTest() {
    }

    public static void main(String[] args) {
        emptyFiles();
        System.out.println("OK: archivos vacíos con y sin extensión conservan tamaño cero y terminador");
        longAndUnicodeExtensions();
        System.out.println("OK: extensiones de más de 15 bytes y con caracteres UTF-8");
        nulBytesAndTrailingData();
        System.out.println("OK: contenido con bytes NUL y datos sobrantes después del terminador");
        invalidPlainPayloads();
        System.out.println("OK: rechazo de tamaños, terminadores y extensiones inválidos");
        encryptedBlocks();
        System.out.println("OK: delimitación del bloque cifrado con su prefijo exterior");
    }

    private static void emptyFiles() {
        byte[] packed = PayloadFormat.pack(new HiddenFile(new byte[0], ".txt"));
        require(HEX.formatHex(packed).equals("000000002e74787400"), "formato de archivo vacío: "
                + HEX.formatHex(packed));
        HiddenFile file = PayloadFormat.unpack(packed);
        require(file.content().length == 0 && file.extension().equals(".txt"), "archivo vacío recuperado");
        byte[] bare = PayloadFormat.pack(new HiddenFile(new byte[0], ""));
        require(HEX.formatHex(bare).equals("0000000000"), "vacío sin extensión: " + HEX.formatHex(bare));
        HiddenFile recovered = PayloadFormat.unpack(bare);
        require(recovered.content().length == 0 && recovered.extension().isEmpty(), "vacío sin extensión recuperado");
    }

    private static void longAndUnicodeExtensions() {
        for (String extension : new String[] {".extensionmuylarga", "." + "y".repeat(300), ".ñandú",
                ".0123456789abcde", ".0123456789abcdef"}) {
            byte[] content = {1, 2, 3};
            HiddenFile file = PayloadFormat.unpack(PayloadFormat.pack(new HiddenFile(content, extension)));
            require(Arrays.equals(content, file.content()), "contenido con extensión " + extension);
            require(extension.equals(file.extension()), "extensión " + extension);
        }
    }

    private static void nulBytesAndTrailingData() {
        byte[] content = {0, 0, 0, 0, 'x', 0, (byte) 0xFF, 0};
        byte[] packed = PayloadFormat.pack(new HiddenFile(content, ".bin"));
        byte[] withTail = Arrays.copyOf(packed, packed.length + 64);
        Arrays.fill(withTail, packed.length, withTail.length, (byte) 0xA5);
        HiddenFile file = PayloadFormat.unpack(withTail);
        require(Arrays.equals(content, file.content()) && file.extension().equals(".bin"), "bytes NUL y cola");
    }

    private static void invalidPlainPayloads() {
        expectNoHiddenData(new byte[] {0, 0, 0}, "menos de 4 bytes");
        expectNoHiddenData(new byte[] {0, 0, 0, 0}, "tamaño cero sin terminador");
        expectNoHiddenData(sized(3, new byte[] {1, 2, 3}), "datos sin lugar para el terminador");
        expectNoHiddenData(sized(4, new byte[] {1, 2, 3}), "tamaño mayor que lo disponible");
        expectNoHiddenData(sized(-1, new byte[16]), "tamaño 0xFFFFFFFF");
        expectNoHiddenData(sized(1, new byte[] {9, '.', 'b', 'i', 'n'}), "extensión sin terminador");
        expectNoHiddenData(sized(1, bytes(9, "bin\0")), "extensión sin punto");
        expectNoHiddenData(sized(1, bytes(9, "./../x\0")), "extensión con separador");
        expectNoHiddenData(sized(1, bytes(9, ".a\\b\0")), "extensión con barra invertida");
        expectNoHiddenData(sized(1, bytes(9, ".a\nb\0")), "extensión con salto de línea");
        expectNoHiddenData(sized(1, new byte[] {9, '.', (byte) 0xC3, 0}), "extensión UTF-8 inválida");
        for (String extension : new String[] {"txt", "./x", ".a\tb"}) {
            try {
                PayloadFormat.pack(new HiddenFile(new byte[1], extension));
                throw new AssertionError("debía rechazar al ocultar la extensión " + extension);
            } catch (NoHiddenDataException e) {
                throw new AssertionError("el rechazo al ocultar no debe confundirse con ausencia de datos");
            } catch (StegoException e) {
                require(e.getMessage().contains("extensión"), "mensaje al ocultar " + extension);
            }
        }
    }

    private static void encryptedBlocks() {
        byte[] block = {10, 20, 30, 40, 50};
        byte[] prefixed = PayloadFormat.withSizePrefix(block);
        require(HEX.formatHex(prefixed).equals("000000050a141e2832"), "prefijo exterior big endian");
        byte[] withTail = Arrays.copyOf(prefixed, prefixed.length + 3);
        require(Arrays.equals(block, PayloadFormat.encryptedBlock(withTail)), "bloque cifrado delimitado");
        expectEncryptedRejected(PayloadFormat.withSizePrefix(new byte[0]), "bloque cifrado vacío");
        expectEncryptedRejected(PayloadFormat.withSizePrefix(new byte[4]), "bloque menor que tamaño y terminador");
        expectEncryptedRejected(Arrays.copyOf(prefixed, prefixed.length - 1), "bloque cifrado truncado");
        expectEncryptedRejected(new byte[] {0, 0}, "sin lugar para el prefijo");
        byte[] large = ByteBuffer.allocate(8).putInt(0x8000_0000).array();
        expectEncryptedRejected(large, "tamaño que no entra en un int");
    }

    private static void expectNoHiddenData(byte[] bytes, String name) {
        try {
            PayloadFormat.unpack(bytes);
        } catch (NoHiddenDataException e) {
            return;
        }
        throw new AssertionError("debía rechazar: " + name);
    }

    private static void expectEncryptedRejected(byte[] bytes, String name) {
        try {
            PayloadFormat.encryptedBlock(bytes);
        } catch (NoHiddenDataException e) {
            return;
        }
        throw new AssertionError("debía rechazar: " + name);
    }

    private static byte[] sized(int size, byte[] rest) {
        return ByteBuffer.allocate(Integer.BYTES + rest.length).putInt(size).put(rest).array();
    }

    private static byte[] bytes(int first, String text) {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[encoded.length + 1];
        result[0] = (byte) first;
        System.arraycopy(encoded, 0, result, 1, encoded.length);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
