package stegobmp.steg;

public final class Lsb1 extends LsbReplacement {

    private static final int BITS_PER_CARRIER_BYTE = 1;

    public Lsb1() {
        super(BITS_PER_CARRIER_BYTE);
    }
}
