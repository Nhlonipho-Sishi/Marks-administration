package service;

import java.util.List;

/** Outcome of a CSV import run. */
public class ImportResult {
    public int updated;            // mark cells written
    public int created;            // new students auto-created + enrolled
    public int skipped;            // cells skipped (unknown column / bad value / out of range)
    public final List<String> warnings = new java.util.ArrayList<>();

    public void warn(String w) { warnings.add(w); }
}
