package stegobmp.payload;

import stegobmp.StegoException;

public class NoHiddenDataException extends StegoException {

    public NoHiddenDataException(String reason) {
        super(reason);
    }
}
