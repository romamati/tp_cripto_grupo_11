package stegobmp.cli;

public final class Usage {

    public static final String TEXT = """
            Uso:
              stegobmp -embed -in <archivo> -p <portador.bmp> -out <salida.bmp> -steg <LSB1|LSB4|LSBI>
                       [-a <aes128|aes192|aes256|3des>] [-m <ecb|cfb|ofb|cbc>] [-pass <password>]

              stegobmp -extract -p <portador.bmp> -out <archivo_sin_extension> -steg <LSB1|LSB4|LSBI>
                       [-a <aes128|aes192|aes256|3des>] [-m <ecb|cfb|ofb|cbc>] [-pass <password>]

            Sin -pass no se cifra. Con -pass, si falta -a se usa aes128 y si falta -m se usa cbc.""";

    private Usage() {
    }
}
