// Finds a Java runtime that can run the language server. Free of the VS Code
// API so it can be tested under plain Node.
import { execFile } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";

/** The language server needs Java 11, because LSP4J 1.0 does. */
export const MINIMUM_JAVA_VERSION = 11;

export interface JavaCandidate {
    executable: string;
    /** Where the candidate came from, for messages: "JAVA_HOME", "the PATH", ... */
    source: string;
}

export interface Java extends JavaCandidate {
    version: number;
}

const JAVA = process.platform === "win32" ? "java.exe" : "java";

/**
 * The places to look for Java, in order. An explicit crml.java.home setting
 * is the only candidate, so that a wrong setting is reported, not bypassed.
 * Otherwise: a runtime bundled with the extension, JAVA_HOME, then the PATH.
 */
export function javaCandidates(javaHomeSetting: string | undefined, extensionPath: string,
                               env: NodeJS.ProcessEnv): JavaCandidate[] {
    if (javaHomeSetting) {
        return [{ executable: join(javaHomeSetting, "bin", JAVA), source: "the crml.java.home setting" }];
    }
    const candidates: JavaCandidate[] = [];
    const bundled = join(extensionPath, "jre", "bin", JAVA);
    if (existsSync(bundled)) {
        candidates.push({ executable: bundled, source: "the runtime bundled with the extension" });
    }
    if (env.JAVA_HOME) {
        candidates.push({ executable: join(env.JAVA_HOME, "bin", JAVA), source: "JAVA_HOME" });
    }
    candidates.push({ executable: JAVA, source: "the PATH" });
    return candidates;
}

/** The major version in `java -version` output: 8 for "1.8.0_504", 11 for "11.0.32". */
export function parseJavaVersion(output: string): number | undefined {
    const match = /version "(\d+)(?:\.(\d+))?/.exec(output);
    if (!match) return undefined;
    return match[1] === "1" && match[2] !== undefined ? Number(match[2]) : Number(match[1]);
}

/** Runs `java -version`; undefined when the executable cannot be run. */
export function probeJavaVersion(executable: string): Promise<number | undefined> {
    return new Promise(resolve => {
        execFile(executable, ["-version"], { timeout: 10_000 }, (error, stdout, stderr) => {
            resolve(error ? undefined : parseJavaVersion(stderr + stdout));
        });
    });
}

/** The first candidate that runs and is recent enough; throws with what was tried otherwise. */
export async function findJava(candidates: JavaCandidate[],
                               probe: (executable: string) => Promise<number | undefined> = probeJavaVersion): Promise<Java> {
    const rejected: string[] = [];
    for (const candidate of candidates) {
        const version = await probe(candidate.executable);
        if (version === undefined) {
            rejected.push(`${candidate.executable} (from ${candidate.source}) could not be run`);
        } else if (version < MINIMUM_JAVA_VERSION) {
            rejected.push(`${candidate.executable} (from ${candidate.source}) is Java ${version}`);
        } else {
            return { ...candidate, version };
        }
    }
    throw new Error(`The CRML language server needs Java ${MINIMUM_JAVA_VERSION} or later. ${rejected.join("; ")}.`);
}
