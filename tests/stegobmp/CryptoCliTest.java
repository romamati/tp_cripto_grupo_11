package stegobmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import stegobmp.bmp.BmpFile;
import stegobmp.payload.PayloadFormat;
import stegobmp.steg.StegMethod;
import stegobmp.steg.StegAlgorithm;

// Pruebas por línea de comandos del cifrado integrado: ejemplos oficiales, valores por defecto,
// archivos especiales, capacidad después del padding y errores sin salidas parciales.
public final class CryptoCliTest {

    private static final List<String> METHODS = List.of("LSB1", "LSB4", "LSBI");
    private static final List<List<String>> CIPHERS = List.of(
            List.of("-a", "aes128", "-m", "cfb"),
            List.of("-a", "aes192", "-m", "ecb"),
            List.of("-a", "aes256", "-m", "cbc"),
            List.of("-a", "3des", "-m", "ofb"));
    private static final String PASSWORD = "margarita";
    private static final String PNG_SHA256 = "b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d";
    private static final int PNG_BYTES = 44_886;
    private static final int BMP_HEADER_BYTES = 54;
    private static final int DIB_HEADER_BYTES = 40;
    private static final int CHANNELS_PER_PIXEL = 3;
    private static final int ROW_ALIGNMENT = 4;
    private static final int PROCESS_TIMEOUT_SECONDS = 20;

    private final Path launcher = Path.of("stegobmp").toAbsolutePath();
    private final Path workspace;
    private final Path examples;
    private final Path carrier;
    private final Path smallCarrier;
    private int passed;

    private CryptoCliTest(Path workspace) throws IOException {
        this.workspace = workspace;
        this.examples = Files.createDirectory(workspace.resolve("ejemplos"));
        Path source = Path.of("materiales/ejemplo").toAbsolutePath();
        for (String file : List.of("lado.bmp", "ladoLSB1.bmp", "ladoLSB1aes128cbc.bmp", "ladoLSBIaes256ofb.bmp",
                "ladoLSBIdescfb.bmp")) {
            Files.copy(source.resolve(file), examples.resolve(file));
        }
        this.carrier = examples.resolve("lado.bmp");
        // Ancho 1: 3 bytes de píxel y 1 de padding por fila; 128 filas dan un cuerpo de 512 bytes.
        this.smallCarrier = workspace.resolve("chico.bmp");
        Files.write(smallCarrier, bitmap(1, 128, 24, 0));
    }

