import * as vscode from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import { lexicalTokens, TOKEN_TYPES } from "./lexical.js";
import { startLanguageServer } from "./server.js";

const legend = new vscode.SemanticTokensLegend([...TOKEN_TYPES], []);

/**
 * Highlights CRML from the ANTLR lexer, in-process, so colour appears
 * immediately and does not depend on the language server.
 */
class LexicalSemanticTokensProvider implements vscode.DocumentSemanticTokensProvider {
    provideDocumentSemanticTokens(document: vscode.TextDocument): vscode.SemanticTokens {
        const builder = new vscode.SemanticTokensBuilder(legend);
        for (const token of lexicalTokens(document.getText())) {
            builder.push(token.line, token.character, token.length, TOKEN_TYPES.indexOf(token.type));
        }
        return builder.build();
    }
}

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext): void {
    context.subscriptions.push(vscode.languages.registerDocumentSemanticTokensProvider(
        { language: "crml" }, new LexicalSemanticTokensProvider(), legend));

    const output = vscode.window.createOutputChannel("CRML Language Server", { log: true });
    context.subscriptions.push(output);

    const start = async () => {
        try {
            client = await startLanguageServer(context, output);
        } catch (error) {
            output.error(`The language server failed to start: ${(error as Error).message}`);
            output.show(true);
        }
    };
    context.subscriptions.push(vscode.commands.registerCommand("crml.restartLanguageServer", async () => {
        await client?.stop();
        client = undefined;
        await start();
    }));

    // Not awaited: highlighting is already registered and must not wait for the JVM.
    void start();
}

export function deactivate(): Thenable<void> | undefined {
    return client?.stop();
}
