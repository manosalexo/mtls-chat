package ssl.chat;

import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ChatClient {

    private static final Logger LOG = Logger.getLogger(ChatClient.class.getName());

    private static final int DEFAULT_PORT = 9001;

    private SSLSocket socket;
    private BufferedReader in;
    private PrintWriter out;

    private final JFrame frame = new JFrame("Chatter");
    private final JTextField textField = new JTextField(40);
    private final JTextArea messageArea = new JTextArea(8, 40);

    public ChatClient() {
        textField.setEditable(false);
        messageArea.setEditable(false);
        frame.getContentPane().add(textField, BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.pack();

        textField.addActionListener(e -> {
            String text = textField.getText();
            if (!text.isBlank()) {
                out.println(text);
                textField.setText("");
            }
        });
    }

    private String getServerAddress() {
        return JOptionPane.showInputDialog(
                frame, "Enter IP Address of the Server:",
                "Welcome to the Chatter",
                JOptionPane.QUESTION_MESSAGE);
    }

    private String getName() {
        return JOptionPane.showInputDialog(
                frame, "Choose a screen name:",
                "Screen name selection",
                JOptionPane.PLAIN_MESSAGE);
    }

    private void process() throws Exception {
        String serverAddress = getServerAddress();
        if (serverAddress == null) return;

        socket = createSSLSocket(serverAddress, DEFAULT_PORT);
        socket.startHandshake();

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("SUBMITNAME")) {
                    String name = getName();
                    if (name == null) return;
                    out.println(name);
                } else if (line.startsWith("NAMEACCEPTED")) {
                    textField.setEditable(true);
                } else if (line.startsWith("MESSAGE")) {
                    messageArea.append(line.substring(8) + "\n");
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Connection lost", e);
        } finally {
            if (socket != null) socket.close();
        }
    }

    private static SSLSocket createSSLSocket(String host, int port) throws Exception {
        String keystorePass = System.getenv().getOrDefault("KEYSTORE_PASS", "changeit");
        char[] pass = keystorePass.toCharArray();

        KeyStore clientKeys = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream("keystore/client.p12")) {
            clientKeys.load(fis, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(clientKeys, pass);

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream("keystore/server-trust.p12")) {
            trustStore.load(fis, pass);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return (SSLSocket) ctx.getSocketFactory().createSocket(host, port);
    }

    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        SwingUtilities.invokeLater(() -> client.frame.setVisible(true));
        client.process();
    }
}
