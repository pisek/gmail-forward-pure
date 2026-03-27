package io.pisek.gmailforward.config;

public class ImapServerConfig extends ServerConfig {

    private final String folder;

    public ImapServerConfig(String host, int port, String username, String password, boolean ssl, String folder) {
        super(host, port, username, password, ssl, "imap");
        this.folder = folder;
    }

    public String getFolder() { return folder; }
}
