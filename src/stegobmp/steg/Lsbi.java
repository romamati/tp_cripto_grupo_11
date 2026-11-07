package stegobmp.steg;

import stegobmp.StegoException;

public final class Lsbi implements StegMethod {

    @Override
    public int capacity(int bodyLength) {
        throw notImplemented();
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
