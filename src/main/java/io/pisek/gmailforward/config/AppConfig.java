package io.pisek.gmailforward.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AppConfig {

    private final String dbPath;
    private final List<AccountConfig> accounts;

    public AppConfig(String dbPath, List<AccountConfig> accounts) {
        this.dbPath = dbPath;
        this.accounts = accounts;
    }

    public String getDbPath() { return dbPath; }
    public List<AccountConfig> getAccounts() { return accounts; }

    @SuppressWarnings("unchecked")
    public static AppConfig load(String configPath) throws IOException {
        String json = Files.readString(Path.of(configPath));
        Map<String, Object> root = (Map<String, Object>) JsonParser.parse(json);

        String dbPath = getStringOrDefault(root, "db_path", "./data");
        List<Object> accountsList = (List<Object>) root.get("accounts");
        if (accountsList == null || accountsList.isEmpty()) {
            throw new IllegalArgumentException("No accounts configured");
        }

        List<AccountConfig> accounts = new ArrayList<>();
        for (Object item : accountsList) {
            Map<String, Object> acc = (Map<String, Object>) item;
            accounts.add(parseAccount(acc));
        }

        return new AppConfig(dbPath, accounts);
    }

    @SuppressWarnings("unchecked")
    private static AccountConfig parseAccount(Map<String, Object> acc) {
        String name = requireString(acc, "name");
        Map<String, Object> pop3Map = (Map<String, Object>) acc.get("pop3");
        Map<String, Object> imapMap = (Map<String, Object>) acc.get("imap");
        if (pop3Map == null) throw new IllegalArgumentException("Account '" + name + "' missing pop3 config");
        if (imapMap == null) throw new IllegalArgumentException("Account '" + name + "' missing imap config");

        ServerConfig pop3 = parseServerConfig(pop3Map);
        ImapServerConfig imap = parseImapConfig(imapMap);
        int pollingInterval = getIntOrDefault(acc, "polling_interval", 300);
        boolean deleteAfterCopy = getBoolOrDefault(acc, "delete_after_copy", false);

        return new AccountConfig(name, pop3, imap, pollingInterval, deleteAfterCopy);
    }

    private static ServerConfig parseServerConfig(Map<String, Object> map) {
        return new ServerConfig(
                requireString(map, "host"),
                requireInt(map, "port"),
                requireString(map, "username"),
                requireString(map, "password"),
                getBoolOrDefault(map, "ssl", true)
        );
    }

    private static ImapServerConfig parseImapConfig(Map<String, Object> map) {
        return new ImapServerConfig(
                requireString(map, "host"),
                requireInt(map, "port"),
                requireString(map, "username"),
                requireString(map, "password"),
                getBoolOrDefault(map, "ssl", true),
                getStringOrDefault(map, "folder", "INBOX")
        );
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required field: " + key);
        return val.toString();
    }

    private static int requireInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required field: " + key);
        return ((Number) val).intValue();
    }

    private static String getStringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static int getIntOrDefault(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        return val != null ? ((Number) val).intValue() : defaultVal;
    }

    private static boolean getBoolOrDefault(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        return val != null ? (Boolean) val : defaultVal;
    }
}
