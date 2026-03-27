package io.pisek.gmailforward.net;

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

public class Pop3Client implements AutoCloseable {

    private static final Logger log = Logger.getLogger(Pop3Client.class.getName());

    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream writer;

    private Pop3Client(Socket socket, BufferedReader reader, OutputStream writer) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
    }

    public static Pop3Client connect(ServerConfig config) throws IOException {
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
        if (!greeting.startsWith("+OK")) {
            throw new IOException("POP3 greeting failed: " + greeting);
        }

        Pop3Client client = new Pop3Client(socket, reader, writer);

        // Authenticate
        client.sendCommand("USER " + config.getUsername());
        client.sendCommand("PASS " + config.getPassword());

        return client;
    }

    public List<Map.Entry<Integer, String>> uidl() throws IOException {
        sendLine("UIDL");
        String status = reader.readLine();
        if (!status.startsWith("+OK")) {
            throw new IOException("UIDL failed: " + status);
        }

        List<Map.Entry<Integer, String>> result = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) break;
            // Format: "1 unique-id"
            int space = line.indexOf(' ');
            if (space > 0) {
                int msgNum = Integer.parseInt(line.substring(0, space));
                String uid = line.substring(space + 1).trim();
                result.add(new AbstractMap.SimpleEntry<>(msgNum, uid));
            }
        }
        return result;
    }

    public byte[] retr(int messageNumber) throws IOException {
        sendLine("RETR " + messageNumber);
        String status = reader.readLine();
        if (!status.startsWith("+OK")) {
            throw new IOException("RETR " + messageNumber + " failed: " + status);
        }

        // Read until "." on a line by itself, handling byte-stuffing
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) break;
            // POP3 byte-stuffing: lines starting with "." have the dot removed
            if (line.startsWith(".")) {
                line = line.substring(1);
            }
            sb.append(line).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public void dele(int messageNumber) throws IOException {
        sendCommand("DELE " + messageNumber);
        log.fine("Marked message " + messageNumber + " for deletion");
    }

    public void quit() throws IOException {
        try {
            sendLine("QUIT");
            reader.readLine(); // read response
        } catch (IOException e) {
            log.warning("Error during QUIT: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        try {
            quit();
        } finally {
            socket.close();
        }
    }

    private void sendCommand(String command) throws IOException {
        sendLine(command);
        String response = reader.readLine();
        if (response == null || !response.startsWith("+OK")) {
            // Mask password in error messages
            String safeCmd = command.startsWith("PASS ") ? "PASS ****" : command;
            throw new IOException("POP3 command '" + safeCmd + "' failed: " + response);
        }
    }

    private void sendLine(String line) throws IOException {
        writer.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }
}
