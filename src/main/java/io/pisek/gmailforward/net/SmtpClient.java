package io.pisek.gmailforward.net;

import io.pisek.gmailforward.config.SmtpServerConfig;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

public class SmtpClient implements AutoCloseable {

    private static final Logger log = Logger.getLogger(SmtpClient.class.getName());

    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream writer;

    private SmtpClient(Socket socket, BufferedReader reader, OutputStream writer) {
        this.socket = socket;
        this.reader = reader;
        this.writer = writer;
    }

    public static SmtpClient connect(SmtpServerConfig config) throws IOException {
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
        String greeting = readMultilineResponse(reader);
        if (!greeting.startsWith("220")) {
            throw new IOException("SMTP greeting failed: " + greeting);
        }

        SmtpClient client = new SmtpClient(socket, reader, writer);

        // EHLO
        client.sendCommand("EHLO localhost", "250");

        // AUTH LOGIN
        client.sendCommand("AUTH LOGIN", "334");
        client.sendCommand(Base64.getEncoder().encodeToString(
                config.getUsername().getBytes(StandardCharsets.UTF_8)), "334");
        client.sendCommand(Base64.getEncoder().encodeToString(
                config.getPassword().getBytes(StandardCharsets.UTF_8)), "235");

        log.fine("Connected and authenticated to SMTP server " + config.getHost());
        return client;
    }

    public void sendMessage(String from, String to, byte[] rawMessage) throws IOException {
        sendCommand("MAIL FROM:<" + from + ">", "250");
        sendCommand("RCPT TO:<" + to + ">", "250");
        sendCommand("DATA", "354");

        // Send raw message bytes
        writer.write(rawMessage);

        // Ensure message ends with CRLF before the terminating dot
        if (rawMessage.length < 2 ||
                rawMessage[rawMessage.length - 2] != '\r' || rawMessage[rawMessage.length - 1] != '\n') {
            writer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }

        // Send terminating dot
        sendCommand(".", "250");

        log.fine("Sent message via SMTP from <" + from + "> to <" + to + "> (" + rawMessage.length + " bytes)");
    }

    @Override
    public void close() throws IOException {
        try {
            sendLine("QUIT");
            reader.readLine();
        } catch (IOException e) {
            log.warning("Error during SMTP QUIT: " + e.getMessage());
        } finally {
            socket.close();
        }
    }

    private void sendCommand(String command, String expectedCode) throws IOException {
        sendLine(command);
        String response = readMultilineResponse(reader);
        if (!response.startsWith(expectedCode)) {
            // Mask potential credentials in error message
            String safeCommand = command.length() > 50 ? command.substring(0, 10) + "..." : command;
            throw new IOException("SMTP command failed (expected " + expectedCode + "): " + response +
                    " (command: " + safeCommand + ")");
        }
    }

    private void sendLine(String line) throws IOException {
        writer.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    private static String readMultilineResponse(BufferedReader reader) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
            // SMTP multiline responses have '-' at position 3, last line has ' ' or end
            if (line.length() >= 4 && line.charAt(3) == '-') {
                response.append("\n");
                continue;
            }
            break;
        }
        if (response.isEmpty()) {
            throw new IOException("Connection closed waiting for SMTP response");
        }
        return response.toString();
    }
}
