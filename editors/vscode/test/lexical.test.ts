import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { lexicalTokens, LexicalToken } from "../src/lexical.js";

const TEST_MODELS = join(__dirname, "../../../submodules/test-resources/src/main/resources/testModels");

/** Renders tokens as "line:character type 'text'" for readable assertions. */
function render(text: string, tokens: LexicalToken[]): string[] {
    const lines = text.split(/\r\n|\n|\r/);
    return tokens.map(t =>
        `${t.line}:${t.character} ${t.type} ${lines[t.line].substring(t.character, t.character + t.length)}`);
}

test("colours the SRI example like the CRML publications", () => {
    const text = [
        "model SRI is FORM_L union {",
        "  Boolean normal_operation is external;",
        "  Real T is external;",
        "  // Req. 1: During normal operation, the system must maintain a temperature between 16°C to 30°C.",
        "  Boolean T_in_range is ((T >= 16) and (T <= 30));",
        "  Boolean req1 is ('during' normal_operation) 'ensure' T_in_range;",
        "",
        "  class Req_speed is {",
        "    Real v is external;",
        "    //Req. 3: To limit erosion and corrosion, the fluid velocity in the heat exchangers shall not exceed 6 m/s.",
        "    Boolean req3 is ('during' true) 'ensure' (v > 6.0);",
        "  }",
        "};",
    ].join("\n");
    // Identifiers, numbers, symbols and true stay uncoloured.
    assert.deepEqual(render(text, lexicalTokens(text)).map(t => t.replace(/^(\d+):\d+/, "$1")), [
        "0 keyword model", "0 keyword is", "0 keyword union",
        "1 type Boolean", "1 keyword is", "1 keyword external",
        "2 type Real", "2 keyword is", "2 keyword external",
        "3 comment // Req. 1: During normal operation, the system must maintain a temperature between 16°C to 30°C.",
        "4 type Boolean", "4 keyword is", "4 keyword and",
        "5 type Boolean", "5 keyword is", "5 userOperator 'during'", "5 userOperator 'ensure'",
        "7 keyword class", "7 keyword is",
        "8 type Real", "8 keyword is", "8 keyword external",
        "9 comment //Req. 3: To limit erosion and corrosion, the fluid velocity in the heat exchangers shall not exceed 6 m/s.",
        "10 type Boolean", "10 keyword is", "10 userOperator 'during'", "10 userOperator 'ensure'",
    ]);
});

test("keeps strings coloured", () => {
    const text = "String s is \"text\";";
    assert.deepEqual(render(text, lexicalTokens(text)), [
        "0:0 type String",
        "0:9 keyword is",
        "0:12 string \"text\"",
    ]);
});

test("splits block comments at line breaks of every kind", () => {
    const text = "/* a\r\nbb\rc */ model";
    assert.deepEqual(render(text, lexicalTokens(text)), [
        "0:0 comment /* a",
        "1:0 comment bb",
        "2:0 comment c */",
        "2:5 keyword model",
    ]);
});

test("counts columns in UTF-16 code units", () => {
    const text = "/* \u{1F600} */ model";
    assert.deepEqual(lexicalTokens(text), [
        { line: 0, character: 0, length: 8, type: "comment" },
        { line: 0, character: 9, length: 5, type: "keyword" },
    ]);
});

test("produces well-formed tokens for every test model", () => {
    const files = readdirSync(TEST_MODELS, { recursive: true, encoding: "utf8" })
        .filter(f => f.endsWith(".crml"));
    assert.ok(files.length > 100, `expected the test corpus, found ${files.length} files`);

    for (const file of files) {
        const text = readFileSync(join(TEST_MODELS, file), "utf8");
        const lines = text.split(/\r\n|\n|\r/);
        const tokens = lexicalTokens(text);
        assert.ok(tokens.some(t => t.type === "keyword"), `${file}: no keywords`);
        let previous = { line: 0, end: 0 };
        for (const t of tokens) {
            const where = `${file} ${t.line}:${t.character}`;
            assert.ok(t.length > 0, `${where}: empty token`);
            assert.ok(t.character + t.length <= lines[t.line].length, `${where}: past end of line`);
            assert.ok(t.line > previous.line || t.character >= previous.end, `${where}: overlaps or out of order`);
            previous = { line: t.line, end: t.character + t.length };
        }
    }
});
