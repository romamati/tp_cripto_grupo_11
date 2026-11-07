package stegobmp;

import java.nio.file.Path;
import java.util.function.Supplier;
import stegobmp.bmp.BmpFile;
import stegobmp.cli.ExtractRequest;
import stegobmp.crypto.DecryptionException;
import stegobmp.crypto.EncryptionSettings;
import stegobmp.crypto.Encryptor;
import stegobmp.io.FileAccess;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.NoHiddenDataException;
import stegobmp.payload.PayloadFormat;

public final class Extractor {

    private Extractor() {
    }

    public static void extract(ExtractRequest request) {
        BmpFile carrier = BmpFile.read(request.carrier());
        byte[] recovered = request.steg().method().extractAll(carrier.body());
        HiddenFile file = request.encryption()
                .map(settings -> decryptAndUnpack(recovered, settings, request))
                .orElseGet(() -> findHidden(() -> PayloadFormat.unpack(recovered), request));
        Path output = Path.of(request.outputWithoutExtension() + file.extension());
        FileAccess.write(output, "el archivo de salida", file.content());
        Messages.info("Se extrajo " + FileAccess.quoted(output) + " (" + file.content().length + " bytes).");
    }

    private static HiddenFile decryptAndUnpack(byte[] recovered, EncryptionSettings settings, ExtractRequest request) {
        byte[] encrypted = findHidden(() -> PayloadFormat.sizePrefixedBlock(recovered), request);
        byte[] plain = Encryptor.from(settings).decrypt(encrypted);
        try {
            return PayloadFormat.unpack(plain);
        } catch (NoHiddenDataException e) {
            throw new DecryptionException();
        }
    }

    private static <T> T findHidden(Supplier<T> parse, ExtractRequest request) {
        try {
            return parse.get();
        } catch (NoHiddenDataException e) {
            throw new StegoException("no se encontró información oculta con " + request.steg() + " en "
                    + FileAccess.quoted(request.carrier()) + ": " + e.getMessage() + ".");
        }
    }
}
