package crml.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Converts between ANTLR character indices, which count code points, and LSP
 * positions, which count UTF-16 code units within a line. Lines end at \r\n,
 * \n or \r, as in the grammar.
 */
public final class Positions {
    private final String text;
    /** UTF-16 offset of each code point index, or null when the text has no surrogate pairs. */
    private final int[] utf16Offsets;
    private final List<Integer> lineStarts = new ArrayList<>();
    private final List<Integer> lineEnds = new ArrayList<>();

    public Positions(String text) {
        this.text = text;
        this.utf16Offsets = text.codePoints().count() == text.length() ? null : utf16Offsets(text);
        lineStarts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                lineEnds.add(i);
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                lineStarts.add(i + 1);
            }
        }
        lineEnds.add(text.length());
    }

    private static int[] utf16Offsets(String text) {
        int[] offsets = new int[text.codePointCount(0, text.length()) + 1];
        int offset = 0;
        for (int i = 0; i < offsets.length - 1; i++) {
            offsets[i] = offset;
            offset += Character.charCount(text.codePointAt(offset));
        }
        offsets[offsets.length - 1] = offset;
        return offsets;
    }

    /** The UTF-16 offset of an ANTLR (code point) index, clamped to the text. */
    public int utf16Offset(int codePointIndex) {
        int index = Math.max(0, codePointIndex);
        if (utf16Offsets == null) return Math.min(index, text.length());
        return utf16Offsets[Math.min(index, utf16Offsets.length - 1)];
    }

    public int lineCount() {
        return lineStarts.size();
    }

    public int lineStart(int line) {
        return lineStarts.get(line);
    }

    /** The UTF-16 offset where the line's content ends, before its line break. */
    public int lineContentEnd(int line) {
        return lineEnds.get(line);
    }

    /** The line containing a UTF-16 offset. */
    public int lineAt(int utf16Offset) {
        int low = 0;
        int high = lineStarts.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lineStarts.get(mid) <= utf16Offset) low = mid; else high = mid - 1;
        }
        return low;
    }

    public Position positionOfOffset(int utf16Offset) {
        int line = lineAt(utf16Offset);
        return new Position(line, utf16Offset - lineStarts.get(line));
    }

    /** The LSP position of an ANTLR (code point) index. */
    public Position position(int codePointIndex) {
        return positionOfOffset(utf16Offset(codePointIndex));
    }

    /** The LSP range of the code points from start (inclusive) to end (exclusive). */
    public Range range(int start, int end) {
        return new Range(position(start), position(Math.max(start, end)));
    }

    /** The UTF-16 offset of an LSP position, clamped to its line. */
    public int offset(Position position) {
        int line = Math.min(Math.max(position.getLine(), 0), lineCount() - 1);
        return Math.min(lineStarts.get(line) + Math.max(position.getCharacter(), 0), lineEnds.get(line));
    }
}
