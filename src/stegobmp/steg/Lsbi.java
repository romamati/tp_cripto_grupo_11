package stegobmp.steg;

import stegobmp.StegoException;

public final class Lsbi implements StegMethod {

    private static final int PATTERN_COUNT = 4;
    private static final int CHANNELS_PER_PIXEL = 3;

    @Override
    public int capacity(int bodyLength) {
        if (bodyLength < PATTERN_COUNT) {
            return 0;
        }
        int usableBytes = bodyLength - bodyLength / CHANNELS_PER_PIXEL;
        int reservedUsableBytes = PATTERN_COUNT - PATTERN_COUNT / CHANNELS_PER_PIXEL;
        return (usableBytes - reservedUsableBytes) / Byte.SIZE;
    }

    @Override
    public void embed(byte[] body, byte[] data) {
        throw notImplemented();
    }

    @Override
    public byte[] extractAll(byte[] body) {
        throw notImplemented();
    }

    private static StegoException notImplemented() {
        return new StegoException("LSBI: no implementado todavía.");
    }
}
