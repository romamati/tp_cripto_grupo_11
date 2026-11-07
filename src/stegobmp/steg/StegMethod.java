package stegobmp.steg;

public interface StegMethod {

    int capacity(int bodyLength);

    void embed(byte[] body, byte[] data);

    byte[] extractAll(byte[] body);
}
