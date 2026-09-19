package ssl.chat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class ChatServer {

    private static final Logger LOG = Logger.getLogger(ChatServer.class.getName());

    private static final int DEFAULT_PORT = 9001;

    private final Set<String> names = ConcurrentHashMap.newKeySet();
    private final Set<PrintWriter> writers = ConcurrentHashMap.newKeySet();

    private final SSLServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    ChatServer(int port) throws Exception {
        this.serverSocket = createServerSocket(port);
        LOG.info("Chat server running on port " + port);
    }

    void serve() {
        try {
            while (!serverSocket.isClosed()) {
                SSLSocket client = (SSLSocket) serverSocket.accept();
                pool.execute(new Handler(client));
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                LOG.log(Level.SEVERE, "Accept failed", e);
            }
        }
    }

    void shutdown() {
        try {
            pool.shutdownNow();
            serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error during shutdown", e);
        }
    }

    private class Handler implements Runnable {
        private final SSLSocket socket;
        private String name;

        Handler(SSLSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null || name.isBlank()) {
                        return;
                    }
                    if (names.add(name)) {
                        break;
                    }
                }

                out.println("NAMEACCEPTED");
                writers.add(out);
                LOG.info(name + " joined");

                String input;
                while ((input = in.readLine()) != null) {
                    if (input.isBlank()) continue;
                    String msg = "MESSAGE " + name + ": " + input;
                    for (PrintWriter w : writers) {
                        w.println(msg);
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Handler error for " + name, e);
            } finally {
                if (name != null) {
                    names.remove(name);
                    LOG.info(name + " left");
                }
            }
        }
    }

    private static SSLServerSocket createServerSocket(int port) throws Exception {
        String keystorePass = System.getenv().getOrDefault("KEYSTORE_PASS", "changeit");
        char[] pass = keystorePass.toCharArray();

        KeyStore serverKeys = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream("keystore/server.p12")) {
            serverKeys.load(fis, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKeys, pass);

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream("keystore/client-trust.p12")) {
            trustStore.load(fis, pass);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        SSLServerSocket ss = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port);
        ss.setNeedClientAuth(true);
        return ss;
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ChatServer server = new ChatServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));
        server.serve();
    }
}
