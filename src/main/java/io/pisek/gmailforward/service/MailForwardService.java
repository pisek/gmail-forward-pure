package io.pisek.gmailforward.service;

import io.pisek.gmailforward.config.AccountConfig;
import io.pisek.gmailforward.config.ImapServerConfig;
import io.pisek.gmailforward.config.SmtpServerConfig;
import io.pisek.gmailforward.net.ImapClient;
import io.pisek.gmailforward.net.Pop3Client;
import io.pisek.gmailforward.net.SmtpClient;

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

        String protocol = account.getInput().getProtocol();
        if ("imap".equalsIgnoreCase(protocol)) {
            processImapInput(account);
        } else {
            processPop3Input(account);
        }
    }

    private void processPop3Input(AccountConfig account) {
        try (Pop3Client pop3 = Pop3Client.connect(account.getInput())) {
            List<Map.Entry<Integer, String>> uidlList = pop3.uidl();
            if (uidlList.isEmpty()) {
                log.info("Account '" + account.getName() + "': no messages on POP3 server");
                return;
            }

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

            List<MessageData> newMessages = tracker.filterUnseen(account.getName(), allMessages);
            if (newMessages.isEmpty()) {
                log.info("Account '" + account.getName() + "': no new messages");
                return;
            }

            log.info("Account '" + account.getName() + "': " + newMessages.size() + " new message(s) to forward");

            Set<Integer> successfulNumbers = new HashSet<>();
            forwardMessages(account, newMessages, successfulNumbers);

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

    private void processImapInput(AccountConfig account) {
        ImapServerConfig inputConfig = (ImapServerConfig) account.getInput();
        try (ImapClient imapIn = ImapClient.connect(inputConfig)) {
            int messageCount = imapIn.selectFolder(inputConfig.getFolder());
            if (messageCount == 0) {
                log.info("Account '" + account.getName() + "': no messages in IMAP folder");
                return;
            }

            List<Map.Entry<Integer, String>> uids = imapIn.fetchUids(messageCount);

            List<MessageData> allMessages = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : uids) {
                try {
                    byte[] raw = imapIn.fetchMessage(entry.getKey());
                    String messageId = MessageUtils.extractMessageId(raw);
                    allMessages.add(new MessageData(messageId, raw, entry.getKey()));
                } catch (Exception e) {
                    log.warning("Account '" + account.getName() + "': failed to fetch message " +
                            entry.getKey() + ": " + e.getMessage());
                }
            }

            List<MessageData> newMessages = tracker.filterUnseen(account.getName(), allMessages);
            if (newMessages.isEmpty()) {
                log.info("Account '" + account.getName() + "': no new messages");
                return;
            }

            log.info("Account '" + account.getName() + "': " + newMessages.size() + " new message(s) to forward");

            Set<Integer> successfulNumbers = new HashSet<>();
            forwardMessages(account, newMessages, successfulNumbers);

            if (account.isDeleteAfterCopy() && !successfulNumbers.isEmpty()) {
                for (int seqNum : successfulNumbers) {
                    try {
                        imapIn.deleteMessage(seqNum);
                    } catch (Exception e) {
                        log.warning("Account '" + account.getName() +
                                "': failed to delete message " + seqNum + ": " + e.getMessage());
                    }
                }
                try {
                    imapIn.expunge();
                } catch (Exception e) {
                    log.warning("Account '" + account.getName() +
                            "': failed to expunge: " + e.getMessage());
                }
                log.info("Account '" + account.getName() + "': deleted " +
                        successfulNumbers.size() + " message(s) from IMAP input");
            }

            log.info("Account '" + account.getName() + "': forwarded " +
                    successfulNumbers.size() + "/" + newMessages.size() + " message(s)");

        } catch (Exception e) {
            log.log(Level.SEVERE, "Account '" + account.getName() + "': failed to process: " + e.getMessage(), e);
        }
    }

    private void forwardMessages(AccountConfig account, List<MessageData> messages,
                                 Set<Integer> successfulNumbers) {
        SmtpServerConfig smtpConfig = account.getOutput();
        try (SmtpClient smtp = SmtpClient.connect(smtpConfig)) {
            for (MessageData message : messages) {
                try {
                    smtp.sendMessage(smtpConfig.getUsername(), smtpConfig.getTo(), message.rawContent());
                    tracker.markSeen(account.getName(), message.messageId());
                    successfulNumbers.add(message.messageNumber());
                    log.fine("Account '" + account.getName() + "': forwarded message " + message.messageId());
                } catch (Exception e) {
                    log.log(Level.WARNING, "Account '" + account.getName() +
                            "': failed to forward message " + message.messageId() + ": " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Account '" + account.getName() +
                    "': failed to connect to SMTP server: " + e.getMessage(), e);
        }
    }
}
