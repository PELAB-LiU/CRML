import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const manifest = JSON.parse(readFileSync(join(__dirname, "..", "package.json"), "utf8"));

test("CRML completion is asked for explicitly, with Ctrl+Space", () => {
    const crml = manifest.contributes.configurationDefaults["[crml]"];
    assert.deepEqual(crml["editor.quickSuggestions"], { other: "off", comments: "off", strings: "off" });
    assert.equal(crml["editor.suggestOnTriggerCharacters"], false);
    assert.equal(crml["editor.wordBasedSuggestions"], "off", "no words of the document mixed in");
});
