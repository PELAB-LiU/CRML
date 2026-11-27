package org.openmodelica.crml.cql.syntax;

/**
 * Where a piece of text is in its source: offset and length in UTF-16 code units (the
 * units of {@link String} and of editors), and the line and column of its start,
 * both from 1. A tab counts as one column.
 *
 * <p>Positions are kept beside the syntax tree, not in it (frontend plan, F9).
 */
public final class Span {

    private final int offset;
    private final int length;
    private final int line;
    private final int column;

    public Span(int offset, int length, int line, int column) {
        if (offset < 0 || length < 0 || line < 1 || column < 1) {
            throw new IllegalArgumentException(
                "offset " + offset + ", length " + length + ", line " + line + ", column " + column);
        }
        this.offset = offset;
        this.length = length;
        this.line = line;
        this.column = column;
    }

    /** The offset of the first character, from 0, in UTF-16 code units. */
    public int offset() {
        return offset;
    }

    /** The number of UTF-16 code units. */
    public int length() {
        return length;
    }

    /** The offset just past the last character. */
    public int end() {
        return offset + length;
    }

    /** The line of the first character, from 1. */
    public int line() {
        return line;
    }

    /** The column of the first character, from 1. */
    public int column() {
        return column;
    }

    /** Whether {@code other} lies within this span. */
    public boolean contains(Span other) {
        return offset <= other.offset && other.end() <= end();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Span)) {
            return false;
        }
        Span other = (Span) obj;
        return offset == other.offset && length == other.length && line == other.line && column == other.column;
    }

    @Override
    public int hashCode() {
        return ((offset * 31 + length) * 31 + line) * 31 + column;
    }

    /** {@code line:column}, as in messages. */
    @Override
    public String toString() {
        return line + ":" + column;
    }
}
