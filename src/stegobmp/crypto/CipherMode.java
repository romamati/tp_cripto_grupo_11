package stegobmp.crypto;

public enum CipherMode {
    ECB("ecb"),
    CFB("cfb"),
    OFB("ofb"),
    CBC("cbc");

    private final String cliName;

    CipherMode(String cliName) {
        this.cliName = cliName;
    }

    public String cliName() {
        return cliName;
    }
}
