package stegobmp.cli;

import java.nio.file.Path;
import java.util.Optional;
import stegobmp.crypto.EncryptionSettings;
import stegobmp.steg.StegAlgorithm;

public record EmbedRequest(
        Path input,
        Path carrier,
        Path output,
        StegAlgorithm steg,
        Optional<EncryptionSettings> encryption) implements Request {
}