    public static void main(String[] args) throws Exception {
        Path workspace = Files.createTempDirectory("stegobmp-crypto-cli-");
        try {
            CryptoCliTest suite = new CryptoCliTest(workspace);
            suite.officialEncryptedExamples();
            suite.cliDefaults();
            suite.specialFiles();
            suite.capacityAfterEncryption();
            suite.decryptionErrors();
            suite.malformedCiphertexts();
            suite.parameterErrors();
            suite.invalidFiles();
            System.out.println("Resultado: " + suite.passed + " casos por CLI con cifrado aprobados.");
        } finally {
            try (Stream<Path> paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private void officialEncryptedExamples() throws Exception {
        officialExample("ladoLSB1aes128cbc.bmp", "LSB1", "aes128", "cbc");
        officialExample("ladoLSBIaes256ofb.bmp", "LSBI", "aes256", "ofb");
        officialExample("ladoLSBIdescfb.bmp", "LSBI", "3des", "cfb");
    }

    private void officialExample(String name, String method, String algorithm, String mode) throws Exception {
        Path reference = examples.resolve(name);
        String prefix = "oficial-" + algorithm + mode;
        success("-extract", "-p", reference.toString(), "-out", workspace.resolve(prefix).toString(),
                "-steg", method, "-a", algorithm, "-m", mode, "-pass", PASSWORD);
        Path png = workspace.resolve(prefix + ".png");
        require(Files.size(png) == PNG_BYTES, "tamaño del PNG de " + name);
        require(PNG_SHA256.equals(sha256(png)), "SHA-256 del PNG de " + name);
        pass("extracción de " + name + ": .png, tamaño y SHA-256");
        Path generated = workspace.resolve("regenerado-" + name);
        success("-embed", "-in", png.toString(), "-p", carrier.toString(), "-out", generated.toString(),
                "-steg", method, "-a", algorithm, "-m", mode, "-pass", PASSWORD);
        sameFile(reference, generated);
        pass("regeneración idéntica de " + name + " con cmp");
    }

    private void cliDefaults() throws Exception {
        Path secret = workspace.resolve("mensaje.txt");
        Files.writeString(secret, "Mensaje para probar los valores por defecto del cifrado.\n");
        Path plain = embed(secret, carrier, "defecto-plano", "LSBI");

        Path onlyPassword = embed(secret, carrier, "defecto-pass", "LSBI", "-pass", PASSWORD);
        Path explicit = embed(secret, carrier, "defecto-explicito", "LSBI",
                "-a", "aes128", "-m", "cbc", "-pass", PASSWORD);
        sameFile(explicit, onlyPassword);
        require(Files.mismatch(plain, onlyPassword) != -1, "con -pass el resultado debe estar cifrado");
        extractAndCompare(onlyPassword, secret, "defecto-pass-extraido", ".txt", "LSBI",
                "-a", "aes128", "-m", "cbc", "-pass", PASSWORD);
        pass("solo -pass equivale a aes128 cbc");

        Path algorithmOnly = embed(secret, carrier, "defecto-a", "LSBI", "-a", "aes256", "-pass", PASSWORD);
        sameFile(embed(secret, carrier, "defecto-a-cbc", "LSBI", "-a", "aes256", "-m", "cbc", "-pass", PASSWORD),
                algorithmOnly);
        extractAndCompare(algorithmOnly, secret, "defecto-a-extraido", ".txt", "LSBI",
                "-m", "cbc", "-a", "aes256", "-pass", PASSWORD);
        pass("-a con -pass y sin -m usa cbc");

        Path modeOnly = embed(secret, carrier, "defecto-m", "LSBI", "-m", "ofb", "-pass", PASSWORD);
        sameFile(embed(secret, carrier, "defecto-m-aes", "LSBI", "-a", "aes128", "-m", "ofb", "-pass", PASSWORD),
                modeOnly);
        extractAndCompare(modeOnly, secret, "defecto-m-extraido", ".txt", "LSBI",
                "-pass", PASSWORD, "-a", "aes128", "-m", "ofb");
        pass("-m con -pass y sin -a usa aes128");

        ignoredWithoutPassword(secret, plain, "se ignoran -a y -m", "-a", "3des", "-m", "ecb");
        ignoredWithoutPassword(secret, plain, "se ignora -a", "-a", "aes192");
        ignoredWithoutPassword(secret, plain, "se ignora -m", "-m", "cfb");
    }

    private void ignoredWithoutPassword(Path secret, Path plain, String warning, String... options)
            throws Exception {
        String name = "sin-pass-" + String.join("", options).replace("-", "");
        Path output = workspace.resolve(name + ".bmp");
        Result result = stego(concat(List.of("-embed", "-in", secret.toString(), "-p", carrier.toString(),
                "-out", output.toString(), "-steg", "LSBI"), options));
        require(result.code() == 0, "sin -pass debe ocultar sin cifrar: " + result.stderr());
        require(result.stderr().contains("Aviso: " + warning), "aviso esperado \"" + warning + "\": "
                + result.stderr());
        sameFile(plain, output);
        Result extract = stego(concat(List.of("-extract", "-p", output.toString(),
                "-out", workspace.resolve(name + "-extraido").toString(), "-steg", "LSBI"), options));
        require(extract.code() == 0, "sin -pass debe extraer sin descifrar: " + extract.stderr());
        sameFile(secret, workspace.resolve(name + "-extraido.txt"));
        pass("sin -pass " + String.join(" ", options) + " se ignora con aviso y no se cifra");
    }

    private void specialFiles() throws Exception {
        Path empty = workspace.resolve("vacio.txt");
        Files.write(empty, new byte[0]);
        Path emptyBare = workspace.resolve("vacio_sin_extension");
        Files.write(emptyBare, new byte[0]);
        Path longExtension = workspace.resolve("datos.extensionmuylarga123");
        Files.writeString(longExtension, "extensión de 25 bytes contando el punto");
        Path bmpSecret = workspace.resolve("secreto.bmp");
        Files.write(bmpSecret, bitmap(5, 7, 24, 0));
        Path nulls = workspace.resolve("nulos.bin");
        byte[] data = new byte[300];
        for (int index = 0; index < data.length; index++) {
            data[index] = index % 3 == 0 ? 0 : (byte) index;
        }
        data[data.length - 1] = 0;
        Files.write(nulls, data);

        List<Special> files = List.of(
                new Special(empty, ".txt"),
                new Special(emptyBare, ""),
                new Special(longExtension, ".extensionmuylarga123"),
                new Special(bmpSecret, ".bmp"),
                new Special(nulls, ".bin"));
        int cipherIndex = 0;
        for (String method : METHODS) {
            for (Special file : files) {
                String base = "especial-" + method + "-" + file.path().getFileName().toString().replace('.', '_');
                Path generated = embed(file.path(), carrier, base, method);
                extractAndCompare(generated, file.path(), base + "-extraido", file.extension(), method);
                List<String> cipher = CIPHERS.get(cipherIndex++ % CIPHERS.size());
                List<String> options = new ArrayList<>(cipher);
                options.addAll(List.of("-pass", PASSWORD));
                String[] optionArray = options.toArray(String[]::new);
                Path encrypted = embed(file.path(), carrier, base + "-cifrado", method, optionArray);
                extractAndCompare(encrypted, file.path(), base + "-cifrado-extraido", file.extension(), method,
                        optionArray);
                pass(file.path().getFileName() + " con " + method + " sin cifrar y con " + String.join(" ", cipher));
            }
        }

        Path tiny = workspace.resolve("diminuto.bmp");
        Files.write(tiny, bitmap(1, 4, 24, 0));
        assertCapacityError(empty, tiny, "vacio-no-entra", "LSB1", 2, 9);
        pass("un archivo vacío sin cifrar también necesita lugar para tamaño y extensión");
    }

    private void capacityAfterEncryption() throws Exception {
        // LSBI en un cuerpo de 512 bytes admite 42. El bloque interior mide n + 9 (tamaño, datos y ".bin\0").
        // CBC con AES: 22 bytes -> 31 -> 32 cifrados -> 36 ocultos; 23 bytes -> 32 -> 48 por el bloque
        // de padding completo -> 52 ocultos.
        Path fits = secretOfLength(22);
        Path generated = embed(fits, smallCarrier, "cap-cbc-entra", "LSBI", "-a", "aes128", "-m", "cbc",
                "-pass", PASSWORD);
        extractAndCompare(generated, fits, "cap-cbc-entra-extraido", ".bin", "LSBI", "-a", "aes128", "-m", "cbc",
                "-pass", PASSWORD);
        assertCapacityError(secretOfLength(23), smallCarrier, "cap-cbc-excede", "LSBI", 42, 52,
                "-a", "aes128", "-m", "cbc", "-pass", PASSWORD);
        pass("capacidad con aes128 cbc: el padding de un bloque completo se cuenta antes de ocultar");

        // OFB conserva la longitud: 29 bytes -> 38 -> 42 ocultos (capacidad exacta); 30 bytes -> 43.
        Path exact = secretOfLength(29);
        Path exactOutput = embed(exact, smallCarrier, "cap-ofb-exacta", "LSBI", "-a", "aes128", "-m", "ofb",
                "-pass", PASSWORD);
        extractAndCompare(exactOutput, exact, "cap-ofb-exacta-extraido", ".bin", "LSBI", "-a", "aes128",
                "-m", "ofb", "-pass", PASSWORD);
        assertCapacityError(secretOfLength(30), smallCarrier, "cap-ofb-excede", "LSBI", 42, 43,
                "-a", "aes128", "-m", "ofb", "-pass", PASSWORD);
        pass("capacidad exacta con aes128 ofb y rechazo con un byte más");

        // 3DES tiene bloque de 8 bytes: 23 bytes -> 32 -> 40 cifrados -> 44 ocultos.
        assertCapacityError(secretOfLength(23), smallCarrier, "cap-3des-excede", "LSBI", 42, 44,
                "-a", "3des", "-m", "cbc", "-pass", PASSWORD);
        pass("capacidad con 3des cbc según su bloque de 8 bytes");

        byte[] sentinel = {9, 8, 7};
        Path existing = workspace.resolve("cap-existente.bmp");
        Files.write(existing, sentinel);
        Result result = stego("-embed", "-in", secretOfLength(23).toString(), "-p", smallCarrier.toString(),
                "-out", existing.toString(), "-steg", "LSBI", "-pass", PASSWORD);
        require(result.code() == 1, "el exceso de capacidad cifrado debe terminar con código 1");
        require(Arrays.equals(sentinel, Files.readAllBytes(existing)), "no debe sobrescribir la salida existente");
        pass("un error de capacidad con cifrado conserva una salida existente");
    }

    private void decryptionErrors() throws Exception {
        decryptionError("ladoLSB1aes128cbc.bmp", "LSB1", "padding", "-a", "aes128", "-m", "cbc", "-pass", "Margarita");
        decryptionError("ladoLSBIaes256ofb.bmp", "LSBI", "formato esperado", "-a", "aes256", "-m", "ofb",
                "-pass", "otra");
        decryptionError("ladoLSBIaes256ofb.bmp", "LSBI", "múltiplo", "-a", "aes256", "-m", "cbc", "-pass", PASSWORD);
        decryptionError("ladoLSBIaes256ofb.bmp", "LSBI", "formato esperado", "-a", "aes128", "-m", "ofb",
                "-pass", PASSWORD);
        decryptionError("ladoLSBIdescfb.bmp", "LSBI", "formato esperado", "-a", "3des", "-m", "ofb",
                "-pass", PASSWORD);
        decryptionError("ladoLSB1.bmp", "LSB1", "múltiplo", "-pass", PASSWORD);

        Path existing = workspace.resolve("descifrado-existente.png");
        byte[] sentinel = {1, 2, 3, 4};
        Files.write(existing, sentinel);
        Result result = stego("-extract", "-p", examples.resolve("ladoLSB1aes128cbc.bmp").toString(),
                "-out", workspace.resolve("descifrado-existente").toString(), "-steg", "LSB1",
                "-pass", "incorrecta");
        require(result.code() == 1, "password incorrecta debe terminar con código 1");
        require(Arrays.equals(sentinel, Files.readAllBytes(existing)), "no debe sobrescribir la salida existente");
        pass("un error de descifrado conserva una salida existente");
    }

    private void decryptionError(String name, String method, String detail, String... options) throws Exception {
        String prefix = "error-descifrado-" + passed;
        Result result = stego(concat(List.of("-extract", "-p", examples.resolve(name).toString(),
                "-out", workspace.resolve(prefix).toString(), "-steg", method), options));
        require(result.code() == 1, "debía fallar con código 1: " + name + " " + Arrays.toString(options));
        require(result.stderr().contains("no se pudo descifrar") && result.stderr().contains(detail),
                "mensaje de descifrado esperado (" + detail + "): " + result.stderr());
        require(!result.stderr().contains("Uso:"), "un error de descifrado no muestra la sintaxis");
        noOutput(prefix);
        pass("descifrado rechazado en " + name + " con " + String.join(" ", options) + ", sin salida");
    }

    private void malformedCiphertexts() throws Exception {
        StegMethod lsb1 = StegAlgorithm.LSB1.method();
        malformed("prefijo-largo", lsb1, ByteBuffer.allocate(24).putInt(1000).array(),
                "no se encontró información oculta", "-pass", PASSWORD);
        malformed("prefijo-corto", lsb1, ByteBuffer.allocate(24).putInt(3).array(),
                "no se encontró información oculta", "-pass", PASSWORD);
        malformed("no-multiplo", lsb1, ByteBuffer.allocate(24).putInt(20).array(),
                "múltiplo", "-pass", PASSWORD);

        // Ciphertext CFB real con su último byte truncado: falta el terminador de la extensión.
        Path secret = secretOfLength(10);
        Path generated = embed(secret, smallCarrier, "truncado-cfb", "LSB1", "-a", "aes128", "-m", "cfb",
                "-pass", PASSWORD);
        BmpFile bmp = BmpFile.read(generated);
        byte[] encrypted = PayloadFormat.encryptedBlock(lsb1.extractAll(bmp.body()));
        byte[] truncated = PayloadFormat.withSizePrefix(Arrays.copyOf(encrypted, encrypted.length - 1));
        malformed("truncado-cfb", lsb1, truncated, "terminada en", "-a", "aes128", "-m", "cfb", "-pass", PASSWORD);
        byte[] cbc = PayloadFormat.withSizePrefix(new byte[31]);
        malformed("truncado-cbc", lsb1, cbc, "múltiplo", "-a", "aes128", "-m", "cbc", "-pass", PASSWORD);
    }

    private void malformed(String name, StegMethod method, byte[] payload, String detail, String... options)
            throws Exception {
        Path generated = workspace.resolve("malformado-" + name + ".bmp");
        BmpFile bmp = BmpFile.read(smallCarrier);
        method.embed(bmp.body(), payload);
        bmp.write(generated);
        String prefix = "malformado-" + name + "-extraido";
        Result result = stego(concat(List.of("-extract", "-p", generated.toString(),
                "-out", workspace.resolve(prefix).toString(), "-steg", "LSB1"), options));
        require(result.code() == 1, "el ciphertext malformado " + name + " debe terminar con código 1");
        require(result.stderr().contains(detail), "detalle esperado en " + name + " (" + detail + "): "
                + result.stderr());
        noOutput(prefix);
        pass("ciphertext malformado (" + name + ") rechazado sin salida");
    }

    private void parameterErrors() throws Exception {
        Path secret = secretOfLength(5);
        usageError("valor inválido para -a", secret, "-a", "aes512", "-pass", PASSWORD);
        usageError("valor inválido para -a", secret, "-a", "AES128", "-pass", PASSWORD);
        usageError("valor inválido para -m", secret, "-m", "CBC", "-pass", PASSWORD);
        usageError("valor inválido para -m", secret, "-m", "cfb8", "-pass", PASSWORD);
        usageError("falta el valor de -pass", secret, "-pass", "-a", "aes128");
        usageError("falta el valor de -pass", secret, "-pass");
        usageError("el parámetro -pass está repetido", secret, "-pass", "uno", "-pass", "dos");
        usageError("el valor de -pass no puede estar vacío", secret, "-pass", "");
        usageError("valor inválido para -a", secret, "-a", "aes512");
    }

    private void usageError(String expected, Path secret, String... options) throws Exception {
        String prefix = "uso-" + passed;
        Result result = stego(concat(List.of("-embed", "-in", secret.toString(), "-p", carrier.toString(),
                "-out", workspace.resolve(prefix + ".bmp").toString(), "-steg", "LSB1"), options));
        require(result.code() == 2, "error de parámetros debe terminar con código 2: " + Arrays.toString(options));
        require(result.stderr().contains(expected) && result.stderr().contains("Uso:"),
                "se esperaba \"" + expected + "\" y la sintaxis: " + result.stderr());
        noOutput(prefix);
        pass("parámetros " + Arrays.toString(options) + ": código 2 con sintaxis");
    }

    private void invalidFiles() throws Exception {
        Path secret = secretOfLength(5);
        Path text = workspace.resolve("no-es-bmp.bmp");
        Files.writeString(text, "esto no es un BMP, aunque tenga esa extensión y bastantes bytes de largo.....");
        Path deep = workspace.resolve("profundidad32.bmp");
        Files.write(deep, bitmap(4, 4, 32, 0));
        Path compressed = workspace.resolve("comprimido.bmp");
        Files.write(compressed, bitmap(4, 4, 24, 1));
        Path missing = workspace.resolve("no-existe.bmp");

        fileError("no existe el archivo a ocultar", "-embed", "-in", workspace.resolve("falta.txt").toString(),
                "-p", carrier.toString());
        fileError("no existe el portador", "-embed", "-in", secret.toString(), "-p", missing.toString());
        fileError("no es un archivo BMP válido", "-embed", "-in", secret.toString(), "-p", text.toString());
        fileError("32 bits por píxel", "-embed", "-in", secret.toString(), "-p", deep.toString());
        fileError("está comprimido", "-embed", "-in", secret.toString(), "-p", compressed.toString());
        fileError("no existe el portador", "-extract", "-p", missing.toString());
        fileError("no es un archivo BMP válido", "-extract", "-p", text.toString());
        fileError("está comprimido", "-extract", "-p", compressed.toString());
    }

    private void fileError(String expected, String... args) throws Exception {
        String prefix = "archivo-" + passed;
        boolean embed = args[0].equals("-embed");
        String output = workspace.resolve(embed ? prefix + ".bmp" : prefix).toString();
        Result result = stego(concat(List.of(args), "-out", output, "-steg", "LSBI", "-a", "aes192", "-m", "ofb",
                "-pass", PASSWORD));
        require(result.code() == 1, "error de archivo debe terminar con código 1: " + Arrays.toString(args));
        require(result.stderr().contains(expected), "se esperaba \"" + expected + "\": " + result.stderr());
        require(!result.stderr().contains("Uso:"), "un error de archivo no muestra la sintaxis");
        noOutput(prefix);
        pass("archivo inválido (" + expected + ") con cifrado: código 1 y sin salida");
    }

    private Path secretOfLength(int length) throws IOException {
        Path secret = workspace.resolve("secreto-" + length + ".bin");
        byte[] data = new byte[length];
        for (int index = 0; index < length; index++) {
            data[index] = (byte) (index * 41 + length);
        }
        Files.write(secret, data);
        return secret;
    }

    private Path embed(Path secret, Path bmp, String name, String method, String... options) throws Exception {
        Path output = workspace.resolve(name + ".bmp");
        success(concat(List.of("-embed", "-in", secret.toString(), "-p", bmp.toString(), "-out", output.toString(),
                "-steg", method), options));
        byte[] original = Files.readAllBytes(bmp);
        byte[] embedded = Files.readAllBytes(output);
        require(original.length == embedded.length, "cambió el tamaño del portador: " + name);
        require(Arrays.equals(original, 0, BMP_HEADER_BYTES, embedded, 0, BMP_HEADER_BYTES),
                "cambió el encabezado del portador: " + name);
        return output;
    }

    private void extractAndCompare(Path bmp, Path expected, String prefix, String extension, String method,
            String... options) throws Exception {
        success(concat(List.of("-extract", "-p", bmp.toString(), "-out", workspace.resolve(prefix).toString(),
                "-steg", method), options));
        sameFile(expected, workspace.resolve(prefix + extension));
    }

    private void assertCapacityError(Path secret, Path bmp, String name, String method, int capacity, int needed,
            String... options) throws Exception {
        Path output = workspace.resolve(name + ".bmp");
        Result result = stego(concat(List.of("-embed", "-in", secret.toString(), "-p", bmp.toString(),
                "-out", output.toString(), "-steg", method), options));
        require(result.code() == 1, "el error de capacidad debe terminar con código 1: " + name);
        require(result.stderr().contains("capacidad máxima es de " + capacity + " bytes")
                        && result.stderr().contains("se necesitan " + needed + " bytes"),
                "capacidad " + capacity + " y necesarios " + needed + " en " + name + ": " + result.stderr());
        noOutput(name);
    }

    private void success(String... args) throws Exception {
        Result result = stego(args);
        require(result.code() == 0, "falló stegobmp " + Arrays.toString(args) + ": " + result.stderr());
    }

    private Result stego(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(launcher.toString());
        command.addAll(Arrays.asList(args));
        return execute(command);
    }

    private void sameFile(Path expected, Path actual) throws Exception {
        Result result = execute(List.of("cmp", "--", expected.toString(), actual.toString()));
        require(result.code() == 0, "cmp encontró diferencias entre " + expected + " y " + actual
                + ": " + result.stdout() + result.stderr());
    }

    private Result execute(List<String> command) throws Exception {
        Path stdout = workspace.resolve("stdout.log");
        Path stderr = workspace.resolve("stderr.log");
        Process process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            throw new AssertionError("el comando excedió el tiempo máximo: " + command);
        }
        return new Result(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
    }

    private void noOutput(String prefix) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(workspace, prefix + "*")) {
            require(!files.iterator().hasNext(), "se creó una salida inesperada: " + prefix);
        }
    }

    private static String[] concat(List<String> first, String... rest) {
        List<String> all = new ArrayList<>(first);
        all.addAll(Arrays.asList(rest));
        return all.toArray(String[]::new);
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static byte[] bitmap(int width, int height, int bitsPerPixel, int compression) {
        int stride = (width * CHANNELS_PER_PIXEL + ROW_ALIGNMENT - 1) / ROW_ALIGNMENT * ROW_ALIGNMENT;
        int bodyLength = stride * height;
        ByteBuffer bytes = ByteBuffer.allocate(BMP_HEADER_BYTES + bodyLength).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("BM".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(bytes.capacity()).putInt(0).putInt(BMP_HEADER_BYTES);
        bytes.putInt(DIB_HEADER_BYTES).putInt(width).putInt(height);
        bytes.putShort((short) 1).putShort((short) bitsPerPixel);
        bytes.putInt(compression).putInt(bodyLength);
        bytes.putInt(0).putInt(0).putInt(0).putInt(0);
        for (int index = 0; index < bodyLength; index++) {
            bytes.put((byte) (index * 37 + 11));
        }
        return bytes.array();
    }

    private void pass(String description) {
        passed++;
        System.out.println("OK: " + description);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Special(Path path, String extension) {
    }

    private record Result(int code, String stdout, String stderr) {
    }
}
