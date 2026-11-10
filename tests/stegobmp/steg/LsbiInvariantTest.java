package stegobmp.steg;

import java.util.Arrays;
import java.util.Random;

public final class LsbiInvariantTest {

    private static final Lsbi LSBI = new Lsbi();
    private static final int[] FIRST_BYTE_POSITIONS = {4, 6, 7, 9, 10, 12, 13, 15};
    private static final int MAX_BOUNDARY_BODY_LENGTH = 512;

    private LsbiInvariantTest() {
    }

    public static void main(String[] args) {
        allIndicatorCombinations();
        System.out.println("OK: las 16 combinaciones de indicadores con resultado esperado conocido");
        tiesDoNotInvert();
        System.out.println("OK: los empates no invierten ningún patrón");
        absentPatternsAndSingleOccurrences();
        System.out.println("OK: patrones ausentes y patrones con una sola ocurrencia");
        preservesCarrierAndPayload();
        System.out.println("OK: bits superiores, rojos, cola sin usar y bytes 0x00..0xFF");
        emptyPayloadClearsOnlyFlags();
        System.out.println("OK: payload vacío modifica únicamente los indicadores");
        exactCapacityAndOverflow();
        System.out.println("OK: capacidad exacta y rechazo sin modificaciones para cuerpos de 0 a "
                + MAX_BOUNDARY_BODY_LENGTH);
    }

    private static void allIndicatorCombinations() {
        for (int mask = 0; mask < 16; mask++) {
            byte[] body = new byte[64];
            Arrays.fill(body, (byte) 0xFF);
            for (int pattern = 0; pattern < 4; pattern++) {
                int flag = (mask >> (3 - pattern)) & 1;
                body[pattern] = (byte) (0xF0 | (flag ^ 1));
            }
            for (int bit = 0; bit < FIRST_BYTE_POSITIONS.length; bit++) {
                int pattern = bit % 4;
                int flag = (mask >> (3 - pattern)) & 1;
                body[FIRST_BYTE_POSITIONS[bit]] = (byte) (0xF8 | (pattern << 1) | flag);
            }
            byte[] expected = body.clone();
            for (int pattern = 0; pattern < 4; pattern++) {
                expected[pattern] = (byte) (0xF0 | ((mask >> (3 - pattern)) & 1));
            }
            assertRoundTripAndPreservation(body, new byte[] {0});
            assertBytes(expected, body, "combinación de indicadores " + mask);
        }
    }

    private static void tiesDoNotInvert() {
        byte[] body = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xF9, (byte) 0xFF, (byte) 0xFB, (byte) 0xFD,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xF8, (byte) 0xFF,
            (byte) 0xFA, (byte) 0xFC, (byte) 0xFF, (byte) 0xFE
        };
        byte[] expected = {
            (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE,
            (byte) 0xF8, (byte) 0xFF, (byte) 0xFA, (byte) 0xFC,
            (byte) 0xFF, (byte) 0xFE, (byte) 0xF8, (byte) 0xFF,
            (byte) 0xFA, (byte) 0xFC, (byte) 0xFF, (byte) 0xFE
        };
        assertRoundTripAndPreservation(body, new byte[] {0});
        assertBytes(expected, body, "un cambio y una coincidencia por patrón");
    }

