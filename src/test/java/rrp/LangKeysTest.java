package rrp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aucune clé de texte utilisée par le code ne doit manquer dans lang_fr.yml, et l'aide couvre toute sous-commande. */
class LangKeysTest {

    // Transitoire (portage NeoForge) : tant que Commands.java n'est pas porte, on lit la reference Paper.
    private static final Path SRC = Files.exists(Path.of("src/main/java/rrp/neoforge/Commands.java"))
            ? Path.of("src/main/java/rrp/neoforge") : Path.of("legacy-paper/java/rrp/bukkit");
    private static final Pattern KEY = Pattern.compile("\"((?:[a-z_]+\\.)+[a-z_]+)\"");
    private static final Pattern LANG_CALL = Pattern.compile("\\blang\\s*\\.\\s*(?:get|msg)\\s*\\(");
    private static final Pattern YAML_KEY = Pattern.compile("^\"?([A-Za-z0-9_.]+)\"?\\s*:", Pattern.MULTILINE);

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static Set<String> langKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        Matcher m = YAML_KEY.matcher(read(Path.of("src/main/resources/lang_fr.yml")));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @Test
    void everyReferencedKeyExists() throws IOException {
        Set<String> have = langKeys();
        Set<String> missing = new TreeSet<>();
        int seen = 0;
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String code = read(f);
                Matcher call = LANG_CALL.matcher(code);
                while (call.find()) {
                    // Les littéraux de clé de l'appel (jusqu'à la fin de l'instruction) : peut contenir un ternaire.
                    int end = code.indexOf(';', call.end());
                    Matcher k = KEY.matcher(code.substring(call.end(), end < 0 ? code.length() : end));
                    while (k.find()) {
                        seen++;
                        if (!have.contains(k.group(1))) {
                            missing.add(f.getFileName() + " : " + k.group(1));
                        }
                    }
                }
            }
        }
        assertTrue(seen > 30, "peu de clés repérées (" + seen + ") : l'analyse du code est cassée");
        assertTrue(missing.isEmpty(), "clés absentes de lang_fr.yml : " + missing);
    }

    @Test
    void dynamicKeyFamiliesAreComplete() throws IOException {
        Set<String> have = langKeys();
        for (String cause : new String[] {"famine", "overpopulation", "insecurity", "unknown"}) {
            assertTrue(have.contains("crisis.cause." + cause), "crisis.cause." + cause);
        }
    }

    @Test
    void helpListsEverySubcommand() throws IOException {
        Set<String> have = langKeys();
        String code = read(SRC.resolve("Commands.java"));
        Matcher sw = Pattern.compile("case \"([a-z]+)\" ->").matcher(code.substring(code.indexOf("onCommand")));
        Set<String> subs = new TreeSet<>();
        while (sw.find()) {
            subs.add(sw.group(1));
            if (sw.group(1).equals("reload")) {
                break;
            }
        }
        assertTrue(subs.size() >= 12, "sous-commandes repérées : " + subs);
        Matcher list = Pattern.compile("SUBS = List.of\\(([^)]*)\\)").matcher(code);
        assertTrue(list.find(), "liste SUBS introuvable");
        for (String s : subs) {
            assertTrue(list.group(1).contains("\"" + s + "\""), "sous-commande absente de SUBS (donc de /respublica help) : " + s);
            assertTrue(have.contains("help." + s), "texte d'aide manquant : help." + s);
        }
    }
}
