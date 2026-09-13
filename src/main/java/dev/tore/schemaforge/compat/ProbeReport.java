package dev.tore.schemaforge.compat;

import java.util.Comparator;
import java.util.List;

/**
 * Result of {@link VersionProbe}, one {@link Section} per checked mod (P0-05).
 * Pure data without Minecraft dependencies; chat rendering lives in {@code commands/DoctorCommand}.
 */
public record ProbeReport(List<Section> sections) {
    public ProbeReport {
        sections = List.copyOf(sections);
    }

    /** Declared from best to worst; {@link #worst} relies on this order. */
    public enum Status {
        /** Present and working (green). */
        OK,
        /** Optional dependency not installed (yellow). */
        MISSING,
        /** Present but broken, e.g. a signature does not resolve (red). */
        FAIL
    }

    public record Line(String label, String value, Status status) {
        public static Line ok(String label, String value) {
            return new Line(label, value, Status.OK);
        }

        public static Line missing(String label, String value) {
            return new Line(label, value, Status.MISSING);
        }

        public static Line fail(String label, String value) {
            return new Line(label, value, Status.FAIL);
        }
    }

    public record Section(String title, List<Line> lines) {
        public Section {
            lines = List.copyOf(lines);
        }

        /** Worst status of all lines; an empty section is {@link Status#OK}. */
        public Status status() {
            return worst(lines.stream().map(Line::status).toList());
        }
    }

    /** Worst status of all sections. */
    public Status status() {
        return worst(sections.stream().map(Section::status).toList());
    }

    /** Number of lines that are not {@link Status#OK}. */
    public long problemCount() {
        return sections.stream().flatMap(s -> s.lines().stream()).filter(l -> l.status() != Status.OK).count();
    }

    private static Status worst(List<Status> statuses) {
        return statuses.stream().max(Comparator.naturalOrder()).orElse(Status.OK);
    }
}
