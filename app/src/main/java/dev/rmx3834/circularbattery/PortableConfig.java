package dev.rmx3834.circularbattery;

import java.util.LinkedHashMap;
import java.util.Map;

final class PortableConfig {
    private PortableConfig() {}

    static Map<String, String> parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        String section = null;
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                if (!"battery".equals(section) && !"clock".equals(section)) {
                    throw new IllegalArgumentException("Line " + (i + 1) + ": unknown section " + section);
                }
                continue;
            }
            int separator = line.indexOf('=');
            if (section == null || separator < 1) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": expected section or key = value");
            }
            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim(), i + 1);
            String fullKey = section + "." + key;
            if (values.put(fullKey, value) != null) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": duplicate key " + fullKey);
            }
        }
        return values;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) quoted = !quoted;
            else if (c == '#' && !quoted && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value, int line) {
        if (value.matches("-?[0-9]+")) return value;
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw new IllegalArgumentException("Line " + line + ": expected a quoted string or integer");
        }
        return value.substring(1, value.length() - 1);
    }

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
