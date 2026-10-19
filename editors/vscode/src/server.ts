import { existsSync } from "node:fs";
import * as vscode from "vscode";
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from "vscode-languageclient/node";
import { findJava, javaCandidates } from "./java.js";

/**
 * Starts the CRML language server, or returns undefined after telling the
 * user why it could not start. Highlighting works without it.
 */
export async function startLanguageServer(context: vscode.ExtensionContext,
                                          output: vscode.LogOutputChannel): Promise<LanguageClient | undefined> {
    const jar = context.asAbsolutePath("server/crml-language-server.jar");
    if (!existsSync(jar)) {
        output.warn(`Language server not found at ${jar}; only highlighting is available.`);
        return undefined;
    }

    const javaHome = vscode.workspace.getConfiguration("crml").get<string | null>("java.home") ?? undefined;
    let java;
    try {
        java = await findJava(javaCandidates(javaHome, context.extensionPath, process.env));
    } catch (error) {
        const message = (error as Error).message;
        output.error(message);
        vscode.window.showErrorMessage(message, "Set crml.java.home").then(choice => {
            if (choice) vscode.commands.executeCommand("workbench.action.openSettings", "crml.java.home");
        });
        return undefined;
    }
    output.info(`Starting the language server with Java ${java.version} from ${java.source}: ${java.executable}`);

    const serverOptions: ServerOptions = {
        run: { command: java.executable, args: ["-jar", jar], transport: TransportKind.stdio },
        // When the extension itself is debugged, a Java debugger can attach on
        // port 5005. quiet=y keeps the JDWP banner off stdout, the protocol channel.
        debug: {
            command: java.executable,
            args: ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,quiet=y,address=localhost:5005", "-jar", jar],
            transport: TransportKind.stdio,
        },
    };
    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ language: "crml" }],
        outputChannel: output,
        // The extension highlights from its own lexer, instantly; the server's
        // tokens would only repeat that. Merging comes back once the server
        // knows more than the lexer, such as what an identifier refers to.
        initializationOptions: { clientHighlighting: true },
    };

    // The id "crml" also names the crml.trace.server setting.
    const client = new LanguageClient("crml", "CRML Language Server", serverOptions, clientOptions);
    await client.start();
    return client;
}
