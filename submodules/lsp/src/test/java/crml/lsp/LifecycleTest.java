package crml.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

class LifecycleTest {
    @Test
    void initializesInProcess() throws Exception {
        PipedInputStream serverIn = new PipedInputStream();
        PipedInputStream clientIn = new PipedInputStream();
        PipedOutputStream clientOut = new PipedOutputStream(serverIn);
        PipedOutputStream serverOut = new PipedOutputStream(clientIn);

        CrmlLanguageServer server = new CrmlLanguageServer();
        Launcher<org.eclipse.lsp4j.services.LanguageClient> serverLauncher =
            LSPLauncher.createServerLauncher(server, serverIn, serverOut);
        server.connect(serverLauncher.getRemoteProxy());
        serverLauncher.startListening();

        Launcher<LanguageServer> clientLauncher = LSPLauncher.createClientLauncher(new TestClient(), clientIn, clientOut);
        clientLauncher.startListening();
        LanguageServer remote = clientLauncher.getRemoteProxy();

        InitializeResult result = remote.initialize(new InitializeParams()).get(10, TimeUnit.SECONDS);
        assertEquals(CrmlLanguageServer.NAME, result.getServerInfo().getName());
        assertEquals(TextDocumentSyncKind.Full, result.getCapabilities().getTextDocumentSync().getLeft());
        remote.initialized(new InitializedParams());
        remote.shutdown().get(10, TimeUnit.SECONDS);
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
