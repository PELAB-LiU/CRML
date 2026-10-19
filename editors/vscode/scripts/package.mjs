// Packages the extension as a .vsix in dist-vsix/.
//
//   node scripts/package.mjs                     universal: uses the Java on the user's machine
//   node scripts/package.mjs --target linux-x64  for one platform, with a Java runtime bundled
//
// For a platform package, jlink builds a Java runtime with only the modules
// the language server needs, from the Temurin jmods for that platform. Since
// Java 24, JDKs come without jmods, so Adoptium publishes them separately.
// jlink and jdeps come from a Temurin JDK of the same version for the build
// machine, as jlink must match the jmods it links. Downloads are checked
// against their SHA-256 and cached in .cache/.
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const JAVA_VERSION = 25; // the most recent LTS; the server needs 11+

/**
 * Modules jdeps lists that the server never loads, left out of the runtime.
 * java.desktop (about a third of the runtime) is referenced only by
 * commons-lang3's AbstractCircuitBreaker, for java.beans, which CRML does not use.
 */
const UNUSED_MODULES = new Set(["java.desktop"]);

/** VS Code platform targets and their Adoptium os/architecture. */
const PLATFORMS = {
    "linux-x64": ["linux", "x64"],
    "linux-arm64": ["linux", "aarch64"],
    "alpine-x64": ["alpine-linux", "x64"],
    "darwin-x64": ["mac", "x64"],
    "darwin-arm64": ["mac", "aarch64"],
    "win32-x64": ["windows", "x64"],
    "win32-arm64": ["windows", "aarch64"],
};

const root = fileURLToPath(new URL("..", import.meta.url));
const repo = join(root, "../..");
const jar = join(root, "server/crml-language-server.jar");
const jre = join(root, "jre");

const targetIndex = process.argv.indexOf("--target");
const target = targetIndex < 0 ? undefined : process.argv[targetIndex + 1];
if (target !== undefined && !PLATFORMS[target]) {
    throw new Error(`Unknown target ${target}; one of ${Object.keys(PLATFORMS).join(", ")}`);
}

const run = (command, args) => execFileSync(command, args, { stdio: "inherit", cwd: root });

run("node", ["scripts/build-server.mjs"]);
run("npm", ["run", "generate"]);
run("node", ["esbuild.mjs", "--production"]);
// vsce takes license and notices from the extension folder.
copyFileSync(join(repo, "OSMC-License.txt"), join(root, "OSMC-License.txt"));
copyFileSync(join(repo, "THIRD-PARTY-NOTICES.md"), join(root, "THIRD-PARTY-NOTICES.md"));

rmSync(jre, { recursive: true, force: true });
if (target) {
    const jdkBin = dirname(findFile(await download(hostPlatform(), "jdk"), exe("jlink")));
    const jmods = dirname(findFile(await download(target, "jmods"), "java.base.jmod"));
    const modules = execFileSync(join(jdkBin, exe("jdeps")),
        ["--ignore-missing-deps", "--print-module-deps", "--multi-release", String(JAVA_VERSION), jar],
        { encoding: "utf8" }).trim().split(",").filter(module => !UNUSED_MODULES.has(module));
    // jdk.jdwp.agent lets a debugger attach when the extension is debugged.
    modules.push("jdk.jdwp.agent");
    console.log(`Linking a Java ${JAVA_VERSION} runtime for ${target} with ${modules.join(",")}`);
    // --strip-debug also strips native binaries, with tools that only handle
    // the build machine's format; for other platforms strip Java debug data only.
    const linuxTarget = PLATFORMS[target][0].endsWith("linux");
    const strip = process.platform === "linux" && linuxTarget ? "--strip-debug" : "--strip-java-debug-attributes";
    run(join(jdkBin, exe("jlink")), [
        "--module-path", jmods,
        "--add-modules", modules.join(","),
        strip, "--no-header-files", "--no-man-pages", "--compress", "zip-6",
        "--output", jre,
    ]);
}

const version = JSON.parse(readFileSync(join(root, "package.json"), "utf8")).version;
mkdirSync(join(root, "dist-vsix"), { recursive: true });
const vsix = join(root, "dist-vsix", `crml-${version}${target ? `-${target}` : ""}.vsix`);
run("npx", ["vsce", "package", "--no-dependencies", ...(target ? ["--target", target] : []), "--out", vsix]);
rmSync(jre, { recursive: true, force: true });
console.log(`${vsix}: ${(statSync(vsix).size / 1e6).toFixed(1)} MB`);

function hostPlatform() {
    const platform = `${process.platform}-${process.arch}`;
    if (!PLATFORMS[platform]) throw new Error(`No JDK for building on ${platform}`);
    return platform;
}

function exe(name) {
    return process.platform === "win32" ? `${name}.exe` : name;
}

/** Downloads and unpacks a Temurin image (jdk or jmods) for a platform, once; returns its folder. */
async function download(platform, imageType) {
    const [os, architecture] = PLATFORMS[platform];
    const folder = join(root, ".cache", "temurin", `${JAVA_VERSION}-${imageType}-${platform}`);
    const done = join(folder, ".complete");
    if (existsSync(done)) return folder;

    const assets = await (await fetch(`https://api.adoptium.net/v3/assets/latest/${JAVA_VERSION}/hotspot`
        + `?os=${os}&architecture=${architecture}&image_type=${imageType}&vendor=eclipse`)).json();
    if (!assets.length) throw new Error(`No Temurin ${JAVA_VERSION} ${imageType} for ${platform}`);
    const { link, checksum, name } = assets[0].binary.package;
    console.log(`Downloading ${name}`);
    const archive = Buffer.from(await (await fetch(link)).arrayBuffer());
    const actual = createHash("sha256").update(archive).digest("hex");
    if (actual !== checksum) throw new Error(`Checksum mismatch for ${name}: ${actual}, expected ${checksum}`);

    rmSync(folder, { recursive: true, force: true });
    mkdirSync(folder, { recursive: true });
    const file = join(folder, name);
    writeFileSync(file, archive);
    if (name.endsWith(".zip")) run("unzip", ["-q", file, "-d", folder]);
    else run("tar", ["-xzf", file, "-C", folder]);
    rmSync(file);
    writeFileSync(done, "");
    return folder;
}

/** The first file with the given name below a folder, breadth first. */
function findFile(folder, name) {
    const queue = [folder];
    while (queue.length) {
        const current = queue.shift();
        for (const entry of readdirSync(current, { withFileTypes: true })) {
            const path = join(current, entry.name);
            if (entry.isDirectory()) queue.push(path);
            else if (entry.name === name) return path;
        }
    }
    throw new Error(`No ${name} in ${folder}`);
}
