package stegobmp.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import stegobmp.Messages;
import stegobmp.crypto.CipherAlgorithm;
import stegobmp.crypto.CipherMode;
import stegobmp.crypto.EncryptionSettings;
import stegobmp.steg.StegAlgorithm;

public final class ArgumentParser {

    private static final String EMBED = "-embed";
    private static final String EXTRACT = "-extract";
    private static final String INPUT = "-in";
    private static final String CARRIER = "-p";
    private static final String OUTPUT = "-out";
    private static final String STEG = "-steg";
    private static final String ALGORITHM = "-a";
    private static final String MODE = "-m";
    private static final String PASSWORD = "-pass";

    private static final Set<String> OPERATIONS = Set.of(EMBED, EXTRACT);
    private static final Set<String> OPTIONS_WITH_VALUE =
            Set.of(INPUT, CARRIER, OUTPUT, STEG, ALGORITHM, MODE, PASSWORD);
    private static final List<String> EMBED_REQUIRED = List.of(INPUT, CARRIER, OUTPUT, STEG);
    private static final List<String> EXTRACT_REQUIRED = List.of(CARRIER, OUTPUT, STEG);

    private static final CipherAlgorithm DEFAULT_ALGORITHM = CipherAlgorithm.AES128;
    private static final CipherMode DEFAULT_MODE = CipherMode.CBC;

    private static final String HYPHEN = "-";
    private static final String EN_DASH = "–";

    private final Set<String> operations = new HashSet<>();
    private final Map<String, String> values = new HashMap<>();

    private ArgumentParser() {
    }

    public static Request parse(String[] args) {
        ArgumentParser parser = new ArgumentParser();
        parser.scan(args);
        return parser.buildRequest();
    }

    private void scan(String[] args) {
        int index = 0;
        while (index < args.length) {
            String name = normalizeDash(args[index]);
            if (OPERATIONS.contains(name)) {
                rejectRepeated(name, operations.contains(name));
                operations.add(name);
                index++;
            } else if (OPTIONS_WITH_VALUE.contains(name)) {
                rejectRepeated(name, values.containsKey(name));
                values.put(name, valueAfter(args, index, name));
                index += 2;
            } else if (name.startsWith(HYPHEN)) {
                throw new UsageException("parámetro desconocido: \"" + args[index] + "\".");
            } else {
                throw new UsageException("argumento inesperado: \"" + args[index]
                        + "\". Cada valor va después del nombre de su parámetro, por ejemplo: -p imagen.bmp");
            }
        }
    }

    private static String normalizeDash(String argument) {
        return argument.startsWith(EN_DASH) ? HYPHEN + argument.substring(EN_DASH.length()) : argument;
    }

    private static boolean isParameterName(String argument) {
        String name = normalizeDash(argument);
        return OPERATIONS.contains(name) || OPTIONS_WITH_VALUE.contains(name);
    }

    private static void rejectRepeated(String name, boolean alreadyPresent) {
        if (alreadyPresent) {
            throw new UsageException("el parámetro " + name + " está repetido.");
        }
    }

    private static String valueAfter(String[] args, int index, String name) {
        int valueIndex = index + 1;
        if (valueIndex >= args.length || isParameterName(args[valueIndex])) {
            throw new UsageException("falta el valor de " + name + ".");
        }
        String value = args[valueIndex];
        if (value.isEmpty()) {
            throw new UsageException("el valor de " + name + " no puede estar vacío.");
        }
        return value;
    }

    private Request buildRequest() {
        boolean embed = readOperation();
        requirePresent(embed ? EMBED_REQUIRED : EXTRACT_REQUIRED);
        if (!embed && values.containsKey(INPUT)) {
            throw new UsageException("el parámetro " + INPUT + " solo se usa con " + EMBED + ".");
        }
        StegAlgorithm steg = parseChoice(STEG, values.get(STEG), StegAlgorithm.values(), StegAlgorithm::name);
        Optional<EncryptionSettings> encryption = readEncryption();
        Path carrier = path(CARRIER);
        if (embed) {
            return new EmbedRequest(path(INPUT), carrier, path(OUTPUT), steg, encryption);
        }
        return new ExtractRequest(carrier, values.get(OUTPUT), steg, encryption);
    }

    private boolean readOperation() {
        boolean embed = operations.contains(EMBED);
        boolean extract = operations.contains(EXTRACT);
        if (embed && extract) {
            throw new UsageException("no se puede usar " + EMBED + " y " + EXTRACT + " a la vez.");
        }
        if (!embed && !extract) {
            throw new UsageException("falta indicar la operación: " + EMBED + " o " + EXTRACT + ".");
        }
        return embed;
    }

    private void requirePresent(List<String> required) {
        List<String> missing = required.stream().filter(name -> !values.containsKey(name)).toList();
        if (missing.size() == 1) {
            throw new UsageException("falta el parámetro obligatorio " + missing.getFirst() + ".");
        }
        if (!missing.isEmpty()) {
            throw new UsageException("faltan los parámetros obligatorios " + String.join(", ", missing) + ".");
        }
    }

    private Optional<EncryptionSettings> readEncryption() {
        Optional<CipherAlgorithm> algorithm =
                optionalChoice(ALGORITHM, CipherAlgorithm.values(), CipherAlgorithm::cliName);
        Optional<CipherMode> mode = optionalChoice(MODE, CipherMode.values(), CipherMode::cliName);
        String password = values.get(PASSWORD);
        if (password == null) {
            warnIgnoredWithoutPassword(algorithm.isPresent(), mode.isPresent());
            return Optional.empty();
        }
        return Optional.of(new EncryptionSettings(
                algorithm.orElse(DEFAULT_ALGORITHM), mode.orElse(DEFAULT_MODE), password));
    }

    private static void warnIgnoredWithoutPassword(boolean hasAlgorithm, boolean hasMode) {
        String reason = " porque no se indicó " + PASSWORD + ": sin password no se cifra.";
        if (hasAlgorithm && hasMode) {
            Messages.warn("se ignoran " + ALGORITHM + " y " + MODE + reason);
        } else if (hasAlgorithm) {
            Messages.warn("se ignora " + ALGORITHM + reason);
        } else if (hasMode) {
            Messages.warn("se ignora " + MODE + reason);
        }
    }

    private <E> Optional<E> optionalChoice(String name, E[] choices, Function<E, String> nameOf) {
        return Optional.ofNullable(values.get(name)).map(value -> parseChoice(name, value, choices, nameOf));
    }

    private static <E> E parseChoice(String name, String value, E[] choices, Function<E, String> nameOf) {
        return Arrays.stream(choices)
                .filter(choice -> nameOf.apply(choice).equals(value))
                .findFirst()
                .orElseThrow(() -> new UsageException("valor inválido para " + name + ": \"" + value
                        + "\". Valores válidos: " + listChoices(choices, nameOf) + "."));
    }

    private static <E> String listChoices(E[] choices, Function<E, String> nameOf) {
        return Arrays.stream(choices).map(nameOf).collect(Collectors.joining(", "));
    }

    private Path path(String name) {
        String value = values.get(name);
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new UsageException("ruta inválida para " + name + ": \"" + value + "\".");
        }
    }
}
