package stegobmp.steg;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import stegobmp.bmp.BmpFile;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.PayloadFormat;

public final class LsbiExtractTest {

    private static final Lsbi LSBI = new Lsbi();
    private static final String PNG_SHA256 =
            "b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d";

    private LsbiExtractTest() {
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        selectiveInversionsAndBitOrder();
        System.out.println("OK: indicadores 1010, patrones, orden de bits y salto del rojo");
        completeBytesOnly();
        System.out.println("OK: cuerpos pequeños, bits sobrantes y bytes nulos");
        officialPayload();
        System.out.println("OK: PNG oficial, formato del payload y portador sin modificaciones");
    }

    private static void selectiveInversionsAndBitOrder() {
        byte[] body = {
            (byte) 0xA1, (byte) 0xB0, (byte) 0xC1, (byte) 0xD0,
            (byte) 0x80, (byte) 0xFF, (byte) 0x82, (byte) 0x84,
            (byte) 0xFF, (byte) 0x86, (byte) 0x81, (byte) 0xFF,
            (byte) 0x83, (byte) 0x84, (byte) 0xFF, (byte) 0x86
        };
        byte[] original = body.clone();
        assertBytes(new byte[] {(byte) 0xA6}, LSBI.extractAll(body), "vector con indicadores 1010");
        assertBytes(original, body, "el vector original debe conservarse");
        body[0] &= (byte) 0xFE;
        body[2] &= (byte) 0xFE;
        assertBytes(new byte[] {0x0C}, LSBI.extractAll(body), "el mismo vector sin inversiones");
    }

    private static void completeBytesOnly() {
        for (int bodyLength = 0; bodyLength <= 40; bodyLength++) {
            byte[] body = new byte[bodyLength];
            Arrays.fill(body, (byte) 0xFF);
            int expectedLength;
            if (bodyLength < 16) {
                expectedLength = 0;
            } else if (bodyLength < 28) {
                expectedLength = 1;
            } else if (bodyLength < 40) {
                expectedLength = 2;
            } else {
                expectedLength = 3;
            }
            assertBytes(new byte[expectedLength], LSBI.extractAll(body),
                    "bytes completos para cuerpo de longitud " + bodyLength);
        }
    }

    private static void officialPayload() throws NoSuchAlgorithmException {
        byte[] body = BmpFile.read(Path.of("materiales/ejemplo/ladoLSBI.bmp")).body();
        byte[] original = body.clone();
        byte[] recovered = LSBI.extractAll(body);
        if (recovered.length != 76_799) {
            throw new AssertionError("la extracción debe devolver los 76.799 bytes recuperables");
        }
        HiddenFile file = PayloadFormat.unpack(recovered);
        if (file.content().length != 44_886 || !file.extension().equals(".png")) {
            throw new AssertionError("el payload oficial debe contener un PNG de 44.886 bytes");
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.content()));
        if (!PNG_SHA256.equals(hash)) {
            throw new AssertionError("SHA-256 incorrecto del PNG oficial: " + hash);
        }
        assertBytes(original, body, "el portador oficial debe conservarse");
    }

    private static void assertBytes(byte[] expected, byte[] actual, String description) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + ": los bytes recuperados no coinciden");
        }
    }
}
