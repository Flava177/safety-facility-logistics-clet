package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deterministic JSON writer used for audit before/after images, the audit hash input and idempotency
 * fingerprints.
 *
 * <p>A general-purpose object mapper is not deterministic enough for a hash chain: property order,
 * number formatting and map iteration order can all change between versions and between JVM runs.
 * This writer sorts object keys, renders temporals and UUIDs through {@code toString()} and escapes
 * strings to the JSON minimum, so the same logical value always produces the same bytes.
 *
 * <p>Callers pass explicit, data-minimised maps rather than whole entities, which keeps personal data
 * such as licence numbers out of the audit image unless a rule requires it.
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(128);
        writeValue(builder, value);
        return builder.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof Collection<?> collection) {
            writeArray(out, collection);
        } else if (value instanceof Object[] array) {
            writeArray(out, java.util.Arrays.asList(array));
        } else if (value instanceof Boolean bool) {
            out.append(bool.booleanValue());
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte) {
            out.append(value);
        } else if (value instanceof java.math.BigDecimal decimal) {
            out.append(decimal.stripTrailingZeros().toPlainString());
        } else if (value instanceof Double || value instanceof Float) {
            out.append(new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString());
        } else if (value instanceof Number number) {
            out.append(number);
        } else if (value instanceof Enum<?> enumValue) {
            writeString(out, enumValue.name());
        } else if (value instanceof Temporal || value instanceof UUID || value instanceof CharSequence) {
            writeString(out, value.toString());
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        Map<String, Object> sorted = new TreeMap<>();
        map.forEach((key, entryValue) -> sorted.put(String.valueOf(key), entryValue));
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, entry.getKey());
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Collection<?> values) {
        out.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, value);
        }
        out.append(']');
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }
}
