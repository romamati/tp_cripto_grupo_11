package stegobmp.crypto;

import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import stegobmp.StegoException;

// Clave e IV se derivan con PBKDF2-HMAC-SHA256, salt de ocho bytes cero y 10000 iteraciones, como en la
// consigna. La salida se separa en forma contigua: primero la clave y a continuación el IV.
// Los parámetros fijos forman parte de la interoperabilidad: no se agrega salt aleatorio ni encabezado.
public final class PasswordBasedEncryptor implements Encryptor {

    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 8;
    private static final int ITERATIONS = 10_000;

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
        try {
            return newCipher(Cipher.ENCRYPT_MODE).doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw unavailable(e);
        }
    }

    @Override
    public byte[] decrypt(byte[] encrypted) {
        // Con padding siempre hay al menos un bloque; la JCE aceptaría en silencio un ciphertext vacío.
        if (mode.padsToBlock() && encrypted.length == 0) {
            throw notBlockMultiple(encrypted);
        }
        Cipher cipher = newCipher(Cipher.DECRYPT_MODE);
        try {
            return cipher.doFinal(encrypted);
        } catch (IllegalBlockSizeException e) {
            throw notBlockMultiple(encrypted);
        } catch (BadPaddingException e) {
            throw new DecryptionException("el padding del último bloque no es válido");
        }
    }

    String transformation() {
        return algorithm.jceName() + "/" + mode.jceName() + "/" + mode.padding();
    }

    static KeyMaterial derive(CipherAlgorithm algorithm, CipherMode mode, String password) {
        int ivBytes = mode.usesIv() ? algorithm.blockBytes() : 0;
        byte[] derived = pbkdf2(password, algorithm.keyBytes() + ivBytes);
        try {
            return new KeyMaterial(
                    Arrays.copyOfRange(derived, 0, algorithm.keyBytes()),
                    Arrays.copyOfRange(derived, algorithm.keyBytes(), derived.length));
        } finally {
            Arrays.fill(derived, (byte) 0);
        }
    }

    private Cipher newCipher(int operation) {
        KeyMaterial material = derive(algorithm, mode, password);
        try {
            Cipher cipher = Cipher.getInstance(transformation());
            SecretKeySpec key = new SecretKeySpec(material.key(), algorithm.jceName());
            if (mode.usesIv()) {
                AlgorithmParameterSpec iv = new IvParameterSpec(material.iv());
                cipher.init(operation, key, iv);
            } else {
                cipher.init(operation, key);
            }
            return cipher;
        } catch (GeneralSecurityException e) {
            throw unavailable(e);
        } finally {
            material.clear();
        }
    }

    private static byte[] pbkdf2(String password, int outputBytes) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), new byte[SALT_BYTES], ITERATIONS,
                outputBytes * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new StegoException("no se pudo derivar la clave con " + KEY_DERIVATION + ": " + e.getMessage());
        } finally {
            spec.clearPassword();
        }
    }

    private DecryptionException notBlockMultiple(byte[] encrypted) {
        return new DecryptionException("el bloque cifrado mide " + encrypted.length
                + " bytes y en modo " + mode.cliName() + " debe ser un múltiplo no nulo de "
                + algorithm.blockBytes() + " bytes");
    }

    private StegoException unavailable(GeneralSecurityException e) {
        return new StegoException("no se pudo usar el cifrado " + algorithm.cliName() + " " + mode.cliName()
                + " (" + transformation() + ") en este entorno Java: " + e.getMessage());
    }

    record KeyMaterial(byte[] key, byte[] iv) {

        void clear() {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }
}
