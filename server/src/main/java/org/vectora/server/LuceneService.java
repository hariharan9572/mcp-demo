package org.vectora.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LuceneService implements Closeable {

    private final Directory directory;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    private final Analyzer analyzer;
    private final ObjectMapper objectMapper;

    public LuceneService(Path indexPath) throws IOException {
        this.directory = FSDirectory.open(indexPath);
        this.reader = DirectoryReader.open(directory);
        this.searcher = new IndexSearcher(reader);
        this.analyzer = new StandardAnalyzer();
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> listTables() throws IOException {
        Query query = new TermQuery(new Term("doc_type", "table_meta"));
        TopDocs docs = searcher.search(query, 1000);
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScoreDoc hit : docs.scoreDocs) {
            Document doc = searcher.doc(hit.doc);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("table", doc.get("table"));
            row.put("primary_key", doc.get("primary_key"));
            row.put("row_count", numericValue(doc, "row_count"));
            row.put("has_created_at", Boolean.parseBoolean(doc.get("has_created_at")));
            results.add(row);
        }
        return results;
    }

    public Map<String, Object> search(String queryString, String table,
                                      Long createdFrom, Long createdTo, int limit) throws Exception {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("doc_type", "row")), BooleanClause.Occur.FILTER);
        if (table != null && !table.isBlank()) {
            builder.add(new TermQuery(new Term("table", table)), BooleanClause.Occur.FILTER);
        }

        Query query;
        if (queryString == null || queryString.isBlank()) {
            query = new MatchAllDocsQuery();
        } else {
            QueryParser parser = new QueryParser("content", analyzer);
            query = parser.parse(queryString);
        }
        builder.add(query, BooleanClause.Occur.MUST);

        if (createdFrom != null || createdTo != null) {
            long from = createdFrom == null ? Long.MIN_VALUE : createdFrom;
            long to = createdTo == null ? Long.MAX_VALUE : createdTo;
            builder.add(LongPoint.newRangeQuery("created_at_epoch", from, to), BooleanClause.Occur.FILTER);
        }

        int cappedLimit = Math.max(1, Math.min(limit, 500));
        TopDocs docs = searcher.search(builder.build(), cappedLimit);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoreDoc hit : docs.scoreDocs) {
            Document doc = searcher.doc(hit.doc);
            rows.add(buildRow(doc));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", queryString == null ? "" : queryString);
        response.put("table", table);
        response.put("count", docs.totalHits.value);
        response.put("limit", cappedLimit);
        response.put("results", rows);
        return response;
    }

    public Optional<Map<String, Object>> lookup(String table, String id) throws IOException {
        if (table == null || table.isBlank() || id == null || id.isBlank()) {
            return Optional.empty();
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("doc_type", "row")), BooleanClause.Occur.FILTER);
        builder.add(new TermQuery(new Term("table", table)), BooleanClause.Occur.FILTER);
        builder.add(new TermQuery(new Term("id", id)), BooleanClause.Occur.FILTER);
        TopDocs docs = searcher.search(builder.build(), 1);
        if (docs.scoreDocs.length == 0) {
            return Optional.empty();
        }
        Document doc = searcher.doc(docs.scoreDocs[0].doc);
        return Optional.of(buildRow(doc));
    }

    public static Long parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return Long.parseLong(trimmed);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        reader.close();
        directory.close();
        analyzer.close();
    }

    private Map<String, Object> buildRow(Document doc) throws IOException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("table", doc.get("table"));
        row.put("id", doc.get("id"));
        row.put("created_at", doc.get("created_at"));
        String dataJson = doc.get("data");
        if (dataJson != null) {
            Map<String, Object> data = objectMapper.readValue(dataJson, new TypeReference<>() {});
            row.put("data", data);
        }
        return row;
    }

    private Long numericValue(Document doc, String field) {
        if (doc == null || field == null) {
            return null;
        }
        var stored = doc.getField(field);
        if (stored != null && stored.numericValue() != null) {
            return stored.numericValue().longValue();
        }
        String value = doc.get(field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
