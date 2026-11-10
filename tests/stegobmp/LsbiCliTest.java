package stegobmp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

public final class LsbiCliTest {

    private static final List<String> METHODS = List.of("LSB1", "LSB4", "LSBI");
    private static final int BMP_HEADER_BYTES = 54;
    private static final int DIB_HEADER_BYTES = 40;
    private static final int CHANNELS_PER_PIXEL = 3;
    private static final int BITS_PER_PIXEL = 24;
    private static final int ROW_ALIGNMENT = 4;
    private static final int PADDED_IMAGE_HEIGHT = 128;
    private static final int PROCESS_TIMEOUT_SECONDS = 20;
    private static final String PNG_SHA256 =
            "b93be15382f35f099ca286241dd7bb6e681d5bce8d315ac0b6240f0b516f549d";

    private final Path launcher = Path.of("stegobmp").toAbsolutePath();
    private final Path workspace;
    private final Path examples;
    private int passed;

    private LsbiCliTest(Path workspace) throws IOException {
        this.workspace = workspace;
        this.examples = Files.createDirectory(workspace.resolve("ejemplos"));
        Path source = Path.of("materiales/ejemplo").toAbsolutePath();
        for (String file : List.of("lado.bmp", "ladoLSB1.bmp", "ladoLSB4.bmp", "ladoLSBI.bmp")) {
            Files.copy(source.resolve(file), examples.resolve(file));
        }
    }

