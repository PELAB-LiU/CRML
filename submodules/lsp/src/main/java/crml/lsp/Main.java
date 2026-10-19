package crml.lsp;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

/** Runs the CRML language server over stdin and stdout. */
public class Main {
    public static void main(String[] args) throws Exception {
        // stdout carries the protocol. Keep a raw handle for it, and send
        // everything else printed to System.out, by any library, to stderr.
        OutputStream protocolOut = new FileOutputStream(FileDescriptor.out);
        System.setOut(System.err);

        CrmlLanguageServer server = new CrmlLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, protocolOut);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
