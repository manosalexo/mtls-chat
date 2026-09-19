# mTLS Chat

A multi-threaded chat application demonstrating mutual TLS (mTLS) authentication in Java.

## Requirements

- Java 17+
- Gradle (or use the wrapper)

## Build

```bash
./gradlew build
```

## Certificate Setup

Generate PKCS12 keystores for mutual TLS authentication:

```bash
mkdir keystore && cd keystore

# Generate server keypair
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 \
  -dname "CN=Server,OU=Dev,O=mTLS-Chat,L=Athens,C=GR" \
  -keystore server.p12 -storetype PKCS12 -storepass changeit

# Generate client keypair
keytool -genkeypair -alias client -keyalg RSA -keysize 2048 \
  -dname "CN=Client,OU=Dev,O=mTLS-Chat,L=Athens,C=GR" \
  -keystore client.p12 -storetype PKCS12 -storepass changeit

# Export and import server certificate into client trust store
keytool -exportcert -alias server -keystore server.p12 -storepass changeit -file server.cer
keytool -importcert -alias server -keystore server-trust.p12 -storetype PKCS12 \
  -storepass changeit -file server.cer -noprompt

# Export and import client certificate into server trust store
keytool -exportcert -alias client -keystore client.p12 -storepass changeit -file client.cer
keytool -importcert -alias client -keystore client-trust.p12 -storetype PKCS12 \
  -storepass changeit -file client.cer -noprompt

# Clean up exported certificates
rm -f server.cer client.cer
```

## Run

Start the server:

```bash
./gradlew run
```

Start a client (in a separate terminal):

```bash
./gradlew runClient
```

### Configuration

| Environment Variable | Default    | Description          |
|---------------------|------------|----------------------|
| `KEYSTORE_PASS`     | `changeit` | Keystore password    |

The server accepts an optional port argument (default: 9001):

```bash
./gradlew run --args="9002"
```

## Changes from Legacy Version

- **TLSv1.3** instead of generic TLS (which could negotiate TLS 1.0/1.1)
- **PKCS12** keystores instead of proprietary JKS format
- **Thread pool** (`ExecutorService`) instead of raw `Thread` spawning
- **Concurrent collections** (`ConcurrentHashMap.newKeySet()`) instead of unsynchronized `HashSet`
- **Null-safe** message reading with proper disconnect handling
- **Graceful shutdown** via shutdown hook
- **Configurable** port and keystore password via environment variable
- **Logging** via `java.util.logging` instead of `System.out.println`
- **Gradle** build system
- **Proper project structure** (`src/main/java/ssl/chat/`)
