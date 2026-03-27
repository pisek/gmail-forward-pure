package io.pisek.gmailforward.net;

import io.pisek.gmailforward.config.ImapServerConfig;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class ImapClient implements AutoCloseable {

    private static final Logger log = Logger.getLogger(ImapClient.class.getName());

    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream writer;
    private int tagCounter = 0;

    private ImapClient(Socket socket, BufferedReader reader, OutputStream writer) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
    }

    public static ImapClient connect(ImapServerConfig config) throws IOException {
        Socket socket;
        if (config.isSsl()) {
            socket = SSLSocketFactory.getDefault().createSocket(config.getHost(), config.getPort());
        } else {
            socket = new Socket(config.getHost(), config.getPort());
        }
        socket.setSoTimeout(30_000);

        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream writer = socket.getOutputStream();

        // Read greeting
        String greeting = reader.readLine();
        if (greeting == null || !greeting.contains("OK")) {
            throw new IOException("IMAP greeting failed: " + greeting);
        }

        ImapClient client = new ImapClient(socket, reader, writer);

        // Login
        client.sendCommand("LOGIN " + quoteString(config.getUsername()) + " " + quoteString(config.getPassword()));

        return client;
    }

    public void appendMessage(String folder, byte[] rawMessage) throws IOException {
        String tag = nextTag();
        // IMAP APPEND command: tag APPEND "folder" {size}
        String command = tag + " APPEND " + quoteString(folder) + " {" + rawMessage.length + "}";
        sendLine(command);

        // Server responds with "+" continuation request
        String continuation = reader.readLine();
        if (continuation == null || !continuation.startsWith("+")) {
            throw new IOException("IMAP APPEND continuation failed: " + continuation);
        }

        // Send raw message bytes followed by CRLF
        writer.write(rawMessage);
        writer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        writer.flush();

        // Read response until we get our tagged response
        readTaggedResponse(tag);
        log.fine("Appended message to folder '" + folder + "' (" + rawMessage.length + " bytes)");
    }

    public void createFolderIfNotExists(String folder) throws IOException {
        String tag = nextTag();
        sendLine(tag + " CREATE " + quoteString(folder));
        // CREATE may fail if folder exists — that's OK
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(tag)) break;
        }
    }

    public void logout() throws IOException {
        try {
            String tag = nextTag();
            sendLine(tag + " LOGOUT");
            // Read until tagged response or BYE
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(tag) || line.contains("BYE")) break;
            }
        } catch (IOException e) {
            log.warning("Error during LOGOUT: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        try {
            logout();
        } finally {
            socket.close();
        }
    }

    private void sendCommand(String command) throws IOException {
        String tag = nextTag();
        sendLine(tag + " " + command);
        readTaggedResponse(tag);
    }

    private void readTaggedResponse(String tag) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(tag)) {
                if (!line.contains("OK")) {
                    throw new IOException("IMAP command failed: " + line);
                }
                return;
            }
            // Untagged responses — skip
        }
        throw new IOException("Connection closed waiting for IMAP response");
    }

    private void sendLine(String line) throws IOException {
        writer.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    private String nextTag() {
        return "A" + (++tagCounter);
    }

    private static String quoteString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
