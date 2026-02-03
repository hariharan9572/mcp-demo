# Lucene + MySQL MCP Server

This project indexes MySQL tables into Lucene and serves search/lookup APIs from Lucene only. It also exposes MCP tools (including the original ping/pong/ding/dong tools plus table-specific search/lookup tools) alongside the REST endpoints.

## Config

Create or edit `config.yaml` in the repo root:

```yaml
server:
  host: 0.0.0.0
  port: 8080

lucene:
  index_path: "./data/index"

mysql:
  host: 127.0.0.1
  port: 3306
  database: droptruck_db
  username: web_app_sa
  password: web_app_sa
```

## Ingest (Build the Index)

Run once to build the Lucene index:

```bash
mvn -f server/pom.xml clean compile exec:java \
  -Dexec.mainClass=org.vectora.server.McpServerApp \
  -Dexec.args="--config ./config.yaml --ingest"
```

This will:

- read MySQL tables
- write a Lucene index into `data/index/`

## Run Server (No Ingest)

After the index exists:

```bash
mvn -f server/pom.xml clean compile exec:java \
  -Dexec.mainClass=org.vectora.server.McpServerApp \
  -Dexec.args="--config ./config.yaml"
```

If the index is missing, the server will auto-ingest on startup.

## HTTP Endpoints

List tools:

```bash
curl http://localhost:8080/tools
```

Health:

```bash
curl http://localhost:8080/health
```

Search:

```bash
curl "http://localhost:8080/search?query=your+search+terms"
```

Optional search filters:

- `table` (e.g. `&table=employees`)
- `created_at_from` / `created_at_to` (ISO-8601 or epoch millis)
- `limit` (default 50, max 500)

Examples:

```bash
# basic keyword search
curl "http://localhost:8080/search?query=truck"

# table-specific search
curl "http://localhost:8080/search?table=indents&query=truck"

# last 3 months of `indents` (adjust the date as needed)
curl "http://localhost:8080/search?table=indents&query=&created_at_from=2025-11-03T00:00:00Z"

# explicit date range
curl "http://localhost:8080/search?table=indents&query=&created_at_from=2026-01-01T00:00:00Z&created_at_to=2026-02-01T00:00:00Z"

# epoch millis date range
curl "http://localhost:8080/search?table=indents&query=&created_at_from=1704067200000&created_at_to=1706745600000"

# limit results
curl "http://localhost:8080/search?table=indents&query=truck&limit=25"
```

Row Lookup (any table):

```bash
curl http://localhost:8080/{table}/{id}
```

## Notes

- All responses are served from Lucene only.
- Re-ingest whenever DB/app code changes.
- If port 8080 is in use, change `server.port` in `config.yaml`.
- MCP transport is exposed under `/mcp` (e.g. `http://localhost:8080/mcp`).
