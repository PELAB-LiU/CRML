// Dumps the Java lexer's tokens for the test corpus to dist-test/java-tokens.tsv,
// for the lexer parity test. Needs a JDK 11+ on the PATH.
import { execFileSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const here = (path) => fileURLToPath(new URL(path, import.meta.url));
const repo = here("../../../");
const corpus = here("../../../submodules/test-resources/src/main/resources/testModels");

const gradle = execFileSync(here("../../../gradlew"),
    ["-q", "-p", repo, "-I", here("language-classpath.gradle"), ":language:printLanguageClasspath"],
    { encoding: "utf8", stdio: ["ignore", "pipe", "inherit"] });
const classpath = /^CLASSPATH=(.*)$/m.exec(gradle)?.[1];
if (!classpath) {
    throw new Error(`Gradle printed no classpath:\n${gradle}`);
}

const tokens = execFileSync("java", ["-cp", classpath, here("../test/java/DumpTokens.java"), corpus],
    { encoding: "utf8", maxBuffer: 256 * 1024 * 1024, stdio: ["ignore", "pipe", "inherit"] });

mkdirSync(here("../dist-test"), { recursive: true });
writeFileSync(here("../dist-test/java-tokens.tsv"), tokens);
console.log(`Java lexer: ${tokens.split("\n").length - 1} tokens`);
