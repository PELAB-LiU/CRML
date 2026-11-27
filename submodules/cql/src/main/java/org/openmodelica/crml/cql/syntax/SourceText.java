package org.openmodelica.crml.cql.syntax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.antlr.v4.runtime.Token;

/**
 * The text of a source, and the {@link Span}s of tokens in it.
 *
 * <p>ANTLR counts in code points, {@link Span} in UTF-16 code units; they differ after
 * a character outside the Basic Multilingual Plane (an emoji in a string). Lines end at
 * {@code \n}, {@code \r\n} or a lone {@code \r}.
 */
final class SourceText {

    private final String text;
    /** The UTF-16 offset of the start of each line; the first is 0. */
    private final int[] lineStarts;
    /** The UTF-16 offset of each code point and of the end, or null if they are the same. */
    private final int[] codePointOffsets;

    SourceText(String text) {
        this.text = text;
        this.lineStarts = lineStarts(text);
        this.codePointOffsets = text.codePointCount(0, text.length()) == text.length()
            ? null
            : codePointOffsets(text);
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 == text.length() || text.charAt(i + 1) != '\n'))) {
                starts.add(i + 1);
            }
        }
        int[] result = new int[starts.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = starts.get(i);
        }
        return result;
    }

    private static int[] codePointOffsets(String text) {
        int[] offsets = new int[text.codePointCount(0, text.length()) + 1];
        int offset = 0;
        for (int i = 0; i < offsets.length - 1; i++) {
            offsets[i] = offset;
            offset += Character.charCount(text.codePointAt(offset));
        }
        offsets[offsets.length - 1] = offset;
        return offsets;
    }

    String text() {
        return text;
    }

    /** The UTF-16 offset of the code point at {@code index} (ANTLR's character index). */
    int offsetOfCodePoint(int index) {
        return codePointOffsets == null ? index : codePointOffsets[index];
    }

    /** The span of {@code length} UTF-16 code units from {@code offset}. */
    Span span(int offset, int length) {
        int lineIndex = Arrays.binarySearch(lineStarts, offset);
        if (lineIndex < 0) {
            lineIndex = -lineIndex - 2;
        }
        return new Span(offset, length, lineIndex + 1, offset - lineStarts[lineIndex] + 1);
    }

    /** The span of {@code token}; the end of the text for EOF. */
    Span span(Token token) {
        if (token.getType() == Token.EOF) {
            return span(text.length(), 0);
        }
        int start = offsetOfCodePoint(token.getStartIndex());
        int end = offsetOfCodePoint(token.getStopIndex() + 1);
        return span(start, end - start);
    }

    /** The span from the start of {@code first} to the end of {@code last}. */
    Span span(Token first, Token last) {
        Span start = span(first);
        Span end = span(last);
        return span(start.offset(), Math.max(end.end(), start.offset()) - start.offset());
    }
}
