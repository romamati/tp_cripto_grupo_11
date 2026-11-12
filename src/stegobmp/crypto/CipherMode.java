package stegobmp.crypto;

// CFB usa realimentación de 8 bits (CFB8), como los ejemplos de la cátedra; OFB realimenta el bloque completo.
// Los modos de flujo no necesitan padding y conservan la longitud del texto plano.
public enum CipherMode {
    ECB("ecb", "ECB", "PKCS5Padding", false),
    CFB("cfb", "CFB8", "NoPadding", true),
    OFB("ofb", "OFB", "NoPadding", true),
    CBC("cbc", "CBC", "PKCS5Padding", true);

    private final String cliName;
    private final String jceName;
    private final String padding;
    private final boolean usesIv;

    CipherMode(String cliName, String jceName, String padding, boolean usesIv) {
        this.cliName = cliName;
        this.jceName = jceName;
        this.padding = padding;
        this.usesIv = usesIv;
    }

    public String cliName() {
        return cliName;
    }

    public String jceName() {
        return jceName;
    }

    public String padding() {
        return padding;
    }

    public boolean usesIv() {
        return usesIv;
    }

    public boolean padsToBlock() {
        return !padding.equals("NoPadding");
    }
}
