package stegobmp;

import stegobmp.cli.ArgumentParser;
import stegobmp.cli.EmbedRequest;
import stegobmp.cli.ExtractRequest;
import stegobmp.cli.Request;
import stegobmp.cli.Usage;
import stegobmp.cli.UsageException;

public final class Main {

    private static final int EXIT_OK = 0;
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_USAGE = 2;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        try {
            execute(ArgumentParser.parse(args));
            return EXIT_OK;
        } catch (UsageException e) {
            Messages.error(e.getMessage());
            System.err.println();
            System.err.println(Usage.TEXT);
            return EXIT_USAGE;
        } catch (StegoException e) {
            Messages.error(e.getMessage());
            return EXIT_ERROR;
        } catch (OutOfMemoryError e) {
            Messages.error("no hay memoria suficiente para procesar los archivos.");
            return EXIT_ERROR;
        } catch (RuntimeException e) {
            Messages.error("error inesperado: " + e);
            return EXIT_ERROR;
        }
    }

    private static void execute(Request request) {
        switch (request) {
            case EmbedRequest embed -> Embedder.embed(embed);
            case ExtractRequest extract -> Extractor.extract(extract);
        }
    }
}