    private static void absentPatternsAndSingleOccurrences() {
        for (int presentPattern = 0; presentPattern < 4; presentPattern++) {
            byte[] body = new byte[16];
            Arrays.fill(body, (byte) (0x80 | (presentPattern << 1)));
            Arrays.fill(body, 0, 4, (byte) 0xFF);
            byte[] expected = body.clone();
            Arrays.fill(expected, 0, 4, (byte) 0xFE);
            expected[presentPattern] = (byte) 0xFF;
            assertRoundTripAndPreservation(body, new byte[] {(byte) 0xFF});
            assertBytes(expected, body, "único patrón presente " + presentPattern);
        }
        byte[] body = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0x81, (byte) 0xFF, (byte) 0x82, (byte) 0x84,
            (byte) 0xFF, (byte) 0x84, (byte) 0x84, (byte) 0xFF,
            (byte) 0x86, (byte) 0x86, (byte) 0xFF, (byte) 0x86
        };
        byte[] expected = body.clone();
        expected[1] = (byte) 0xFE;
        expected[2] = (byte) 0xFE;
        expected[3] = (byte) 0xFE;
        assertRoundTripAndPreservation(body, new byte[] {0});
        assertBytes(expected, body, "ocurrencia única: un patrón cambia y otro coincide");
    }

    private static void preservesCarrierAndPayload() {
        byte[] body = new byte[8_192];
        for (int index = 0; index < body.length; index++) {
            body[index] = (byte) index;
        }
        byte[] data = new byte[256];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) index;
        }
        assertRoundTripAndPreservation(body, data);
    }

    private static void emptyPayloadClearsOnlyFlags() {
        byte[] body = new byte[64];
        Arrays.fill(body, (byte) 0xFF);
        byte[] expected = body.clone();
        Arrays.fill(expected, 0, 4, (byte) 0xFE);
        assertRoundTripAndPreservation(body, new byte[0]);
        assertBytes(expected, body, "indicadores sin patrones utilizados");
    }

    private static void exactCapacityAndOverflow() {
        Random random = new Random(0x11_2026L);
        for (int bodyLength = 0; bodyLength <= MAX_BOUNDARY_BODY_LENGTH; bodyLength++) {
            byte[] body = new byte[bodyLength];
            random.nextBytes(body);
            if (bodyLength < 4) {
                assertRejectedWithoutMutation(body, new byte[0]);
                continue;
            }
            int capacity = LSBI.capacity(bodyLength);
            byte[] data = new byte[capacity];
            random.nextBytes(data);
            assertRoundTripAndPreservation(body, data);
            byte[] tooLarge = new byte[capacity + 1];
            random.nextBytes(tooLarge);
            assertRejectedWithoutMutation(body, tooLarge);
        }
    }

    private static void assertRoundTripAndPreservation(byte[] body, byte[] data) {
        byte[] originalBody = body.clone();
        byte[] originalData = data.clone();
        LSBI.embed(body, data);
        assertBytes(originalData, data, "payload sin modificaciones");
        int remainingBits = data.length * Byte.SIZE;
        for (int index = 0; index < body.length; index++) {
            if ((body[index] & 0xFE) != (originalBody[index] & 0xFE)) {
                throw new AssertionError("se modificaron bits superiores en el índice " + index);
            }
            if (index < 4) {
                continue;
            }
            if (index % 3 != 2 && remainingBits > 0) {
                remainingBits--;
            } else if (body[index] != originalBody[index]) {
                throw new AssertionError("se modificó un byte sin usar en el índice " + index);
            }
        }
        if (remainingBits != 0) {
            throw new AssertionError("no se recorrieron todos los bits del payload");
        }
        byte[] embeddedBody = body.clone();
        byte[] recovered = LSBI.extractAll(body);
        if (recovered.length != LSBI.capacity(body.length) || recovered.length < data.length) {
            throw new AssertionError("longitud incorrecta de los bytes recuperables");
        }
        if (!Arrays.equals(data, 0, data.length, recovered, 0, data.length)) {
            throw new AssertionError("el payload recuperado no coincide");
        }
        assertBytes(embeddedBody, body, "extracción sin modificaciones del cuerpo");
    }

    private static void assertRejectedWithoutMutation(byte[] body, byte[] data) {
        byte[] originalBody = body.clone();
        byte[] originalData = data.clone();
        try {
            LSBI.embed(body, data);
        } catch (IllegalArgumentException e) {
            assertBytes(originalBody, body, "cuerpo rechazado sin modificaciones");
            assertBytes(originalData, data, "payload rechazado sin modificaciones");
            return;
        }
        throw new AssertionError("se aceptó un payload sin espacio suficiente");
    }

    private static void assertBytes(byte[] expected, byte[] actual, String description) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + ": los bytes no coinciden");
        }
    }
}
