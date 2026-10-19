import * as vscode from "vscode";
import { lexicalTokens, TOKEN_TYPES } from "./lexical.js";

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

export function activate(context: vscode.ExtensionContext): void {
    context.subscriptions.push(vscode.languages.registerDocumentSemanticTokensProvider(
        { language: "crml" }, new LexicalSemanticTokensProvider(), legend));
}

export function deactivate(): void {}
