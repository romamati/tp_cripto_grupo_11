package stegobmp.crypto;

import stegobmp.StegoException;

public final class PasswordBasedEncryptor implements Encryptor {

    private final CipherAlgorithm algorithm;
    private final CipherMode mode;
    private final String password;

    public PasswordBasedEncryptor(CipherAlgorithm algorithm, CipherMode mode, String password) {
        this.algorithm = algorithm;
        this.mode = mode;
        this.password = password;
    }

    @Override
    public byte[] encrypt(byte[] plain) {
        throw notImplemented();
    }

    @Override
    public byte[] decrypt(byte[] encrypted) {
        throw notImplemented();
    }

    private StegoException notImplemented() {
        return new StegoException("cifrado " + algorithm.cliName() + " " + mode.cliName()
                + ": no implementado todavía.");
    }
}
