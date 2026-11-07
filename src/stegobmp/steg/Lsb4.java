package stegobmp.steg;

public final class Lsb4 extends LsbReplacement {

    private static final int BITS_PER_CARRIER_BYTE = 4;

    public Lsb4() {
        super(BITS_PER_CARRIER_BYTE);
    }
}
