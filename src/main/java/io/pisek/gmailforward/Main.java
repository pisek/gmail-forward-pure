package io.pisek.gmailforward;

import io.pisek.gmailforward.config.AccountConfig;
import io.pisek.gmailforward.config.AppConfig;
import io.pisek.gmailforward.service.DuplicateTracker;
import io.pisek.gmailforward.service.MailForwardService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static final AtomicBoolean running = new AtomicBoolean(true);

    public static void main(String[] args) {
        String configPath = "config.json";
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = args[++i];
            }
        }

        AppConfig config;
        try {
            config = AppConfig.load(configPath);
        } catch (Exception e) {
            log.severe("Failed to load config from '" + configPath + "': " + e.getMessage());
            System.exit(1);
            return;
        }

        log.info("Loaded " + config.getAccounts().size() + " account(s) from " + configPath);

        DuplicateTracker tracker;
        try {
            tracker = new DuplicateTracker(config.getDbPath());
        } catch (Exception e) {
            log.severe("Failed to initialize duplicate tracker: " + e.getMessage());
            System.exit(1);
            return;
        }

        MailForwardService service = new MailForwardService(tracker);

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            running.set(false);
        }));

        // Track next poll time per account
        Map<String, Instant> nextPollTime = new HashMap<>();
        for (AccountConfig account : config.getAccounts()) {
            nextPollTime.put(account.getName(), Instant.now());
            log.info("Scheduled account '" + account.getName() + "' every " +
                    account.getPollingIntervalSeconds() + "s");
        }

        // Main polling loop
        while (running.get()) {
            Instant now = Instant.now();
            for (AccountConfig account : config.getAccounts()) {
                if (!running.get()) break;

                Instant next = nextPollTime.get(account.getName());
                if (now.isAfter(next) || now.equals(next)) {
                    try {
                        service.processAccount(account);
                    } catch (Exception e) {
                        log.log(Level.SEVERE, "Unexpected error processing account '" +
                                account.getName() + "': " + e.getMessage(), e);
                    }
                    nextPollTime.put(account.getName(),
                            Instant.now().plusSeconds(account.getPollingIntervalSeconds()));
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Stopped.");
    }
}
