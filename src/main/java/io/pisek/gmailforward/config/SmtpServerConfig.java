package io.pisek.gmailforward.config;

public class SmtpServerConfig extends ServerConfig {

    private final String to;

    public SmtpServerConfig(String host, int port, String username, String password, boolean ssl, String to) {
        super(host, port, username, password, ssl, "smtp");
        this.to = to;
    }

    public String getTo() { return to; }
}
