package stegobmp.steg;

public abstract class LsbReplacement implements StegMethod {

    private static final int BITS_PER_BYTE = Byte.SIZE;

    private final int bitsPerCarrierByte;
    private final int carrierBytesPerDataByte;
    private final int lowBitsMask;

    protected LsbReplacement(int bitsPerCarrierByte) {
        this.bitsPerCarrierByte = bitsPerCarrierByte;
        this.carrierBytesPerDataByte = BITS_PER_BYTE / bitsPerCarrierByte;
        this.lowBitsMask = (1 << bitsPerCarrierByte) - 1;
    }

    @Override
    public int capacity(int bodyLength) {
        return bodyLength / carrierBytesPerDataByte;
    }

    @Override
    public void embed(byte[] body, byte[] data) {
        if (data.length > capacity(body.length)) {
            throw new IllegalArgumentException("los datos no entran en el portador");
        }
        int carrierIndex = 0;
        for (byte dataByte : data) {
            for (int shift = BITS_PER_BYTE - bitsPerCarrierByte; shift >= 0; shift -= bitsPerCarrierByte) {
                int chunk = (dataByte >> shift) & lowBitsMask;
                body[carrierIndex] = (byte) ((body[carrierIndex] & ~lowBitsMask) | chunk);
                carrierIndex++;
            }
        }
    }

    @Override
    public byte[] extractAll(byte[] body) {
        byte[] data = new byte[capacity(body.length)];
        int carrierIndex = 0;
        for (int dataIndex = 0; dataIndex < data.length; dataIndex++) {
            int value = 0;
            for (int chunk = 0; chunk < carrierBytesPerDataByte; chunk++) {
                value = (value << bitsPerCarrierByte) | (body[carrierIndex] & lowBitsMask);
                carrierIndex++;
            }
            data[dataIndex] = (byte) value;
        }
        return data;
    }
}