    public static void main(String[] args) throws Exception {
        Path workspace = Files.createTempDirectory("stegobmp-cli-");
        try {
            LsbiCliTest suite = new LsbiCliTest(workspace);
            suite.officialExamples();
            suite.carrierWithoutMessage();
            suite.binaryRoundTrips();
            suite.paddedCarriers();
            suite.capacityErrors();
            System.out.println("Resultado: " + suite.passed + " casos por CLI sin cifrado aprobados.");
        } finally {
            try (Stream<Path> paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private void officialExamples() throws Exception {
        for (String method : METHODS) {
            Path reference = examples.resolve("lado" + method + ".bmp");
            Path output = workspace.resolve("oficial-" + method);
            success("-extract", "-p", reference.toString(), "-out", output.toString(), "-steg", method);
            Path png = workspace.resolve("oficial-" + method + ".png");
            require(Files.size(png) == 44_886, "tamaño incorrecto del PNG oficial de " + method);
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(png)));
            require(PNG_SHA256.equals(hash), "SHA-256 incorrecto del PNG oficial de " + method);
            pass("extracción oficial " + method + ", tamaño y SHA-256");
            Path generated = workspace.resolve("regenerado-" + method + ".bmp");
            success("-embed", "-in", png.toString(), "-p", examples.resolve("lado.bmp").toString(),
                    "-out", generated.toString(), "-steg", method);
            sameFile(reference, generated);
            pass("regeneración idéntica " + method + " con cmp");
        }
    }

    private void carrierWithoutMessage() throws Exception {
        for (String method : METHODS) {
            String prefix = "sin-mensaje-" + method;
            Result result = stego("-extract", "-p", examples.resolve("lado.bmp").toString(),
                    "-out", workspace.resolve(prefix).toString(), "-steg", method);
            require(result.code() == 1, "el portador sin mensaje debe terminar con código 1: " + method);
            require(result.stderr().contains("no se encontró información oculta"),
                    "mensaje de error incorrecto: " + result.stderr());
            require(result.stdout().isBlank(), "el error debe informarse por stderr");
            noOutput(prefix);
            pass("portador sin mensaje " + method + ", código 1 y sin archivo de salida");
        }
    }

    private void binaryRoundTrips() throws Exception {
        byte[] data = new byte[256];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) index;
        }
        Path secret = workspace.resolve("secreto binario.bin");
        Files.write(secret, data);
        for (String method : METHODS) {
            roundTrip(secret, examples.resolve("lado.bmp"), method, "binario-" + method);
            pass("ida y vuelta binaria " + method + " con los 256 valores de byte");
        }
    }

    private void paddedCarriers() throws Exception {
        byte[] data = new byte[33];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) (index * 17 + 0x80);
        }
        data[0] = 0;
        data[1] = (byte) 0xFF;
        Path secret = workspace.resolve("corto.bin");
        Files.write(secret, data);
        for (int width = 1; width <= CHANNELS_PER_PIXEL; width++) {
            Path carrier = workspace.resolve("padding-" + width + ".bmp");
            Files.write(carrier, bitmap(width, PADDED_IMAGE_HEIGHT));
            for (String method : METHODS) {
                Path generated = roundTrip(secret, carrier, method, "padding-" + width + "-" + method);
                require(changedPadding(carrier, generated, width, PADDED_IMAGE_HEIGHT),
                        "el caso debe utilizar bytes de padding: " + method + ", ancho " + width);
                pass("padding de " + width + " bytes por fila, " + method + ", encabezado y cmp");
            }
        }
    }

    private void capacityErrors() throws Exception {
        Path secret = workspace.resolve("exceso.bin");
        Files.write(secret, new byte[34]);
        Path carrier = workspace.resolve("padding-1.bmp");
        Path output = workspace.resolve("rechazado.bmp");
        assertCapacityError(secret, carrier, output);
        noOutput("rechazado");
        pass("capacidad LSBI: rechaza 43 bytes cuando caben 42, sin crear salida");
        byte[] sentinel = {1, 2, 3, 4, 5};
        Files.write(output, sentinel);
        assertCapacityError(secret, carrier, output);
        require(Arrays.equals(sentinel, Files.readAllBytes(output)),
                "un error de capacidad no debe sobrescribir una salida existente");
        pass("capacidad LSBI: conserva una salida existente al rechazar el payload");
    }

    private void assertCapacityError(Path secret, Path carrier, Path output) throws Exception {
        Result result = stego("-embed", "-in", secret.toString(), "-p", carrier.toString(),
                "-out", output.toString(), "-steg", "LSBI");
        require(result.code() == 1, "el error de capacidad debe terminar con código 1");
        require(result.stderr().contains("capacidad máxima es de 42 bytes")
                        && result.stderr().contains("se necesitan 43 bytes"),
                "el error debe indicar capacidad y bytes necesarios: " + result.stderr());
        require(result.stdout().isBlank(), "el error de capacidad debe informarse por stderr");
    }

    private Path roundTrip(Path secret, Path carrier, String method, String name) throws Exception {
        Path generated = workspace.resolve(name + ".bmp");
        success("-embed", "-in", secret.toString(), "-p", carrier.toString(),
                "-out", generated.toString(), "-steg", method);
        byte[] original = Files.readAllBytes(carrier);
        byte[] embedded = Files.readAllBytes(generated);
        require(original.length == embedded.length, "cambió el tamaño del portador: " + name);
        require(Arrays.equals(original, 0, BMP_HEADER_BYTES, embedded, 0, BMP_HEADER_BYTES),
                "cambió el encabezado del portador: " + name);
        Path output = workspace.resolve(name + "-extraido");
        success("-extract", "-p", generated.toString(), "-out", output.toString(), "-steg", method);
        sameFile(secret, workspace.resolve(name + "-extraido.bin"));
        return generated;
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

    private static byte[] bitmap(int width, int height) {
        int stride = rowStride(width);
        int bodyLength = stride * height;
        ByteBuffer bytes = ByteBuffer.allocate(BMP_HEADER_BYTES + bodyLength).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'B').put((byte) 'M');
        bytes.putInt(bytes.capacity()).putInt(0).putInt(BMP_HEADER_BYTES);
        bytes.putInt(DIB_HEADER_BYTES).putInt(width).putInt(height);
        bytes.putShort((short) 1).putShort((short) BITS_PER_PIXEL);
        bytes.putInt(0).putInt(bodyLength);
        bytes.putInt(0).putInt(0).putInt(0).putInt(0);
        for (int index = 0; index < bodyLength; index++) {
            bytes.put((byte) (index * 37 + 11));
        }
        return bytes.array();
    }

    private static boolean changedPadding(Path original, Path generated, int width, int height) throws IOException {
        byte[] before = Files.readAllBytes(original);
        byte[] after = Files.readAllBytes(generated);
        int stride = rowStride(width);
        for (int row = 0; row < height; row++) {
            for (int column = width * CHANNELS_PER_PIXEL; column < stride; column++) {
                int offset = BMP_HEADER_BYTES + row * stride + column;
                if (before[offset] != after[offset]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int rowStride(int width) {
        return (width * CHANNELS_PER_PIXEL + ROW_ALIGNMENT - 1) / ROW_ALIGNMENT * ROW_ALIGNMENT;
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

    private record Result(int code, String stdout, String stderr) {
    }
}
