package io.pisek.gmailforward.config;

public class ServerConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final String protocol;

    public ServerConfig(String host, int port, String username, String password, boolean ssl, String protocol) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
        this.protocol = protocol;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isSsl() { return ssl; }
    public String getProtocol() { return protocol; }
}
