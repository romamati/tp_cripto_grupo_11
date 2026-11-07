package stegobmp.crypto;

public enum CipherAlgorithm {
    AES128("aes128"),
    AES192("aes192"),
    AES256("aes256"),
    TRIPLE_DES("3des");

    private final String cliName;

    CipherAlgorithm(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }
}
