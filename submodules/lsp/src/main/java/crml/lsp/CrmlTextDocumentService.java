package crml.lsp;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.services.TextDocumentService;

/** Keeps the text of the documents open in the editor, by URI. */
public class CrmlTextDocumentService implements TextDocumentService {
    private final Map<String, String> open = new ConcurrentHashMap<>();

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        open.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // Full sync: the last change holds the whole new text.
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (!changes.isEmpty()) {
            open.put(params.getTextDocument().getUri(), changes.get(changes.size() - 1).getText());
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        open.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    /** The current text of an open document, or null when it is not open. */
    public String text(String uri) {
        return open.get(uri);
    }
}
