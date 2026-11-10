package stegobmp.steg;

import stegobmp.StegoException;

public final class Lsbi implements StegMethod {

    private static final int PATTERN_COUNT = 4;
    private static final int CHANNELS_PER_PIXEL = 3;
    private static final int RED_CHANNEL = 2;
    private static final int LSB_MASK = 1;
    private static final int PATTERN_MASK = PATTERN_COUNT - 1;

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
        byte[] data = new byte[capacity(body.length)];
        if (data.length == 0) {
            return data;
        }
        int[] inversions = new int[PATTERN_COUNT];
        for (int pattern = 0; pattern < PATTERN_COUNT; pattern++) {
            inversions[pattern] = body[pattern] & LSB_MASK;
        }
        int carrierIndex = PATTERN_COUNT;
        for (int dataIndex = 0; dataIndex < data.length; dataIndex++) {
            int value = 0;
            for (int bit = 0; bit < Byte.SIZE; bit++) {
                if (carrierIndex % CHANNELS_PER_PIXEL == RED_CHANNEL) {
                    carrierIndex++;
                }
                int carrierByte = body[carrierIndex];
                int pattern = (carrierByte >> 1) & PATTERN_MASK;
                int messageBit = (carrierByte & LSB_MASK) ^ inversions[pattern];
                value = (value << 1) | messageBit;
                carrierIndex++;
            }
            data[dataIndex] = (byte) value;
        }
        return data;
    }

    private static StegoException notImplemented() {
        return new StegoException("LSBI: no implementado todavía.");
    }
}
