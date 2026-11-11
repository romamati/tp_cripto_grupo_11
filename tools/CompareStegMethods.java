import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import stegobmp.bmp.BmpFile;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.PayloadFormat;
import stegobmp.steg.StegAlgorithm;
import stegobmp.steg.StegMethod;

public final class CompareStegMethods {

    private static final Path EXAMPLES = Path.of("materiales/ejemplo");
    private static final int CHANNELS = 3;
    private static final int FLAGS = 4;
    private static final int MAX_COMPONENT_VALUE = 255;

    private CompareStegMethods() {
    }

    public static void main(String[] args) throws Exception {
        Path carrierPath = EXAMPLES.resolve("lado.bmp");
        byte[] original = BmpFile.read(carrierPath).body();
        byte[] reference = BmpFile.read(EXAMPLES.resolve("ladoLSBI.bmp")).body();
        HiddenFile secret = PayloadFormat.unpack(StegAlgorithm.LSBI.method().extractAll(reference));
        byte[] payload = PayloadFormat.pack(secret);
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("Portador: " + carrierPath + ", " + Files.size(carrierPath) + " bytes");
        System.out.println("SHA-256 del portador: " + hash(Files.readAllBytes(carrierPath)));
        System.out.println("Secreto PNG: " + secret.content().length + " bytes, SHA-256 " + hash(secret.content()));
        System.out.println("Payload completo: " + payload.length + " bytes, sin cifrado");
        System.out.println("MSE calculado sobre " + original.length + " componentes BGR, sin encabezado");
        System.out.println();
        System.out.println("| Método | Capacidad payload | Bytes modificados | Bits modificados | Delta máximo | MSE | PSNR (dB) |");
        System.out.println("| --- | --- | --- | --- | --- | --- | --- |");
        Measurements[] measurements = new Measurements[StegAlgorithm.values().length];
        int flagChanges = 0;
        for (StegAlgorithm algorithm : StegAlgorithm.values()) {
            StegMethod method = algorithm.method();
            byte[] body = original.clone();
            method.embed(body, payload);
            HiddenFile recovered = PayloadFormat.unpack(method.extractAll(body));
            require(secret.extension().equals(recovered.extension())
                    && Arrays.equals(secret.content(), recovered.content()), "falló la recuperación con " + algorithm);
            byte[] expected = BmpFile.read(EXAMPLES.resolve("lado" + algorithm + ".bmp")).body();
            require(Arrays.equals(expected, body), "el resultado difiere del ejemplo oficial de " + algorithm);
            Measurements result = measure(original, body);
            measurements[algorithm.ordinal()] = result;
            System.out.printf(Locale.ROOT, "| %s | %d | %d | %d | %d | %.6f | %.4f |%n", algorithm,
                    method.capacity(body.length), result.changedBytes(), result.changedBits(),
                    result.maxDifference(), result.mse(), result.psnr());
            if (algorithm == StegAlgorithm.LSBI) {
                for (int index = 0; index < FLAGS; index++) {
                    if (original[index] != body[index]) {
                        flagChanges++;
                    }
                }
            }
        }
        System.out.println();
        System.out.println("| Método | Azul | Verde | Rojo |");
        System.out.println("| --- | --- | --- | --- |");
        for (StegAlgorithm algorithm : StegAlgorithm.values()) {
            long[] channels = measurements[algorithm.ordinal()].changedChannels();
            System.out.printf("| %s | %d | %d | %d |%n", algorithm, channels[0], channels[1], channels[2]);
        }
        printInversionControl(original, payload, flagChanges, measurements[StegAlgorithm.LSBI.ordinal()]);
        System.out.println();
        System.out.println("Los tres resultados recuperan el mismo archivo y coinciden con sus ejemplos oficiales.");
        System.out.println("La herramienta trabaja en memoria; no escribe imágenes ni archivos de salida.");
    }

    private static Measurements measure(byte[] original, byte[] body) {
        long changedBytes = 0;
        long changedBits = 0;
        long squaredError = 0;
        int maxDifference = 0;
        long[] channels = new long[CHANNELS];
        for (int index = 0; index < original.length; index++) {
            int before = Byte.toUnsignedInt(original[index]);
            int after = Byte.toUnsignedInt(body[index]);
            int difference = after - before;
            if (difference != 0) {
                changedBytes++;
                channels[index % CHANNELS]++;
            }
            changedBits += Integer.bitCount(before ^ after);
            squaredError += (long) difference * difference;
            maxDifference = Math.max(maxDifference, Math.abs(difference));
        }
        double mse = (double) squaredError / original.length;
        double psnr = mse == 0 ? Double.POSITIVE_INFINITY
                : 10 * Math.log10((double) MAX_COMPONENT_VALUE * MAX_COMPONENT_VALUE / mse);
        return new Measurements(changedBytes, changedBits, maxDifference, mse, psnr, channels);
    }

    private static void printInversionControl(byte[] original, byte[] payload, int flagChanges,
            Measurements lsbi) {
        int[][] counts = new int[FLAGS][2];
        int position = FLAGS;
        for (byte value : payload) {
            for (int shift = Byte.SIZE - 1; shift >= 0; shift--) {
                if (position % CHANNELS == CHANNELS - 1) {
                    position++;
                }
                int pattern = (original[position] >> 1) & (FLAGS - 1);
                int changed = (original[position] & 1) ^ ((value >> shift) & 1);
                counts[pattern][changed]++;
                position++;
            }
        }
        System.out.println();
        System.out.println("| Patrón | Sin cambios | Cambiados sin inversión | Cambiados con inversión |");
        System.out.println("| --- | --- | --- | --- |");
        long withoutInversion = 0;
        long withInversion = 0;
        for (int pattern = 0; pattern < FLAGS; pattern++) {
            int unchanged = counts[pattern][0];
            int changed = counts[pattern][1];
            int minimum = Math.min(unchanged, changed);
            System.out.printf("| %s | %d | %d | %d |%n",
                    Integer.toBinaryString(pattern | FLAGS).substring(1), unchanged, changed, minimum);
            withoutInversion += changed;
            withInversion += minimum;
        }
        require(withInversion + flagChanges == lsbi.changedBytes(), "conteo inconsistente de cambios LSBI");
        System.out.println("Cambios del control LSB1 B/G en las mismas posiciones, sin inversión: " + withoutInversion);
        System.out.println("Cambios del mensaje LSBI: " + withInversion + "; cambios de indicadores: " + flagChanges);
        System.out.printf(Locale.ROOT, "Reducción por inversión sobre ese control: %.4f %%%n",
                100.0 * (withoutInversion - withInversion) / withoutInversion);
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Measurements(long changedBytes, long changedBits, int maxDifference,
            double mse, double psnr, long[] changedChannels) {
    }
}
