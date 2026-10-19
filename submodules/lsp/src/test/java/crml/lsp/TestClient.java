package crml.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

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
        synchronized (diagnostics) {
            diagnostics.add(params);
            diagnostics.notifyAll();
        }
    }

    /** Waits for the count-th diagnostics published for a document. */
    PublishDiagnosticsParams awaitDiagnostics(String uri, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        synchronized (diagnostics) {
            while (true) {
                List<PublishDiagnosticsParams> forUri = diagnostics.stream()
                    .filter(d -> d.getUri().equals(uri)).collect(Collectors.toList());
                if (forUri.size() >= count) return forUri.get(count - 1);
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) throw new AssertionError("no diagnostics #" + count + " for " + uri + ": " + diagnostics);
                diagnostics.wait(left);
            }
        }
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
