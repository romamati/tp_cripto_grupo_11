package stegobmp.steg;

import java.nio.file.Path;
import java.util.Arrays;
import stegobmp.bmp.BmpFile;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.PayloadFormat;

public final class LsbiEmbedTest {

    private static final Lsbi LSBI = new Lsbi();

    private LsbiEmbedTest() {
    }

    public static void main(String[] args) {
        selectivePatternInversion();
        System.out.println("OK: ocultamiento con inversión selectiva e indicadores 1010");
        binaryRoundTrip();
        System.out.println("OK: ida y vuelta con los 256 valores de byte y payload sin modificaciones");
        rejectsBeforeMutation();
        System.out.println("OK: rechazo por capacidad o falta de indicadores antes de modificar el cuerpo");
        officialCarrier();
        System.out.println("OK: reproducción exacta del cuerpo de ladoLSBI.bmp");
    }

    private static void selectivePatternInversion() {
        byte[] body = {
            (byte) 0xA0, (byte) 0xB1, (byte) 0xC0, (byte) 0xD1,
            (byte) 0x80, (byte) 0xFF, (byte) 0x82, (byte) 0x84,
            (byte) 0xFF, (byte) 0x86, (byte) 0x81, (byte) 0xFF,
            (byte) 0x83, (byte) 0x84, (byte) 0xFF, (byte) 0x86
        };
        byte[] expected = {
            (byte) 0xA1, (byte) 0xB0, (byte) 0xC1, (byte) 0xD0,
            (byte) 0x80, (byte) 0xFF, (byte) 0x82, (byte) 0x84,
            (byte) 0xFF, (byte) 0x86, (byte) 0x81, (byte) 0xFF,
            (byte) 0x83, (byte) 0x84, (byte) 0xFF, (byte) 0x86
        };
        LSBI.embed(body, new byte[] {(byte) 0xA6});
        assertBytes(expected, body, "vector de ocultamiento con indicadores 1010");
    }

    private static void binaryRoundTrip() {
        byte[] body = new byte[4_096];
        for (int index = 0; index < body.length; index++) {
            body[index] = (byte) (index * 37 + 11);
        }
        byte[] data = new byte[256];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) index;
        }
        byte[] originalData = data.clone();
        LSBI.embed(body, data);
        byte[] recovered = LSBI.extractAll(body);
        assertBytes(originalData, Arrays.copyOf(recovered, data.length), "ida y vuelta binaria");
        assertBytes(originalData, data, "el payload original debe conservarse");
    }

    private static void rejectsBeforeMutation() {
        byte[] body = new byte[16];
        Arrays.fill(body, (byte) 0xAB);
        assertRejectedWithoutMutation(body, new byte[2]);
        assertRejectedWithoutMutation(new byte[] {1, 2, 3}, new byte[0]);
    }

    private static void assertRejectedWithoutMutation(byte[] body, byte[] data) {
        byte[] original = body.clone();
        try {
            LSBI.embed(body, data);
        } catch (IllegalArgumentException e) {
            assertBytes(original, body, "el cuerpo rechazado debe conservarse");
            return;
        }
        throw new AssertionError("el ocultamiento debe rechazar un cuerpo sin espacio suficiente");
    }

    private static void officialCarrier() {
        BmpFile reference = BmpFile.read(Path.of("materiales/ejemplo/ladoLSBI.bmp"));
        HiddenFile file = PayloadFormat.unpack(LSBI.extractAll(reference.body()));
        BmpFile carrier = BmpFile.read(Path.of("materiales/ejemplo/lado.bmp"));
        LSBI.embed(carrier.body(), PayloadFormat.pack(file));
        assertBytes(reference.body(), carrier.body(), "cuerpo del ejemplo oficial");
    }

    private static void assertBytes(byte[] expected, byte[] actual, String description) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + ": los bytes no coinciden");
        }
    }
}
