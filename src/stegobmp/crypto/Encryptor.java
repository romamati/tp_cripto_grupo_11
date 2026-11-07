package stegobmp.crypto;

public interface Encryptor {

    static Encryptor from(EncryptionSettings settings) {
        return new PasswordBasedEncryptor(settings.algorithm(), settings.mode(), settings.password());
    }

    byte[] encrypt(byte[] plain);

    byte[] decrypt(byte[] encrypted);
}
