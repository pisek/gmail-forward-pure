package io.pisek.gmailforward.config;

public class AccountConfig {

    private final String name;
    private final ServerConfig input;
    private final SmtpServerConfig output;
    private final int pollingIntervalSeconds;
    private final boolean deleteAfterCopy;

    public AccountConfig(String name, ServerConfig input, SmtpServerConfig output,
                         int pollingIntervalSeconds, boolean deleteAfterCopy) {
        this.name = name;
        this.input = input;
        this.output = output;
        this.pollingIntervalSeconds = pollingIntervalSeconds;
        this.deleteAfterCopy = deleteAfterCopy;
    }

    public String getName() { return name; }
    public ServerConfig getInput() { return input; }
    public SmtpServerConfig getOutput() { return output; }
    public int getPollingIntervalSeconds() { return pollingIntervalSeconds; }
    public boolean isDeleteAfterCopy() { return deleteAfterCopy; }
}
