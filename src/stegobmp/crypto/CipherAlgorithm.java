package stegobmp.crypto;

// Tamaños en bytes. 3DES usa tres claves de 8 bytes (DES-EDE3) y bloque de 64 bits.
public enum CipherAlgorithm {
    AES128("aes128", "AES", 16, 16),
    AES192("aes192", "AES", 24, 16),
    AES256("aes256", "AES", 32, 16),
    TRIPLE_DES("3des", "DESede", 24, 8);

    private final String cliName;
    private final String jceName;
    private final int keyBytes;
    private final int blockBytes;

    CipherAlgorithm(String cliName, String jceName, int keyBytes, int blockBytes) {
        this.cliName = cliName;
        this.jceName = jceName;
        this.keyBytes = keyBytes;
        this.blockBytes = blockBytes;
    }

    public String cliName() {
        return cliName;
    }

    public String jceName() {
        return jceName;
    }

    public int keyBytes() {
        return keyBytes;
    }

    public int blockBytes() {
        return blockBytes;
    }
}
