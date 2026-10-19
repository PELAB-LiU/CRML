import assert from "node:assert/strict";
import { join } from "node:path";
import { test } from "node:test";
import { findJava, javaCandidates, parseJavaVersion } from "../src/java.js";

const JAVA = process.platform === "win32" ? "java.exe" : "java";

test("reads the major version of old and new java -version output", () => {
    assert.equal(parseJavaVersion('java version "1.8.0_504"\nJava(TM) SE Runtime Environment'), 8);
    assert.equal(parseJavaVersion('openjdk version "11.0.32.1" 2026-08-18'), 11);
    assert.equal(parseJavaVersion('openjdk version "21" 2023-09-19'), 21);
    assert.equal(parseJavaVersion("no version here"), undefined);
});

test("uses only crml.java.home when it is set", () => {
    assert.deepEqual(javaCandidates("/opt/jdk", "/ext", { JAVA_HOME: "/usr/lib/jvm/x" }),
        [{ executable: join("/opt/jdk", "bin", JAVA), source: "the crml.java.home setting" }]);
});

test("falls back to JAVA_HOME, then the PATH", () => {
    assert.deepEqual(javaCandidates(undefined, "/no/such/extension", { JAVA_HOME: "/usr/lib/jvm/x" }).map(c => c.source),
        ["JAVA_HOME", "the PATH"]);
    assert.deepEqual(javaCandidates(undefined, "/no/such/extension", {}).map(c => c.source), ["the PATH"]);
});

test("skips Java that is missing or too old, and says so when nothing fits", async () => {
    const versions: Record<string, number | undefined> = { old: 8, missing: undefined, good: 17 };
    const probe = async (executable: string) => versions[executable];
    const candidate = (executable: string) => ({ executable, source: executable });

    const java = await findJava([candidate("old"), candidate("missing"), candidate("good")], probe);
    assert.equal(java.executable, "good");
    assert.equal(java.version, 17);

    await assert.rejects(findJava([candidate("old"), candidate("missing")], probe),
        /needs Java 11 or later\. old \(from old\) is Java 8; missing \(from missing\) could not be run\./);
});
