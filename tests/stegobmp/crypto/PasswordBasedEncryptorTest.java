package stegobmp.crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import stegobmp.bmp.BmpFile;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.PayloadFormat;
import stegobmp.steg.StegAlgorithm;

public final class PasswordBasedEncryptorTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final String PASSWORD = "margarita";
    // Salida de 48 bytes de PBKDF2-HMAC-SHA256("margarita", salt de 8 bytes cero, 10000 iteraciones).
    // Verificada en forma independiente con:
    // openssl kdf -keylen 48 -kdfopt digest:SHA256 -kdfopt pass:margarita
    //     -kdfopt hexsalt:0000000000000000 -kdfopt iter:10000 PBKDF2
    private static final String PBKDF2_48 = "03db0a157acfe8de523760aa731d8122b25f8d99f3173ec0"
            + "b52849f459a4c20d212420edc583a686a94d19a3497363a2";
    private static final String PNG_SHA256 = "b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d";
    private static final int PNG_BYTES = 44_886;
    private static final byte[] UNALIGNED = "Grupo 11: stegobmp TP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ALIGNED = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    // Generados con OpenSSL 3, independiente de la JCE, con la clave y el IV contiguos de PBKDF2_48:
    // printf %s "<texto>" | openssl enc -<cifrado> -K <clave> [-iv <iv>] | od -An -tx1
    // donde <cifrado> es aes-128-ecb, aes-128-cbc, aes-128-cfb8, aes-128-ofb, ..., des-ede3, des-ede3-cbc,
    // des-ede3-cfb8 y des-ede3-ofb. ECB y CBC usan el padding PKCS#7 por defecto de OpenSSL.
    private static final List<Vector> OPENSSL_VECTORS = List.of(
            new Vector(CipherAlgorithm.AES128, CipherMode.ECB,
                    "58afda7f3b1baec7b6b50f5e66afa67d05d2e3a10c31a8364bfb7b743d323070",
                    "a472f9b2b3850991b8d67daa43930a6272492f17d2627b7342abbcc2b02e74bc"),
            new Vector(CipherAlgorithm.AES128, CipherMode.CBC,
                    "f89d8e3b2730de552a5b7073db77f0e3c2e5cc2b1bdbc42510258f4572d1509e",
                    "8895cfd459525b430b6a68c6b669dc8690ec0803a190b360d3aafb0dcb850027"),
            new Vector(CipherAlgorithm.AES128, CipherMode.CFB,
                    "711afc99b6a96fe78f698fa4d3ff78ea351d65ea99", "067e54ce8bf7ce9d06456b41b6fe99b4"),
            new Vector(CipherAlgorithm.AES128, CipherMode.OFB,
                    "714d7686584c9f98c3a6ec3d1f9f2598cf0b83af17", "060e31c50359989ec1bffe2b199c2f9c"),
            new Vector(CipherAlgorithm.AES192, CipherMode.ECB,
                    "3bbd3b0563f2e7a89b6c9d537e7599838732f6cfb58a31da85eaee9568a1aa20",
                    "bb5efc63003b1f3bc9c79bf199fed87647e2a4d029c55991b52985e5110795af"),
            new Vector(CipherAlgorithm.AES192, CipherMode.CBC,
                    "79e94e9dcf0f3a2acb4448608186a0ddf4a39d7a15a49f98d17e40a42e66d69b",
                    "1fbc635566a91ad14ff1766b33f0c795919eb23ef645253acd4fbdb781ba6fcb"),
            new Vector(CipherAlgorithm.AES192, CipherMode.CFB,
                    "0dd2a0a907ab419b2e0ae8e6c93fa60558787a1a02", "7ac6f93f0550bab2e56c4462f2730fd8"),
            new Vector(CipherAlgorithm.AES192, CipherMode.OFB,
                    "0d90159186925777ac0bb3e6d3b74c2777666093bc", "7ad352d2dd875071ae12a1f0d5b44623"),
            new Vector(CipherAlgorithm.AES256, CipherMode.ECB,
                    "24a5a79875c16e5a48c753512a7689307b7a2e35d231bb5a8179ee708c0788a3",
                    "8b17261528c9bced0223e690910b608b62105c1140b8738203725c8b16f6b550"),
            new Vector(CipherAlgorithm.AES256, CipherMode.CBC,
                    "3287e8f8f12b97e8186f58bd8c8b2dbfffeca4287332aefa611b963004573ec4",
                    "b7eb1b3d1487695457584e7ffb7eb2df357ffbc34f39c62f639aa619c3e9c291"),
            new Vector(CipherAlgorithm.AES256, CipherMode.CFB,
                    "2fbaf5044f0dea89bc97386c9900636927b1cd3545", "587c7b43e8dd47ab63982d893681fcb1"),
            new Vector(CipherAlgorithm.AES256, CipherMode.OFB,
                    "2feb9cda2ffbedfa295e537a2bdd82183ac68f30fe", "58a8db9974eeeafc2b47416c2dde881c"),
            new Vector(CipherAlgorithm.TRIPLE_DES, CipherMode.ECB,
                    "2cb2353b582e5f84cba3c836149fc6f86cf0c73bf4ee401a",
                    "f1482bb9270072dcdf05843aabd693dbb26ecb645d9141c4"),
            new Vector(CipherAlgorithm.TRIPLE_DES, CipherMode.CBC,
                    "a61fef2c90022d84c16808c912ebf15a2295b2358a0d0baa",
                    "a4a8bc81b5c56b11af75d623ba71271d2080d37648a486c0"),
            new Vector(CipherAlgorithm.TRIPLE_DES, CipherMode.CFB,
                    "6ce4ac4e55869411408a47cd3b563e226372fb4d1d", "1bb46c3c7326b06aa007acab7dfba73c"),
            new Vector(CipherAlgorithm.TRIPLE_DES, CipherMode.OFB,
                    "6c3c2c1334c7b895b113cdb64e464aa9b03cea8662", "1b7f6b506fd2bf93b30adfa0484540ad"));

    private PasswordBasedEncryptorTest() {
    }

    public static void main(String[] args) throws Exception {
        keyDerivation();
        System.out.println("OK: PBKDF2 con clave e IV contiguos para AES-128/192/256 y 3DES; ECB sin IV");
        transformations();
        System.out.println("OK: transformaciones JCE explícitas, CFB8 y OFB sin padding");
        openSslVectors();
        System.out.println("OK: 16 combinaciones coinciden con OpenSSL en textos alineados y no alineados");
        ciphertextLengths();
        System.out.println("OK: longitudes cifradas de 0 a 40 bytes, padding PKCS#5 completo en ECB/CBC");
        invalidCiphertexts();
        System.out.println("OK: ciphertext truncado, padding inválido y password incorrecta");
        officialExamples();
        System.out.println("OK: ejemplos cifrados de la cátedra, PNG esperado y recifrado idéntico");
    }

    private static void keyDerivation() {
        byte[] full = HEX.parseHex(PBKDF2_48);
        expectMaterial(CipherAlgorithm.AES128, CipherMode.CBC, full, 16, "b25f8d99f3173ec0b52849f459a4c20d");
        expectMaterial(CipherAlgorithm.AES192, CipherMode.CBC, full, 24, "b52849f459a4c20d212420edc583a686");
        expectMaterial(CipherAlgorithm.AES256, CipherMode.CBC, full, 32, "212420edc583a686a94d19a3497363a2");
        expectMaterial(CipherAlgorithm.TRIPLE_DES, CipherMode.CBC, full, 24, "b52849f459a4c20d");
        for (CipherAlgorithm algorithm : CipherAlgorithm.values()) {
            for (CipherMode mode : CipherMode.values()) {
                PasswordBasedEncryptor.KeyMaterial material = PasswordBasedEncryptor.derive(algorithm, mode, PASSWORD);
                require(Arrays.equals(Arrays.copyOf(full, algorithm.keyBytes()), material.key()),
                        "la clave no depende del modo: " + algorithm + " " + mode);
                int expectedIv = mode == CipherMode.ECB ? 0 : algorithm.blockBytes();
                require(material.iv().length == expectedIv, "longitud de IV para " + algorithm + " " + mode);
            }
        }
        PasswordBasedEncryptor.KeyMaterial other =
                PasswordBasedEncryptor.derive(CipherAlgorithm.AES128, CipherMode.CBC, "Margarita");
        require(!Arrays.equals(other.key(), Arrays.copyOf(full, 16)), "la password distingue mayúsculas");
    }

    private static void expectMaterial(CipherAlgorithm algorithm, CipherMode mode, byte[] full, int keyBytes,
            String iv) {
        PasswordBasedEncryptor.KeyMaterial material = PasswordBasedEncryptor.derive(algorithm, mode, PASSWORD);
        require(Arrays.equals(Arrays.copyOf(full, keyBytes), material.key()), "clave derivada de " + algorithm);
        require(HEX.formatHex(material.iv()).equals(iv), "IV derivado de " + algorithm + ": "
                + HEX.formatHex(material.iv()));
    }

    private static void transformations() {
        require(encryptor(CipherAlgorithm.AES128, CipherMode.ECB).transformation().equals("AES/ECB/PKCS5Padding"),
                "AES ECB");
        require(encryptor(CipherAlgorithm.AES256, CipherMode.CBC).transformation().equals("AES/CBC/PKCS5Padding"),
                "AES CBC");
        require(encryptor(CipherAlgorithm.AES192, CipherMode.CFB).transformation().equals("AES/CFB8/NoPadding"),
                "AES CFB8");
        require(encryptor(CipherAlgorithm.AES128, CipherMode.OFB).transformation().equals("AES/OFB/NoPadding"),
                "AES OFB");
        require(encryptor(CipherAlgorithm.TRIPLE_DES, CipherMode.ECB).transformation()
                .equals("DESede/ECB/PKCS5Padding"), "3DES ECB");
        require(encryptor(CipherAlgorithm.TRIPLE_DES, CipherMode.CFB).transformation()
                .equals("DESede/CFB8/NoPadding"), "3DES CFB8");
        require(encryptor(CipherAlgorithm.TRIPLE_DES, CipherMode.OFB).transformation()
                .equals("DESede/OFB/NoPadding"), "3DES OFB");
    }

    private static void openSslVectors() {
        require(OPENSSL_VECTORS.size() == CipherAlgorithm.values().length * CipherMode.values().length,
                "debe haber un vector por combinación");
        for (Vector vector : OPENSSL_VECTORS) {
            PasswordBasedEncryptor encryptor = encryptor(vector.algorithm(), vector.mode());
            String name = vector.algorithm().cliName() + " " + vector.mode().cliName();
            checkVector(encryptor, UNALIGNED, vector.unaligned(), name + " no alineado");
            checkVector(encryptor, ALIGNED, vector.aligned(), name + " alineado");
        }
    }

    private static void checkVector(Encryptor encryptor, byte[] plain, String expectedHex, String name) {
        byte[] expected = HEX.parseHex(expectedHex);
        byte[] actual = encryptor.encrypt(plain);
        require(Arrays.equals(expected, actual), "cifrado distinto de OpenSSL en " + name + ": "
                + HEX.formatHex(actual));
        require(Arrays.equals(plain, encryptor.decrypt(expected)), "descifrado del vector OpenSSL en " + name);
    }

    private static void ciphertextLengths() {
        for (CipherAlgorithm algorithm : CipherAlgorithm.values()) {
            for (CipherMode mode : CipherMode.values()) {
                PasswordBasedEncryptor encryptor = encryptor(algorithm, mode);
                for (int length = 0; length <= 40; length++) {
                    byte[] plain = new byte[length];
                    for (int index = 0; index < length; index++) {
                        plain[index] = (byte) (index * 29 + length);
                    }
                    byte[] encrypted = encryptor.encrypt(plain);
                    int block = algorithm.blockBytes();
                    int expected = mode.padsToBlock() ? (length / block + 1) * block : length;
                    require(encrypted.length == expected, "longitud cifrada " + algorithm + " " + mode + " de "
                            + length + " bytes: " + encrypted.length);
                    require(Arrays.equals(plain, encryptor.decrypt(encrypted)),
                            "ida y vuelta " + algorithm + " " + mode + " de " + length + " bytes");
                }
            }
        }
    }

    private static void invalidCiphertexts() {
        for (CipherAlgorithm algorithm : CipherAlgorithm.values()) {
            for (CipherMode mode : List.of(CipherMode.ECB, CipherMode.CBC)) {
                PasswordBasedEncryptor encryptor = encryptor(algorithm, mode);
                byte[] encrypted = encryptor.encrypt(UNALIGNED);
                expectDecryptionError(encryptor, Arrays.copyOf(encrypted, encrypted.length - 1),
                        "múltiplo no nulo", "truncado " + algorithm + " " + mode);
                expectDecryptionError(encryptor, new byte[0], "múltiplo no nulo", "vacío " + algorithm + " " + mode);
                // Con ALIGNED el último bloque es solo padding: alterar su último byte lo vuelve inválido.
                byte[] tampered = withInvalidPadding(encryptor, encryptor.encrypt(ALIGNED), algorithm.blockBytes(), mode);
                expectDecryptionError(encryptor, tampered, "padding", "padding inválido " + algorithm + " " + mode);
            }
        }
        Encryptor right = encryptor(CipherAlgorithm.AES128, CipherMode.CBC);
        Encryptor wrong = new PasswordBasedEncryptor(CipherAlgorithm.AES128, CipherMode.CBC, "Margarita");
        expectDecryptionError(wrong, right.encrypt(UNALIGNED), "padding", "password incorrecta en CBC");
    }

    // Construye un último bloque que descifra a ...0x00, que nunca es un padding PKCS#5 válido.
    private static byte[] withInvalidPadding(Encryptor encryptor, byte[] encrypted, int block, CipherMode mode) {
        byte[] result = encrypted.clone();
        if (mode == CipherMode.CBC) {
            // En CBC, alterar el penúltimo bloque modifica el mismo byte del último bloque descifrado.
            result[result.length - block - 1] ^= (byte) block;
            return result;
        }
        // En ECB se reemplaza el último bloque por el cifrado de un bloque de ceros.
        byte[] zeros = encryptor.encrypt(new byte[block]);
        System.arraycopy(zeros, 0, result, result.length - block, block);
        return result;
    }

    private static void expectDecryptionError(Encryptor encryptor, byte[] encrypted, String detail, String name) {
        try {
            encryptor.decrypt(encrypted);
        } catch (DecryptionException e) {
            require(e.getMessage().contains(detail), "detalle del error en " + name + ": " + e.getMessage());
            return;
        }
        throw new AssertionError("debía fallar el descifrado: " + name);
    }

    private static void officialExamples() throws Exception {
        officialExample("ladoLSB1aes128cbc.bmp", StegAlgorithm.LSB1, CipherAlgorithm.AES128, CipherMode.CBC, 44_896);
        officialExample("ladoLSBIaes256ofb.bmp", StegAlgorithm.LSBI, CipherAlgorithm.AES256, CipherMode.OFB, 44_895);
        officialExample("ladoLSBIdescfb.bmp", StegAlgorithm.LSBI, CipherAlgorithm.TRIPLE_DES, CipherMode.CFB, 44_895);
    }

    private static void officialExample(String name, StegAlgorithm steg, CipherAlgorithm algorithm, CipherMode mode,
            int ciphertextBytes) throws Exception {
        byte[] body = BmpFile.read(Path.of("materiales/ejemplo", name)).body();
        byte[] encrypted = PayloadFormat.encryptedBlock(steg.method().extractAll(body));
        require(encrypted.length == ciphertextBytes, "longitud del ciphertext de " + name + ": " + encrypted.length);
        Encryptor encryptor = encryptor(algorithm, mode);
        byte[] plain = encryptor.decrypt(encrypted);
        HiddenFile file = PayloadFormat.unpack(plain);
        require(file.extension().equals(".png"), "extensión de " + name);
        require(file.content().length == PNG_BYTES, "tamaño del PNG de " + name);
        String hash = HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(file.content()));
        require(hash.equals(PNG_SHA256), "SHA-256 del PNG de " + name + ": " + hash);
        require(Arrays.equals(encrypted, encryptor.encrypt(PayloadFormat.pack(file))),
                "el recifrado debe coincidir con el ciphertext de " + name);
    }

    private static PasswordBasedEncryptor encryptor(CipherAlgorithm algorithm, CipherMode mode) {
        return new PasswordBasedEncryptor(algorithm, mode, PASSWORD);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Vector(CipherAlgorithm algorithm, CipherMode mode, String unaligned, String aligned) {
    }
}
