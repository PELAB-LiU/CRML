package crml.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Keeps the text of the open documents and answers questions about them.
 * Diagnostics are recomputed shortly after the last change, so that typing
 * does not trigger a full analysis on every keystroke.
 */
public class CrmlTextDocumentService implements TextDocumentService {
    static final long DIAGNOSTICS_DELAY_MS = 200;

    private final Map<String, String> open = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingDiagnostics = new ConcurrentHashMap<>();
    private final ScheduledExecutorService analysis = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "crml-analysis");
        thread.setDaemon(true);
        return thread;
    });
    private volatile LanguageClient client;

    void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        open.put(uri, params.getTextDocument().getText());
        scheduleDiagnostics(uri);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // Full sync: the last change holds the whole new text.
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (changes.isEmpty()) return;
        String uri = params.getTextDocument().getUri();
        open.put(uri, changes.get(changes.size() - 1).getText());
        scheduleDiagnostics(uri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        open.remove(uri);
        ScheduledFuture<?> pending = pendingDiagnostics.remove(uri);
        if (pending != null) pending.cancel(false);
        publish(uri, new ArrayList<>());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
        ParsedDocument document = parsed(params.getTextDocument().getUri());
        if (document != null) {
            for (DocumentSymbol symbol : DocumentSymbols.of(document)) result.add(Either.forRight(symbol));
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        ParsedDocument document = parsed(params.getTextDocument().getUri());
        List<CompletionItem> items = document == null ? new ArrayList<>() : KeywordCompletion.at(document, params.getPosition());
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        ParsedDocument document = parsed(params.getTextDocument().getUri());
        return CompletableFuture.completedFuture(
            document == null ? new SemanticTokens(new ArrayList<>()) : SemanticTokensProvider.of(document));
    }

    private ParsedDocument parsed(String uri) {
        String text = open.get(uri);
        return text == null ? null : ParsedDocument.parse(text);
    }

    private void scheduleDiagnostics(String uri) {
        ScheduledFuture<?> previous = pendingDiagnostics.put(uri,
            analysis.schedule(() -> diagnose(uri), DIAGNOSTICS_DELAY_MS, TimeUnit.MILLISECONDS));
        if (previous != null) previous.cancel(false);
    }

    private void diagnose(String uri) {
        String text = open.get(uri);
        if (text == null) return;
        try {
            publish(uri, Diagnostics.of(ParsedDocument.parse(text)));
        } catch (RuntimeException e) {
            System.err.println("Analysis of " + uri + " failed:");
            e.printStackTrace();
        }
    }

    private void publish(String uri, List<Diagnostic> diagnostics) {
        LanguageClient client = this.client;
        if (client != null) client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }
}
