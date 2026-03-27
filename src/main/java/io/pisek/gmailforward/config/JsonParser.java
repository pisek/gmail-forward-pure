package io.pisek.gmailforward.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonParser {

    private final String input;
    private int pos;

    public JsonParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    public static Object parse(String json) {
        return new JsonParser(json).parseValue();
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new RuntimeException("Unexpected end of JSON");
        }
        char c = input.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield parseNumber();
                }
                throw new RuntimeException("Unexpected character '" + c + "' at position " + pos);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // skip '{'
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (pos < input.length()) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') {
                pos++;
            } else {
                break;
            }
        }
        skipWhitespace();
        expect('}');
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        pos++; // skip '['
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (pos < input.length()) {
            list.add(parseValue());
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ',') {
                pos++;
            } else {
                break;
            }
        }
        skipWhitespace();
        expect(']');
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '"') {
                pos++;
                return sb.toString();
            }
            if (c == '\\') {
                pos++;
                if (pos >= input.length()) {
                    throw new RuntimeException("Unexpected end of string escape");
                }
                char esc = input.charAt(pos);
                switch (esc) {
                    case '"', '\\', '/' -> sb.append(esc);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = input.substring(pos + 1, pos + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                    }
                    default -> throw new RuntimeException("Unknown escape: \\" + esc);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        throw new RuntimeException("Unterminated string");
    }

    private Number parseNumber() {
        int start = pos;
        if (input.charAt(pos) == '-') pos++;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        boolean isFloat = false;
        if (pos < input.length() && input.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
        }
        String num = input.substring(start, pos);
        if (isFloat) {
            return Double.parseDouble(num);
        }
        long val = Long.parseLong(num);
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
            return (int) val;
        }
        return val;
    }

    private Boolean parseBoolean() {
        if (input.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (input.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new RuntimeException("Expected boolean at position " + pos);
    }

    private Object parseNull() {
        if (input.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new RuntimeException("Expected null at position " + pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private void expect(char c) {
        if (pos >= input.length() || input.charAt(pos) != c) {
            throw new RuntimeException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }
}
