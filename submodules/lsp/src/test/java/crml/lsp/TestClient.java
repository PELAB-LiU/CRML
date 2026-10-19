package crml.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

/** A language client that records what the server sends it. */
class TestClient implements LanguageClient {
    final List<PublishDiagnosticsParams> diagnostics = new CopyOnWriteArrayList<>();
    final List<MessageParams> messages = new CopyOnWriteArrayList<>();

    @Override
    public void telemetryEvent(Object object) {
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams params) {
        diagnostics.add(params);
    }

    @Override
    public void showMessage(MessageParams params) {
        messages.add(params);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams params) {
        messages.add(params);
    }
}
