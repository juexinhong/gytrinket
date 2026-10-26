package com.gytrinket.gytrinket.observer;

import java.lang.reflect.RecordComponent;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P5 诊断用的稳定 JSON 输出器（仅 observer 开发模组使用）。
 * <p>
 * 规则：Map 按 key 排序、Set 按序列化后的字符串排序、List 保持顺序、record 按字段名排序。
 * 同一份运行时状态必须得到逐字节相同的输出，才能用 sha256 做 golden 对照。
 */
final class P5Dump {

    private P5Dump() {
    }

    static String canonical(Object value) {
        StringBuilder sb = new StringBuilder(1 << 16);
        append(sb, value);
        return sb.toString();
    }

    static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof CharSequence text) {
            quote(sb, text.toString());
            return;
        }
        if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
            return;
        }
        if (value instanceof Enum<?> enumValue) {
            quote(sb, enumValue.name());
            return;
        }
        if (value instanceof Map<?, ?> map) {
            appendMap(sb, map);
            return;
        }
        if (value instanceof Set<?> set) {
            appendSet(sb, set);
            return;
        }
        if (value instanceof Collection<?> collection) {
            appendCollection(sb, collection);
            return;
        }
        if (value.getClass().isRecord()) {
            appendRecord(sb, value);
            return;
        }
        quote(sb, String.valueOf(value));
    }

    private static void appendMap(StringBuilder sb, Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : entries) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            append(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void appendSet(StringBuilder sb, Set<?> set) {
        List<String> encoded = new ArrayList<>(set.size());
        for (Object element : set) {
            StringBuilder elementSb = new StringBuilder();
            append(elementSb, element);
            encoded.add(elementSb.toString());
        }
        encoded.sort(Comparator.naturalOrder());
        sb.append('[');
        for (int i = 0; i < encoded.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(encoded.get(i));
        }
        sb.append(']');
    }

    private static void appendCollection(StringBuilder sb, Collection<?> collection) {
        sb.append('[');
        boolean first = true;
        for (Object element : collection) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            append(sb, element);
        }
        sb.append(']');
    }

    private static void appendRecord(StringBuilder sb, Object record) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Arrays.sort(components, Comparator.comparing(RecordComponent::getName));
        sb.append('{');
        boolean first = true;
        for (RecordComponent component : components) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, component.getName());
            sb.append(':');
            try {
                append(sb, component.getAccessor().invoke(record));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法读取 record 字段: " + component.getName(), e);
            }
        }
        sb.append('}');
    }

    private static void quote(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
