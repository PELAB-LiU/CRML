package crml.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/** The CRML language server: protocol lifecycle and the services it offers. */
public class CrmlLanguageServer implements LanguageServer, LanguageClientAware {
    public static final String NAME = "CRML Language Server";

    private final CrmlTextDocumentService documents = new CrmlTextDocumentService();
    private final CrmlWorkspaceService workspace = new CrmlWorkspaceService();
    private LanguageClient client;
    private boolean shutdownRequested;

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        // Each change sends the whole document; analysis re-reads it anyway.
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        String version = CrmlLanguageServer.class.getPackage().getImplementationVersion();
        return CompletableFuture.completedFuture(
            new InitializeResult(capabilities, new ServerInfo(NAME, version)));
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
