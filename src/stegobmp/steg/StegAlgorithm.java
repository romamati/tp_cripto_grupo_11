package stegobmp.steg;

public enum StegAlgorithm {
    LSB1(new Lsb1()),
    LSB4(new Lsb4()),
    LSBI(new Lsbi());

    private final StegMethod method;

    StegAlgorithm(StegMethod method) {
        this.method = method;
    }

    public StegMethod method() {
        return method;
    }
}
