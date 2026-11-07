package stegobmp.crypto;

public record EncryptionSettings(CipherAlgorithm algorithm, CipherMode mode, String password) {
}
