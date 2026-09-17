package dev.tore.schemaforge.core;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Hotbar slots the printer may use (P2-05, setting {@code allowedHotbarSlots}). The text numbers slots like the
 * number keys, 1–9, e.g. {@code "2-8"} or {@code "1,3,5-7"}; {@link #indices()} holds inventory indices 0–8.
 */
public record HotbarSlots(Set<Integer> indices) {
    public HotbarSlots {
        if (indices.isEmpty()) throw new IllegalArgumentException("no hotbar slot allowed");
        for (int index : indices) {
            if (index < 0 || index > 8) throw new IllegalArgumentException("hotbar index out of range: " + index);
        }
        indices = Collections.unmodifiableSet(new TreeSet<>(indices));
    }

    /** Parses a comma-separated list of slot numbers 1–9 and ranges {@code a-b}; spaces are ignored. */
    public static HotbarSlots parse(String text) {
        Set<Integer> indices = new TreeSet<>();
        for (String part : text.replace(" ", "").split(",")) {
            if (part.isEmpty()) continue;
            String[] bounds = part.split("-", -1);
            if (bounds.length > 2) throw new IllegalArgumentException("invalid range: " + part);
            int from = slotNumber(bounds[0], part);
            int to = bounds.length == 2 ? slotNumber(bounds[1], part) : from;
            if (from > to) throw new IllegalArgumentException("descending range: " + part);
            for (int n = from; n <= to; n++) indices.add(n - 1);
        }
        return new HotbarSlots(indices);
    }

    public boolean allows(int index) {
        return indices.contains(index);
    }

    private static int slotNumber(String text, String part) {
        try {
            int n = Integer.parseInt(text);
            if (n < 1 || n > 9) throw new IllegalArgumentException("hotbar slot must be 1-9: " + part);
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid hotbar slot: " + part, e);
        }
    }
}
