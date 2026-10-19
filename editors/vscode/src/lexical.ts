// Lexical highlighting: runs the ANTLR lexer generated from crml.g4 and turns
// its tokens into highlight ranges. Free of the VS Code API so it can be
// tested under plain Node.
import { CharStream, Token } from "antlr4ng";
import { crmlLexer } from "./generated/crmlLexer.js";
import { BOOLEAN_VALUES, BUILTIN_TYPES } from "./generated/grammarInfo.js";

/**
 * Token kinds: standard LSP semantic token types, plus userOperator for
 * quoted operators such as 'during', declared in package.json as a subtype
 * of operator. Following the CRML publication style, numbers, symbols and
 * boolean values are left uncoloured.
 */
export const TOKEN_TYPES = ["keyword", "type", "userOperator", "string", "comment"] as const;
export type TokenType = typeof TOKEN_TYPES[number];

/** A highlight range within a single line. Columns count UTF-16 code units, as LSP and VS Code do. */
export interface LexicalToken {
    line: number;
    character: number;
    length: number;
    type: TokenType;
}

/** Maps each lexer token type to a highlight kind; undefined leaves the token uncoloured. */
const kindOf: (TokenType | undefined)[] = (() => {
    const builtinTypes = new Set(BUILTIN_TYPES);
    const booleanValues = new Set(BOOLEAN_VALUES);
    const vocabulary = crmlLexer.literalNames;
    const kinds: (TokenType | undefined)[] = [];
    for (let type = 0; type < vocabulary.length; type++) {
        const literal = vocabulary[type];
        if (!literal) continue;
        const text = literal.slice(1, -1); // literal names are quoted: 'model'
        if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(text) && !booleanValues.has(text)) {
            kinds[type] = builtinTypes.has(text) ? "type" : "keyword";
        }
    }
    kinds[crmlLexer.USER_KEYWORD] = "userOperator";
    kinds[crmlLexer.STRING] = "string";
    kinds[crmlLexer.LINE_COMMENT] = "comment";
    kinds[crmlLexer.ML_COMMENT] = "comment";
    return kinds;
})();

/** Returns the highlight ranges of a CRML document, sorted by position. */
export function lexicalTokens(text: string): LexicalToken[] {
    const lexer = new crmlLexer(CharStream.fromString(text));
    lexer.removeErrorListeners(); // unknown characters are simply skipped

    const offsets = new Utf16Offsets(text);
    const lines = new LineIndex(text);
    const result: LexicalToken[] = [];

    for (let token = lexer.nextToken(); token.type !== Token.EOF; token = lexer.nextToken()) {
        const type = kindOf[token.type];
        if (!type) continue;
        // Token indices count code points; LSP positions count UTF-16 units.
        const start = offsets.of(token.start);
        const end = offsets.of(token.stop + 1);
        // Semantic tokens cannot span lines, so split block comments and the
        // like, leaving out the line breaks themselves.
        for (let line = lines.lineAt(start); line < lines.count && lines.start(line) < end; line++) {
            const from = Math.max(start, lines.start(line));
            const to = Math.min(end, lines.contentEnd(line));
            if (to > from) {
                result.push({ line, character: from - lines.start(line), length: to - from, type });
            }
        }
    }
    return result;
}

/** Converts code point indices to UTF-16 offsets; the identity unless the text has surrogate pairs. */
class Utf16Offsets {
    private readonly table?: Uint32Array;

    constructor(text: string) {
        if (!/[\uD800-\uDBFF]/.test(text)) return;
        const table: number[] = [];
        let offset = 0;
        for (const ch of text) {
            table.push(offset);
            offset += ch.length;
        }
        table.push(offset);
        this.table = Uint32Array.from(table);
    }

    of(codePointIndex: number): number {
        return this.table ? this.table[codePointIndex] : codePointIndex;
    }
}

/** Line starts and ends in UTF-16 offsets, with the grammar's line breaks: \r\n, \n or \r. */
class LineIndex {
    private readonly starts: number[] = [0];
    private readonly ends: number[] = [];

    constructor(text: string) {
        const lineBreak = /\r\n|\n|\r/g;
        for (let m = lineBreak.exec(text); m; m = lineBreak.exec(text)) {
            this.ends.push(m.index);
            this.starts.push(m.index + m[0].length);
        }
        this.ends.push(text.length);
    }

    get count(): number { return this.starts.length; }
    start(line: number): number { return this.starts[line]; }
    contentEnd(line: number): number { return this.ends[line]; }

    lineAt(offset: number): number {
        let low = 0;
        let high = this.starts.length - 1;
        while (low < high) {
            const mid = (low + high + 1) >> 1;
            if (this.starts[mid] <= offset) low = mid; else high = mid - 1;
        }
        return low;
    }
}
