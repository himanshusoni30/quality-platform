package quality.platform.lab;

import java.util.UUID;

public final class TestDataFiles {
    public static final String RUN_ID = UUID.randomUUID().toString();
    public static final String BOOKS_FILE = "target/test-data/books-" + RUN_ID + ".json";
    public static final String RESERVATIONS_FILE = "target/test-data/reservations-" + RUN_ID + ".json";

    private TestDataFiles() {
    }
}
