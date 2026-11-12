package stegobmp.crypto;

import java.util.Arrays;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.PayloadFormat;
import stegobmp.steg.StegAlgorithm;
import stegobmp.steg.StegMethod;

// Recorre los tres métodos por los cuatro algoritmos y los cuatro modos (48 combinaciones) en memoria,
// con el mismo encadenamiento que Embedder y Extractor.
public final class EncryptedPayloadMatrixTest {

    private static final int[] CONTENT_LENGTHS = {0, 1, 3, 4, 7, 8, 11, 12, 16, 17, 40};
    private static final String EXTENSION = ".bin";
    private static final int BODY_BYTES = 4_096;

    private EncryptedPayloadMatrixTest() {
    }

    public static void main(String[] args) {
        int combinations = 0;
        for (StegAlgorithm steg : StegAlgorithm.values()) {
            for (CipherAlgorithm algorithm : CipherAlgorithm.values()) {
                for (CipherMode mode : CipherMode.values()) {
                    Encryptor encryptor = new PasswordBasedEncryptor(algorithm, mode, "clave de prueba");
                    for (int length : CONTENT_LENGTHS) {
                        roundTrip(steg, algorithm, mode, encryptor, length);
                    }
                    combinations++;
                }
            }
        }
        if (combinations != 48) {
            throw new AssertionError("se esperaban 48 combinaciones y hubo " + combinations);
        }
        System.out.println("OK: matriz de 48 combinaciones con longitudes alrededor de 4, 8 y 16 bytes");
    }

    private static void roundTrip(StegAlgorithm steg, CipherAlgorithm algorithm, CipherMode mode,
            Encryptor encryptor, int length) {
        String name = steg + " " + algorithm.cliName() + " " + mode.cliName() + " con " + length + " bytes";
        byte[] content = new byte[length];
        for (int index = 0; index < length; index++) {
            content[index] = (byte) (index * 53 + 7);
        }
        byte[] plain = PayloadFormat.pack(new HiddenFile(content, EXTENSION));
        byte[] encrypted = encryptor.encrypt(plain);
        int block = algorithm.blockBytes();
        int expected = mode.padsToBlock() ? (plain.length / block + 1) * block : plain.length;
        require(encrypted.length == expected, "longitud cifrada de " + name);
        byte[] payload = PayloadFormat.withSizePrefix(encrypted);
        require(payload.length == Integer.BYTES + encrypted.length, "prefijo exterior de " + name);

        StegMethod method = steg.method();
        byte[] body = new byte[BODY_BYTES];
        for (int index = 0; index < body.length; index++) {
            body[index] = (byte) (index * 31 + 3);
        }
        method.embed(body, payload);
        byte[] recovered = PayloadFormat.encryptedBlock(method.extractAll(body));
        require(Arrays.equals(encrypted, recovered), "ciphertext recuperado de " + name);
        HiddenFile file = PayloadFormat.unpack(encryptor.decrypt(recovered));
        require(Arrays.equals(content, file.content()) && EXTENSION.equals(file.extension()), "archivo de " + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
