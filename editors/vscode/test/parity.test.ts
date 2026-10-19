import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { CharStream, Token } from "antlr4ng";
import { crmlLexer } from "../src/generated/crmlLexer.js";

const TEST_MODELS = join(__dirname, "../../../submodules/test-resources/src/main/resources/testModels");
const JAVA_TOKENS = join(__dirname, "java-tokens.tsv"); // written by scripts/java-tokens.mjs

/** The TypeScript lexer's tokens in the format of test/java/DumpTokens.java, without the file column. */
function typescriptTokens(text: string): string[] {
    const lexer = new crmlLexer(CharStream.fromString(text));
    lexer.removeErrorListeners();
    const lines: string[] = [];
    for (let token = lexer.nextToken(); token.type !== Token.EOF; token = lexer.nextToken()) {
        lines.push(`${lexer.vocabulary.getDisplayName(token.type)}\t${token.start}\t${token.stop}`);
    }
    return lines;
}

test("the TypeScript lexer matches the Java lexer on every test model", { skip: !existsSync(JAVA_TOKENS) && "no Java token dump; run npm run test:parity" }, () => {
    const javaTokens = new Map<string, string[]>();
    for (const line of readFileSync(JAVA_TOKENS, "utf8").split("\n")) {
        if (!line) continue;
        const tab = line.indexOf("\t");
        const file = line.substring(0, tab);
        if (!javaTokens.has(file)) javaTokens.set(file, []);
        javaTokens.get(file)!.push(line.substring(tab + 1));
    }
    assert.ok(javaTokens.size > 100, `expected the test corpus, found ${javaTokens.size} files`);

    for (const [file, expected] of javaTokens) {
        const actual = typescriptTokens(readFileSync(join(TEST_MODELS, file), "utf8"));
        const mismatch = expected.findIndex((token, i) => token !== actual[i]);
        assert.ok(mismatch < 0 && actual.length === expected.length,
            `${file}: token ${mismatch < 0 ? expected.length : mismatch} differs, ` +
            `Java "${expected[mismatch] ?? "<end>"}", TypeScript "${actual[mismatch < 0 ? expected.length : mismatch] ?? "<end>"}"`);
    }
});
