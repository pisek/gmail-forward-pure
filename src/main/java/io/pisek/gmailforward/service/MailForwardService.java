package io.pisek.gmailforward.service;

import io.pisek.gmailforward.config.AccountConfig;
import io.pisek.gmailforward.net.ImapClient;
import io.pisek.gmailforward.net.Pop3Client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailForwardService {

    private static final Logger log = Logger.getLogger(MailForwardService.class.getName());

    private final DuplicateTracker tracker;

    public MailForwardService(DuplicateTracker tracker) {
        this.tracker = tracker;
    }

    public void processAccount(AccountConfig account) {
        log.info("Processing account '" + account.getName() + "'");

        try (Pop3Client pop3 = Pop3Client.connect(account.getPop3())) {
            // Get message list with unique IDs
            List<Map.Entry<Integer, String>> uidlList = pop3.uidl();
            if (uidlList.isEmpty()) {
                log.info("Account '" + account.getName() + "': no messages on POP3 server");
                return;
            }

            // Fetch raw messages
            List<MessageData> allMessages = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : uidlList) {
                try {
                    byte[] raw = pop3.retr(entry.getKey());
                    String messageId = MessageUtils.extractMessageId(raw);
                    allMessages.add(new MessageData(messageId, raw, entry.getKey()));
                } catch (Exception e) {
                    log.warning("Account '" + account.getName() + "': failed to fetch message " +
                            entry.getKey() + ": " + e.getMessage());
                }
            }

            // Filter already-seen messages
            List<MessageData> newMessages = tracker.filterUnseen(account.getName(), allMessages);
            if (newMessages.isEmpty()) {
                log.info("Account '" + account.getName() + "': no new messages");
                return;
            }

            log.info("Account '" + account.getName() + "': " + newMessages.size() + " new message(s) to forward");

            // Append to IMAP
            Set<Integer> successfulNumbers = new HashSet<>();
            for (MessageData message : newMessages) {
                try (ImapClient imap = ImapClient.connect(account.getImap())) {
                    imap.appendMessage(account.getImap().getFolder(), message.rawContent());
                    tracker.markSeen(account.getName(), message.messageId());
                    successfulNumbers.add(message.messageNumber());
                    log.fine("Account '" + account.getName() + "': forwarded message " + message.messageId());
                } catch (Exception e) {
                    log.log(Level.WARNING, "Account '" + account.getName() +
                            "': failed to forward message " + message.messageId() + ": " + e.getMessage(), e);
                }
            }

            // Delete from POP3 if configured
            if (account.isDeleteAfterCopy() && !successfulNumbers.isEmpty()) {
                for (int msgNum : successfulNumbers) {
                    try {
                        pop3.dele(msgNum);
                    } catch (Exception e) {
                        log.warning("Account '" + account.getName() +
                                "': failed to delete message " + msgNum + ": " + e.getMessage());
                    }
                }
                log.info("Account '" + account.getName() + "': deleted " +
                        successfulNumbers.size() + " message(s) from POP3");
            }

            log.info("Account '" + account.getName() + "': forwarded " +
                    successfulNumbers.size() + "/" + newMessages.size() + " message(s)");

        } catch (Exception e) {
            log.log(Level.SEVERE, "Account '" + account.getName() + "': failed to process: " + e.getMessage(), e);
        }
    }
}
