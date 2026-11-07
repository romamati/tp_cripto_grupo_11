package stegobmp.crypto;

import stegobmp.StegoException;

public class DecryptionException extends StegoException {

    public DecryptionException() {
        super("no se pudo descifrar lo oculto: password, algoritmo o modo incorrectos.");
    }
}
