package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration;

import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort.TelemetryTranslationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Field readers shared by the BMS adapters. Each failure names the field and the item, because the person
 * reading the rejection is the vendor's integrator, and "schema invalid" alone costs them a support call.
 */
final class TelemetryPayloadFields {

    private TelemetryPayloadFields() {
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> items(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof List<?> list)) {
            throw new TelemetryTranslationException("'" + field + "' must be an array of readings");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                throw new TelemetryTranslationException("every entry of '" + field + "' must be an object");
            }
        }
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (!(value instanceof Map<?, ?>)) {
            throw new TelemetryTranslationException("'" + field + "' must be an object");
        }
        return (Map<String, Object>) value;
    }

    static String text(Map<String, Object> item, String field, int index) {
        Object value = item.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new TelemetryTranslationException("reading " + index + " is missing '" + field + "'");
        }
        return String.valueOf(value).strip();
    }

    static BigDecimal number(Map<String, Object> item, String field, int index) {
        Object value = item.get(field);
        if (value instanceof Boolean || value == null) {
            throw new TelemetryTranslationException("reading " + index + " '" + field + "' must be a number");
        }
        try {
            return new BigDecimal(String.valueOf(value).strip());
        } catch (NumberFormatException notANumber) {
            throw new TelemetryTranslationException("reading " + index + " '" + field + "' must be a number");
        }
    }

    static Instant instant(Map<String, Object> item, String field, int index) {
        try {
            return Instant.parse(text(item, field, index));
        } catch (DateTimeParseException malformed) {
            throw new TelemetryTranslationException("reading " + index + " '" + field
                    + "' must be an ISO-8601 instant");
        }
    }

    static Instant epochMillis(Map<String, Object> item, String field, int index) {
        try {
            return Instant.ofEpochMilli(number(item, field, index).longValueExact());
        } catch (ArithmeticException notWhole) {
            throw new TelemetryTranslationException("reading " + index + " '" + field
                    + "' must be whole epoch milliseconds");
        }
    }
}
