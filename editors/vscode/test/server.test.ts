import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { createMessageConnection, StreamMessageReader, StreamMessageWriter } from "vscode-jsonrpc/node";
import { findJava, javaCandidates } from "../src/java.js";

const EXTENSION = join(__dirname, "..");
const JAR = join(EXTENSION, "server/crml-language-server.jar"); // copied by npm run build:server

test("starts the server jar with the Java the extension would pick", { skip: !existsSync(JAR) && "no server jar; run npm run build:server" }, async () => {
    const java = await findJava(javaCandidates(undefined, EXTENSION, process.env));
    const server = spawn(java.executable, ["-jar", JAR], { stdio: ["pipe", "pipe", "inherit"] });
    const exited = new Promise<number | null>(resolve => server.on("exit", resolve));

    const connection = createMessageConnection(new StreamMessageReader(server.stdout), new StreamMessageWriter(server.stdin));
    connection.listen();
    const result = await connection.sendRequest<{ serverInfo: { name: string } }>("initialize",
        { processId: process.pid, rootUri: null, capabilities: {} });
    assert.equal(result.serverInfo.name, "CRML Language Server");
    await connection.sendNotification("initialized", {});
    await connection.sendRequest("shutdown");
    await connection.sendNotification("exit");

    assert.equal(await exited, 0);
    connection.dispose();
});
