# Gmail Forward (Pure Java)

[![Build](https://github.com/pisek/gmail-forward-pure/actions/workflows/build.yml/badge.svg)](https://github.com/pisek/gmail-forward-pure/actions/workflows/build.yml)

A POP3-to-IMAP mail forwarder written in pure Java with zero external dependencies. Periodically fetches emails from POP3 servers and copies them to IMAP servers, preserving original sender (FROM) headers.

## Features

- Multiple POP3-to-IMAP account pairs with independent polling intervals
- Configurable delete-after-copy or leave-on-server mode per account
- Duplicate detection using flat files (persists across restarts)
- Original email headers (FROM, Reply-To, etc.) preserved — raw RFC822 bytes are transferred unchanged
- JSON configuration
- Zero dependencies — only Java standard library
- Tiny footprint (~27KB JAR)

## Requirements

- Java 21+
- Maven 3.9+ (for building)

## Building

```bash
mvn package
```

Produces an executable JAR at `target/gmail-forward-1.0.0.jar`.

## Configuration

Create a `config.json` file (see `config.example.json`):

```json
{
  "db_path": "./data",
  "accounts": [
    {
      "name": "personal-gmail",
      "input": {
        "protocol": "pop3",
        "host": "pop.gmail.com",
        "port": 995,
        "username": "user@gmail.com",
        "password": "your-app-password",
        "ssl": true
      },
      "output": {
        "host": "imap.destination.com",
        "port": 993,
        "username": "user@destination.com",
        "password": "your-imap-password",
        "ssl": true,
        "folder": "INBOX"
      },
      "polling_interval": 300,
      "delete_after_copy": false
    }
  ]
}
```

### Configuration reference

| Property | Description | Default |
|---|---|---|
| `db_path` | Directory for duplicate tracking files | `./data` |
| `name` | Unique account identifier | required |
| `input.protocol` | Input protocol: `pop3` or `imap` | `pop3` |
| `input.host` | Input server hostname | required |
| `input.port` | Input server port | required |
| `input.username` | Input login username | required |
| `input.password` | Input login password | required |
| `input.ssl` | Enable SSL/TLS | `true` |
| `input.folder` | Source IMAP folder (only when protocol is `imap`) | `INBOX` |
| `output.host` | IMAP server hostname | required |
| `output.port` | IMAP server port | required |
| `output.username` | IMAP login username | required |
| `output.password` | IMAP login password | required |
| `output.ssl` | Enable SSL/TLS | `true` |
| `output.folder` | Target IMAP folder | `INBOX` |
| `polling_interval` | Seconds between checks | `300` |
| `delete_after_copy` | Delete messages from input server after copying | `false` |

### Passwords

Passwords are stored directly in `config.json`. Restrict file access to the owner only:

```bash
chmod 600 config.json
```

### Gmail App Passwords

If you use Gmail as a POP3 source, generate a dedicated App Password at https://myaccount.google.com/apppasswords instead of using your main Google account password. App Passwords are single-purpose credentials that bypass 2-Step Verification and can be revoked independently without affecting your main account.

## Running

```bash
java -jar target/gmail-forward-1.0.0.jar --config config.json
```

The `--config` flag defaults to `config.json` in the current directory if omitted.

The application will:
1. Start polling each configured POP3 account at the specified interval
2. Fetch new messages and copy them to the corresponding IMAP server
3. Track processed message IDs in flat files under the `db_path` directory

Stop with Ctrl+C — the application handles SIGINT/SIGTERM gracefully.

## How it works

1. Connects to the POP3 server via raw SSL/TCP socket and fetches message list (UIDL)
2. Downloads each message as raw RFC822 bytes (RETR)
3. Filters out already-seen messages using flat file tracking
4. Connects to the IMAP server via raw SSL/TCP socket and uploads each message (APPEND)
5. All original headers (FROM, Reply-To, Date, etc.) are preserved because the raw bytes are never modified
6. Marks messages as seen and optionally deletes from POP3 (DELE)

## Architecture

Pure Java implementation of POP3 and IMAP protocols using `java.net.Socket` and `javax.net.ssl.SSLSocketFactory`. No Jakarta Mail, no Spring, no external libraries.

| Component | Description |
|---|---|
| `net/Pop3Client` | POP3 protocol: USER, PASS, UIDL, RETR, DELE, QUIT |
| `net/ImapClient` | IMAP protocol: LOGIN, APPEND (with literal continuation), LOGOUT |
| `service/MailForwardService` | Orchestrates fetch, dedup, append, delete |
| `service/DuplicateTracker` | Flat file per account tracking seen message IDs |
| `config/JsonParser` | Recursive descent JSON parser |
