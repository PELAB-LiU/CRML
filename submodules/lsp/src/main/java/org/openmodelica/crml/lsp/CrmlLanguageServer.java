package org.openmodelica.crml.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The CRML language server: protocol lifecycle and the services it offers. */
public class CrmlLanguageServer implements LanguageServer, LanguageClientAware {
    public static final String NAME = "CRML Language Server";
    /**
     * Initialization option set by clients that highlight CRML themselves,
     * like the VS Code extension. The server then offers no semantic tokens,
     * which would only repeat the client's.
     */
    public static final String CLIENT_HIGHLIGHTING = "clientHighlighting";

    private final CrmlTextDocumentService documents = new CrmlTextDocumentService();
    private final CrmlWorkspaceService workspace = new CrmlWorkspaceService();
    private boolean shutdownRequested;

    @Override
    public void connect(LanguageClient client) {
        documents.connect(client);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        // Each change sends the whole document; analysis re-reads it anyway.
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setDocumentSymbolProvider(true);
        // Asked for explicitly (Ctrl+Space): no trigger characters.
        capabilities.setCompletionProvider(new CompletionOptions(true, null));
        documents.support(clientSupport(params));
        if (!option(params, CLIENT_HIGHLIGHTING)) {
            SemanticTokensWithRegistrationOptions tokens = new SemanticTokensWithRegistrationOptions(SemanticTokensProvider.LEGEND);
            tokens.setFull(true);
            capabilities.setSemanticTokensProvider(tokens);
        }

        String version = CrmlLanguageServer.class.getPackage().getImplementationVersion();
        return CompletableFuture.completedFuture(
            new InitializeResult(capabilities, new ServerInfo(NAME, version)));
    }

    /** What the client's completion can show, from its capabilities. */
    private static CompletionEngine.ClientSupport clientSupport(InitializeParams params) {
        CompletionItemCapabilities item = null;
        if (params.getCapabilities() != null && params.getCapabilities().getTextDocument() != null
            && params.getCapabilities().getTextDocument().getCompletion() != null) {
            item = params.getCapabilities().getTextDocument().getCompletion().getCompletionItem();
        }
        if (item == null) return new CompletionEngine.ClientSupport(false, false, false);
        return new CompletionEngine.ClientSupport(
            Boolean.TRUE.equals(item.getSnippetSupport()),
            Boolean.TRUE.equals(item.getLabelDetailsSupport()),
            item.getDocumentationFormat() != null && item.getDocumentationFormat().contains(MarkupKind.MARKDOWN));
    }

    private static boolean option(InitializeParams params, String name) {
        Object options = params.getInitializationOptions();
        if (!(options instanceof JsonObject)) return false;
        JsonElement value = ((JsonObject) options).get(name);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdownRequested = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // The protocol asks for exit code 1 when exit comes without shutdown.
        System.exit(shutdownRequested ? 0 : 1);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return documents;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspace;
    }
}
