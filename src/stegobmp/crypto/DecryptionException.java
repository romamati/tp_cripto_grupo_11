package stegobmp.crypto;

import stegobmp.StegoException;

public class DecryptionException extends StegoException {

    private static final String MESSAGE = "no se pudo descifrar lo oculto: password, algoritmo o modo incorrectos";

    public DecryptionException() {
        super(MESSAGE + ".");
    }

    public DecryptionException(String detail) {
        super(MESSAGE + " (" + detail + ").");
    }
}
