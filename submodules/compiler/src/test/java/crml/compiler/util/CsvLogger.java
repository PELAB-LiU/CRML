package crml.compiler.util;

import java.io.*;
import java.util.*;

/**
 * A CSV logger that accumulates key-value entries and commits them as rows.
 *
 * Usage:
 *   CsvLogger logger = new CsvLogger("output.csv", List.of("timestamp", "level", "message"));
 *   logger.set("timestamp", "2026-03-27T10:00:00");
 *   logger.set("level", "INFO");
 *   logger.set("message", "Server started");
 *   logger.commit();
 *   logger.close();
 */
public class CsvLogger implements Closeable {

    private final File outputFile;
    private final List<String> headers;
    private final Map<String, String> currentEntry;
    private boolean headerWritten;

    /**
     * Creates a CsvLogger that writes to the specified file with the given column headers.
     *
     * @param filePath path to the output CSV file
     * @param headers  ordered list of column names
     * @throws IOException if the file cannot be created or accessed
     */
    public CsvLogger(String filePath, List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Headers must not be null or empty.");
        }
        this.outputFile = new File(filePath);
        this.headers = Collections.unmodifiableList(new ArrayList<>(headers));
        this.currentEntry = new LinkedHashMap<>();
        this.headerWritten = outputFile.exists() && outputFile.length() > 0;

        // Ensure parent directories exist
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    /**
     * Sets a value for the given key in the current (uncommitted) entry.
     * Keys not in the header list are silently ignored.
     *
     * @param key   column name
     * @param value value to record
     * @return this logger (fluent API)
     */
    public CsvLogger set(String key, String value) {
        if (headers.contains(key)) {
            currentEntry.put(key, value != null ? value : "");
        }
        return this;
    }

    /**
     * Commits the current entry as a new row in the CSV file, then clears
     * the working entry so the next row can be built fresh.
     *
     * @throws IOException if writing fails
     */
    public void commit() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputFile, true))) {

            if (!headerWritten) {
                writer.write(formatRow(headers));
                writer.newLine();
                headerWritten = true;
            }

            List<String> row = new ArrayList<>();
            for (String header : headers) {
                row.add(currentEntry.getOrDefault(header, ""));
            }
            writer.write(formatRow(row));
            writer.newLine();
        }
        currentEntry.clear();
    }

    /**
     * Discards the current working entry without writing it.
     */
    public void discard() {
        currentEntry.clear();
    }

    /**
     * Returns an unmodifiable view of the current (uncommitted) entry.
     */
    public Map<String, String> getCurrentEntry() {
        return Collections.unmodifiableMap(currentEntry);
    }

    /**
     * Returns the list of column headers.
     */
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * No-op close — included so CsvLogger works in try-with-resources blocks.
     * (The writer is opened/closed per commit to support long-lived loggers.)
     */
    @Override
    public void close() {
        // Writer is already closed after each commit; nothing to do here.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Formats a list of values as a single CSV row, quoting fields as needed. */
    private static String formatRow(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsvField(fields.get(i)));
        }
        return sb.toString();
    }

    /**
     * Wraps a field in double-quotes if it contains a comma, double-quote,
     * or newline. Internal double-quotes are escaped by doubling them.
     */
    private static String escapeCsvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}