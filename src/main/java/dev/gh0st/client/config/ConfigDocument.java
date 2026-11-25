package dev.gh0st.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A config file turned into editable key/value entries.
 * JSON files are parsed properly; TOML, properties, INI and YAML files are edited
 * line by line so their comments and layout are kept.
 */
public final class ConfigDocument {
    public enum Kind { BOOLEAN, NUMBER, TEXT, RAW }

    public static final class Entry {
        public final String label;
        public final Kind kind;
        public String value;
        final String original;
        // JSON
        final List<String> path;
        // line-based
        final int line;
        final String prefix;
        final String quote;
        final String tail;

        Entry(String label, Kind kind, String value, List<String> path, int line, String prefix, String quote, String tail) {
            this.label = label;
            this.kind = kind;
            this.value = value;
            this.original = value;
            this.path = path;
            this.line = line;
            this.prefix = prefix;
            this.quote = quote;
            this.tail = tail;
        }

        public boolean changed() {
            return !value.equals(original);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    // indent, key, separator, rest
    private static final Pattern LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_.\\-\"' ]*?[A-Za-z0-9_\"'])(\\s*[=:]\\s*)(.*)$");
    private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$");

    public final Path path;
    public final List<Entry> entries = new ArrayList<>();
    public String error;

    private final boolean json;
    private JsonObject root;
    private List<String> lines;

    private ConfigDocument(Path path) {
        this.path = path;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        this.json = name.endsWith(".json") || name.endsWith(".json5");
    }

    public static ConfigDocument load(Path path) {
        ConfigDocument doc = new ConfigDocument(path);
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (doc.json) doc.loadJson(text);
            else doc.loadLines(text);
        } catch (Exception e) {
            doc.error = "Couldn't read this file: " + e.getMessage();
        }
        return doc;
    }

    // ---------- JSON ----------

    @SuppressWarnings("deprecation")
    private void loadJson(String text) throws IOException {
        try (Reader r = new StringReader(text)) {
            JsonReader reader = new JsonReader(r);
            reader.setLenient(true); // accept comments, trailing commas, json5-ish files
            JsonElement el = JsonParser.parseReader(reader);
            if (!el.isJsonObject()) {
                error = "This file isn't a JSON object, open it in an editor instead.";
                return;
            }
            root = el.getAsJsonObject();
            flatten(root, new ArrayList<>());
        }
    }

    private void flatten(JsonObject obj, List<String> parents) {
        for (var e : obj.entrySet()) {
            List<String> p = new ArrayList<>(parents);
            p.add(e.getKey());
            String label = String.join(".", p);
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                flatten(v.getAsJsonObject(), p);
            } else if (v.isJsonPrimitive()) {
                JsonPrimitive prim = v.getAsJsonPrimitive();
                Kind k = prim.isBoolean() ? Kind.BOOLEAN : prim.isNumber() ? Kind.NUMBER : Kind.TEXT;
                entries.add(new Entry(label, k, prim.getAsString(), p, -1, null, null, null));
            } else {
                entries.add(new Entry(label, Kind.RAW, GSON.toJson(v).replace("\n", " ").replaceAll("\\s+", " "), p, -1, null, null, null));
            }
        }
    }

    private void saveJson() throws IOException {
        for (Entry e : entries) {
            if (!e.changed()) continue;
            JsonObject parent = root;
            for (int i = 0; i < e.path.size() - 1; i++) parent = parent.getAsJsonObject(e.path.get(i));
            String key = e.path.get(e.path.size() - 1);
            parent.add(key, toJson(e));
        }
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static JsonElement toJson(Entry e) {
        String v = e.value.trim();
        switch (e.kind) {
            case BOOLEAN:
                return new JsonPrimitive(Boolean.parseBoolean(v));
            case NUMBER:
                if (NUMBER.matcher(v).matches()) {
                    if (v.contains(".") || v.contains("e") || v.contains("E")) return new JsonPrimitive(Double.parseDouble(v));
                    try { return new JsonPrimitive(Long.parseLong(v)); } catch (NumberFormatException ignored) { }
                }
                return new JsonPrimitive(e.value);
            case RAW:
                try { return JsonParser.parseString(v); } catch (Exception ex) { return new JsonArray(); }
            default:
                return new JsonPrimitive(e.value);
        }
    }

    // ---------- TOML / properties / INI / YAML ----------

    private void loadLines(String text) {
        lines = new ArrayList<>(List.of(text.split("\\r?\\n", -1)));
        String section = "";
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";") || t.startsWith("//") || t.startsWith("!")) continue;
            if (t.startsWith("[") && t.endsWith("]")) {
                section = t.replaceAll("^\\[+|]+$", "").trim();
                continue;
            }
            Matcher m = LINE.matcher(raw);
            if (!m.matches()) continue;
            String rest = m.group(4);
            if (rest.isEmpty()) continue; // YAML parent key

            String value;
            String quote = "";
            String tail = "";
            char c0 = rest.charAt(0);
            if ((c0 == '"' || c0 == '\'') && rest.indexOf(c0, 1) > 0) {
                int end = rest.indexOf(c0, 1);
                quote = String.valueOf(c0);
                value = rest.substring(1, end);
                tail = rest.substring(end + 1);
            } else {
                int hash = rest.indexOf(" #");
                value = (hash >= 0 ? rest.substring(0, hash) : rest).trim();
                tail = hash >= 0 ? rest.substring(hash) : "";
            }

            Kind k;
            if (!quote.isEmpty()) k = Kind.TEXT;
            else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) k = Kind.BOOLEAN;
            else if (NUMBER.matcher(value).matches()) k = Kind.NUMBER;
            else k = Kind.TEXT;

            String key = m.group(2).replace("\"", "").replace("'", "");
            String label = section.isEmpty() ? key : section + "." + key;
            entries.add(new Entry(label, k, value, null, i, m.group(1) + m.group(2) + m.group(3), quote, tail));
        }
    }

    private void saveLines() throws IOException {
        for (Entry e : entries) {
            if (!e.changed()) continue;
            lines.set(e.line, e.prefix + e.quote + e.value + e.quote + e.tail);
        }
        Files.writeString(path, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
    }

    // ---------- common ----------

    public boolean hasChanges() {
        for (Entry e : entries) if (e.changed()) return true;
        return false;
    }

    public void save() throws IOException {
        if (error != null) return;
        // keep a backup the first time we touch a file
        Path backup = path.resolveSibling(path.getFileName() + ".gh0st-backup");
        if (!Files.exists(backup)) Files.copy(path, backup);
        if (json) saveJson();
        else saveLines();
    }
}
