package service;

import java.nio.file.*;
import java.util.concurrent.*;

/**
 * Runs CSV import/export on a background thread pool so the console stays
 * responsive (Concurrency requirement). The calling thread can poll isDone()
 * and print progress dots.
 */
public class CsvService {
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final MarksService marks = new MarksService();

    public Future<ImportResult> importAsync(int moduleId, Path file, int userId) {
        return pool.submit(() -> marks.importCsv(moduleId, file, userId));
    }

    public Future<Path> exportAsync(int moduleId, Path target, int userId) {
        return pool.submit(() -> marks.exportCsv(moduleId, target, userId));
    }

    public void shutdown() {
        pool.shutdown();
    }
}
