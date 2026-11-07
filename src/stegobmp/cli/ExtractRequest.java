package stegobmp.cli;

import java.nio.file.Path;
import java.util.Optional;
import stegobmp.crypto.EncryptionSettings;
import stegobmp.steg.StegAlgorithm;

public record ExtractRequest(
        Path carrier,
        String outputWithoutExtension,
        StegAlgorithm steg,
        Optional<EncryptionSettings> encryption) implements Request {
}
