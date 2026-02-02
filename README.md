# MCP Ping-Pong Demo

This project is a simple Model Context Protocol (MCP) ping/pong demo that runs an HTTP/SSE server on a port and a client that calls the server tool. The server responds with `pong` and includes the server time in the response (for example, `pong @ 2026-02-02T14:22:10.123-05:00`).

## Prerequisites

- Java 25 (as configured in `pom.xml`)
- Maven 3.9+

## Build

The commands below use a local Maven repository in `/tmp/m2` to avoid permission issues with the default `~/.m2` directory.

```bash
mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 compile
```

## Run the server

```bash
mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 \
  compile exec:java \
  -Dexec.mainClass=org.example.server.PingPongServer \
  -Dexec.args=8080
```

You can pass a different port number as the argument. If no argument is provided, the server uses port `8080`. You can also set the `PORT` environment variable.

## Run the client

```bash
mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 \
  compile exec:java \
  -Dexec.mainClass=org.example.client.PingClient \
  -Dexec.args=ping
```

The client calls the `ping` tool and prints the server response (including the server time).

### Client arguments

If you provide a single argument, it can be either:

- a message (e.g., `ping`)
- a port number (e.g., `8080`)
- a full base URL (e.g., `http://localhost:8080`)

To pass both a message and base URL:

```bash
mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 \
  compile exec:java \
  -Dexec.mainClass=org.example.client.PingClient \
  -Dexec.args="ping http://localhost:8080"
```

You can also pass a message and a port number:

```bash
mvn -q -DskipTests -Dmaven.repo.local=/tmp/m2 \
  compile exec:java \
  -Dexec.mainClass=org.example.client.PingClient \
  -Dexec.args="ping 8080"
```

## Notes

- If you see a warning about `sun.misc.Unsafe`, it comes from Maven/Guice and does not block the demo.
- If you run `exec:java` without `compile`, Maven may execute stale classes. Use `compile exec:java` as shown above.
