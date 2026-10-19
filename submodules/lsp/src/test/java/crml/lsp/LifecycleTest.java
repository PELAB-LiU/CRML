package crml.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class LifecycleTest {
    /** Connects a client to a server in this process, through pipes. */
    private static LanguageServer connect(TestClient client) throws IOException {
        PipedInputStream serverIn = new PipedInputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream(serverIn);
        PipedOutputStream serverOut = new PipedOutputStream(clientIn);

        CrmlLanguageServer server = new CrmlLanguageServer();
        Launcher<LanguageClient> serverLauncher = LSPLauncher.createServerLauncher(server, serverIn, serverOut);
        server.connect(serverLauncher.getRemoteProxy());
        serverLauncher.startListening();

        Launcher<LanguageServer> clientLauncher = LSPLauncher.createClientLauncher(client, clientIn, clientOut);
        clientLauncher.startListening();
        return clientLauncher.getRemoteProxy();
    }

    @Test
    void initializesInProcess() throws Exception {
        LanguageServer remote = connect(new TestClient());
        InitializeResult result = remote.initialize(new InitializeParams()).get(10, TimeUnit.SECONDS);
        assertEquals(CrmlLanguageServer.NAME, result.getServerInfo().getName());
        ServerCapabilities capabilities = result.getCapabilities();
        assertEquals(TextDocumentSyncKind.Full, capabilities.getTextDocumentSync().getLeft());
        assertTrue(capabilities.getDocumentSymbolProvider().getLeft());
        assertNotNull(capabilities.getCompletionProvider());
        assertNotNull(capabilities.getSemanticTokensProvider());
        remote.initialized(new InitializedParams());
        remote.shutdown().get(10, TimeUnit.SECONDS);
    }

    @Test
    void leavesHighlightingToClientsThatDoItThemselves() throws Exception {
        LanguageServer remote = connect(new TestClient());
        InitializeParams params = new InitializeParams();
        JsonObject options = new JsonObject();
        options.addProperty(CrmlLanguageServer.CLIENT_HIGHLIGHTING, true);
        params.setInitializationOptions(options);
        assertNull(remote.initialize(params).get(10, TimeUnit.SECONDS).getCapabilities().getSemanticTokensProvider());
    }

    @Test
    void publishesDiagnosticsShortlyAfterChanges() throws Exception {
        TestClient client = new TestClient();
        LanguageServer remote = connect(client);
        remote.initialize(new InitializeParams()).get(10, TimeUnit.SECONDS);
        String uri = "file:///test/M.crml";

        remote.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
            new TextDocumentItem(uri, "crml", 1, "model M is {\n  Boolean b is ;\n};")));
        PublishDiagnosticsParams broken = client.awaitDiagnostics(uri, 1);
        assertEquals(1, broken.getDiagnostics().size());

        remote.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
            new VersionedTextDocumentIdentifier(uri, 2),
            List.of(new TextDocumentContentChangeEvent("model M is {\n  Boolean b is true;\n};"))));
        assertEquals(List.of(), client.awaitDiagnostics(uri, 2).getDiagnostics());
    }

    /** Runs the shadow jar the editor launches, and checks that stdout carries nothing but the protocol. */
    @Test
    void runsAsProcessOverStdio() throws Exception {
        Path jar = Paths.get(System.getProperty("crml.lsp.jar"));
        assertTrue(Files.isRegularFile(jar), "missing " + jar);
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(), "-jar", jar.toString())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();

        // Tee stdout so that, after the session, it can be checked for stray output.
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        InputStream tee = new InputStream() {
            private final InputStream in = process.getInputStream();

            @Override
            public int read() throws IOException {
                int b = in.read();
                if (b >= 0) stdout.write(b);
                return b;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int n = in.read(buffer, offset, length);
                if (n > 0) stdout.write(buffer, offset, n);
                return n;
            }
        };

        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(new TestClient(), tee, process.getOutputStream());
        launcher.startListening();
        LanguageServer remote = launcher.getRemoteProxy();

        InitializeResult result = remote.initialize(new InitializeParams()).get(30, TimeUnit.SECONDS);
        assertEquals(CrmlLanguageServer.NAME, result.getServerInfo().getName());
        remote.initialized(new InitializedParams());
        remote.shutdown().get(10, TimeUnit.SECONDS);
        remote.exit();

        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "server did not exit");
        assertEquals(0, process.exitValue());
        String output = new String(stdout.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(output.startsWith("Content-Length:"), "stdout does not start with a protocol message: " + output);
    }
}
