package io.pisek.gmailforward.net;

import io.pisek.gmailforward.config.ImapServerConfig;
import io.pisek.gmailforward.config.ServerConfig;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static ImapClient connect(ServerConfig config) throws IOException {
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

    // --- Output methods (APPEND) ---

    public void appendMessage(String folder, byte[] rawMessage) throws IOException {
        String tag = nextTag();
        String command = tag + " APPEND " + quoteString(folder) + " {" + rawMessage.length + "}";
        sendLine(command);

        String continuation = reader.readLine();
        if (continuation == null || !continuation.startsWith("+")) {
            throw new IOException("IMAP APPEND continuation failed: " + continuation);
        }

        writer.write(rawMessage);
        writer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        writer.flush();

        readTaggedResponse(tag);
        log.fine("Appended message to folder '" + folder + "' (" + rawMessage.length + " bytes)");
    }

    public void createFolderIfNotExists(String folder) throws IOException {
        String tag = nextTag();
        sendLine(tag + " CREATE " + quoteString(folder));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(tag)) break;
        }
    }

    // --- Input methods (SELECT, FETCH, DELETE) ---

    public int selectFolder(String folder) throws IOException {
        String tag = nextTag();
        sendLine(tag + " SELECT " + quoteString(folder));
        int exists = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("EXISTS")) {
                Matcher m = Pattern.compile("\\* (\\d+) EXISTS").matcher(line);
                if (m.find()) {
                    exists = Integer.parseInt(m.group(1));
                }
            }
            if (line.startsWith(tag)) {
                if (!line.contains("OK")) {
                    throw new IOException("IMAP SELECT failed: " + line);
                }
                break;
            }
        }
        return exists;
    }

    public List<Map.Entry<Integer, String>> fetchUids(int messageCount) throws IOException {
        if (messageCount == 0) return List.of();

        String tag = nextTag();
        sendLine(tag + " FETCH 1:" + messageCount + " (UID)");

        List<Map.Entry<Integer, String>> result = new ArrayList<>();
        Pattern uidPattern = Pattern.compile("\\* (\\d+) FETCH \\(UID (\\d+)\\)");
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher m = uidPattern.matcher(line);
            if (m.find()) {
                int seqNum = Integer.parseInt(m.group(1));
                String uid = m.group(2);
                result.add(new AbstractMap.SimpleEntry<>(seqNum, uid));
            }
            if (line.startsWith(tag)) {
                if (!line.contains("OK")) {
                    throw new IOException("IMAP FETCH UIDs failed: " + line);
                }
                break;
            }
        }
        return result;
    }

    public byte[] fetchMessage(int sequenceNumber) throws IOException {
        String tag = nextTag();
        sendLine(tag + " FETCH " + sequenceNumber + " BODY.PEEK[]");

        StringBuilder messageData = new StringBuilder();
        int expectedSize = -1;
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith(tag)) {
                if (!line.contains("OK")) {
                    throw new IOException("IMAP FETCH failed: " + line);
                }
                break;
            }

            // Look for literal size: * N FETCH (BODY[] {SIZE}
            if (expectedSize < 0) {
                Matcher m = Pattern.compile("\\{(\\d+)\\}").matcher(line);
                if (m.find()) {
                    expectedSize = Integer.parseInt(m.group(1));
                    // Read exactly expectedSize bytes
                    char[] buf = new char[expectedSize];
                    int totalRead = 0;
                    while (totalRead < expectedSize) {
                        int read = reader.read(buf, totalRead, expectedSize - totalRead);
                        if (read == -1) throw new IOException("Unexpected end of stream during FETCH");
                        totalRead += read;
                    }
                    messageData.append(buf, 0, expectedSize);
                }
            }
        }

        if (messageData.isEmpty()) {
            throw new IOException("No message data received for sequence " + sequenceNumber);
        }
        return messageData.toString().getBytes(StandardCharsets.UTF_8);
    }

    public void deleteMessage(int sequenceNumber) throws IOException {
        sendCommand("STORE " + sequenceNumber + " +FLAGS (\\Deleted)");
    }

    public void expunge() throws IOException {
        sendCommand("EXPUNGE");
    }

    // --- Connection management ---

    public void logout() throws IOException {
        try {
            String tag = nextTag();
            sendLine(tag + " LOGOUT");
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
