package io.pisek.gmailforward.config;

public class AccountConfig {

    private final String name;
    private final ServerConfig pop3;
    private final ImapServerConfig imap;
    private final int pollingIntervalSeconds;
    private final boolean deleteAfterCopy;

    public AccountConfig(String name, ServerConfig pop3, ImapServerConfig imap,
                         int pollingIntervalSeconds, boolean deleteAfterCopy) {
        this.name = name;
        this.pop3 = pop3;
        this.imap = imap;
        this.pollingIntervalSeconds = pollingIntervalSeconds;
        this.deleteAfterCopy = deleteAfterCopy;
    }

    public String getName() { return name; }
    public ServerConfig getPop3() { return pop3; }
    public ImapServerConfig getImap() { return imap; }
    public int getPollingIntervalSeconds() { return pollingIntervalSeconds; }
    public boolean isDeleteAfterCopy() { return deleteAfterCopy; }
}
