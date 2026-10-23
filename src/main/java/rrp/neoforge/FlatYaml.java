package rrp.neoforge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lecteur minimal de YAML plat (« clé: valeur », commentaires #, chaînes entre guillemets, nombres). */
public final class FlatYaml {

    private FlatYaml() {
    }

    /** Valeurs : Double pour un nombre, Boolean pour true/false, String sinon. */
    public static Map<String, Object> parse(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(line.substring(0, colon).strip());
            String value = line.substring(colon + 1).strip();
            if (value.isEmpty()) {
                continue;
            }
            out.put(key, scalar(value));
        }
        return out;
    }

    private static Object scalar(String v) {
        if (v.startsWith("\"")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < v.length(); i++) {
                char c = v.charAt(i);
                if (c == '\\' && i + 1 < v.length()) {
                    char n = v.charAt(++i);
                    sb.append(n == 'n' ? '\n' : n);
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        if (v.startsWith("'")) {
            int end = v.lastIndexOf('\'');
            return v.substring(1, Math.max(1, end)).replace("''", "'");
        }
        int hash = v.indexOf(" #");
        if (hash >= 0) {
            v = v.substring(0, hash).strip();
        }
        if (v.equals("true") || v.equals("false")) {
            return Boolean.valueOf(v);
        }
        try {
            return Double.valueOf(v);
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private static String unquote(String k) {
        if (k.length() >= 2 && (k.startsWith("\"") && k.endsWith("\"") || k.startsWith("'") && k.endsWith("'"))) {
            return k.substring(1, k.length() - 1);
        }
        return k;
    }
}
