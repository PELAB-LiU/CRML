// Bundles the extension, or with --test the unit tests, into plain CommonJS.
// --production minifies and leaves out source maps, for packaging.
import { build } from "esbuild";
import { readdirSync } from "node:fs";

const production = process.argv.includes("--production");
const common = {
    bundle: true,
    platform: "node",
    format: "cjs",
    target: "node20",
    sourcemap: !production,
    minify: production,
    logLevel: "warning",
};

if (process.argv.includes("--test")) {
    await build({
        ...common,
        entryPoints: readdirSync("test").filter(f => f.endsWith(".test.ts")).map(f => `test/${f}`),
        outdir: "dist-test",
    });
} else {
    await build({
        ...common,
        entryPoints: ["src/extension.ts"],
        outfile: "dist/extension.js",
        external: ["vscode"],
    });
}
