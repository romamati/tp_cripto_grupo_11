package stegobmp.steg;

import java.nio.file.Path;
import stegobmp.bmp.BmpFile;

public final class LsbiCapacityTest {

    private static final Lsbi LSBI = new Lsbi();
    private static final int MAX_TRAVERSAL_LENGTH = 100_000;

    private LsbiCapacityTest() {
    }

    public static void main(String[] args) {
        smallBodiesAndByteBoundaries();
        System.out.println("OK: cuerpos pequeños y límites de bytes completos");
        capacityMatchesCarrierTraversal();
        System.out.println("OK: capacidad contra recorrido para longitudes de 0 a " + MAX_TRAVERSAL_LENGTH);
        largeBodiesWithoutOverflow();
        System.out.println("OK: longitudes grandes sin desbordamiento");
        officialCarrier();
        System.out.println("OK: lado.bmp admite 76.799 bytes de payload LSBI");
    }

    private static void smallBodiesAndByteBoundaries() {
        for (int bodyLength = 0; bodyLength < 16; bodyLength++) {
            assertCapacity(0, bodyLength);
        }
        assertCapacity(1, 16);
        assertCapacity(1, 17);
        assertCapacity(1, 18);
        assertCapacity(1, 26);
        assertCapacity(1, 27);
        assertCapacity(2, 28);
        assertCapacity(2, 29);
        assertCapacity(2, 30);
        assertCapacity(2, 39);
        assertCapacity(3, 40);
    }

    private static void capacityMatchesCarrierTraversal() {
        int usableBits = 0;
        for (int bodyLength = 0; bodyLength <= MAX_TRAVERSAL_LENGTH; bodyLength++) {
            int lastIndex = bodyLength - 1;
            if (lastIndex >= 4 && lastIndex % 3 != 2) {
                usableBits++;
            }
            assertCapacity(usableBits / Byte.SIZE, bodyLength);
        }
    }

    private static void largeBodiesWithoutOverflow() {
        assertCapacity(178_956_969, 2_147_483_643);
        assertCapacity(178_956_970, 2_147_483_644);
        assertCapacity(178_956_970, Integer.MAX_VALUE);
    }

    private static void officialCarrier() {
        BmpFile carrier = BmpFile.read(Path.of("materiales/ejemplo/lado.bmp"));
        assertCapacity(76_799, carrier.body().length);
    }

    private static void assertCapacity(int expected, int bodyLength) {
        int actual = LSBI.capacity(bodyLength);
        if (actual != expected) {
            throw new AssertionError("capacity(" + bodyLength + "): se esperaba " + expected
                    + " y se obtuvo " + actual);
        }
    }
}
