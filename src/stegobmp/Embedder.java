package stegobmp;

import java.nio.file.Path;
import java.util.Optional;
import stegobmp.bmp.BmpFile;
import stegobmp.cli.EmbedRequest;
import stegobmp.crypto.EncryptionSettings;
import stegobmp.crypto.Encryptor;
import stegobmp.io.FileAccess;
import stegobmp.payload.FileExtension;
import stegobmp.payload.HiddenFile;
import stegobmp.payload.PayloadFormat;
import stegobmp.steg.StegMethod;

public final class Embedder {

    private Embedder() {
    }

    public static void embed(EmbedRequest request) {
        HiddenFile file = readFileToHide(request.input());
        BmpFile carrier = BmpFile.read(request.carrier());
        byte[] payload = buildPayload(file, request.encryption());
        StegMethod method = request.steg().method();
        requireCapacity(method.capacity(carrier.body().length), payload.length, request);
        method.embed(carrier.body(), payload);
        carrier.write(request.output());
        Messages.info("Se ocultó " + FileAccess.quoted(request.input()) + " en "
                + FileAccess.quoted(request.output()) + " con " + request.steg() + ".");
    }

    private static HiddenFile readFileToHide(Path input) {
        byte[] content = FileAccess.read(input, "el archivo a ocultar");
        if (content.length == 0) {
            throw new StegoException("el archivo a ocultar " + FileAccess.quoted(input) + " está vacío.");
        }
        String extension = FileExtension.of(input);
        if (extension.isEmpty()) {
            Messages.warn("el archivo a ocultar " + FileAccess.quoted(input)
                    + " no tiene extensión: se oculta sin extensión.");
        }
        return new HiddenFile(content, extension);
    }

    private static byte[] buildPayload(HiddenFile file, Optional<EncryptionSettings> encryption) {
        byte[] plain = PayloadFormat.pack(file);
        return encryption
                .map(settings -> PayloadFormat.withSizePrefix(Encryptor.from(settings).encrypt(plain)))
                .orElse(plain);
    }

    private static void requireCapacity(int capacity, int needed, EmbedRequest request) {
        if (needed > capacity) {
            throw new StegoException("el archivo no entra en el portador " + FileAccess.quoted(request.carrier())
                    + ": con " + request.steg() + " la capacidad máxima es de " + capacity
                    + " bytes y se necesitan " + needed + " bytes.");
        }
    }
}
