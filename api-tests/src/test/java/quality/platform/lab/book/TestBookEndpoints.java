package quality.platform.lab.book;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.datafaker.Faker;
import quality.platform.lab.TestDataFiles;
import quality.platform.lab.dto.Book;
import quality.platform.lab.dto.BookDetails;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TestBookEndpoints {
    private static final String BOOKS_FILE = TestDataFiles.BOOKS_FILE;

    private static final Faker faker = new Faker();
    private Book book;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void bookStore(DynamicPropertyRegistry registry) {
        registry.add("books.data-file", () -> BOOKS_FILE);
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        book = newBook(uniqueAuthor());

        Response response = create(book);

        Assertions.assertEquals(201, response.statusCode());
        book = response.as(Book.class);
        Assertions.assertNotNull(book.getId());
    }

    @AfterEach
    void cleanUp() {
        // tests may already have deleted the book, so the status is not asserted
        delete(book.getId());
    }

    @Test
    void testUserIsAbleToGetBookById() {
        Response response = getBook(book.getId());

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(book, response.as(Book.class));
    }

    @RepeatedTest(value = 10, name = "unknown book id {currentRepetition}/{totalRepetitions} returns 404")
    void testGetBookReturnsNotFoundForUnknownId() {
        Assertions.assertEquals(404, getBook(UUID.randomUUID()).statusCode());
    }

    @ParameterizedTest(name = "get book rejects id [{0}]")
    @ValueSource(strings = {"abc", "123", "not-a-uuid", "00000000-0000-0000-0000", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz"})
    void testGetBookRejectsInvalidId(String id) {
        Assertions.assertEquals(400, getBook(id).statusCode());
    }

    @Test
    void testUserIsAbleToCreateBookWithOwnId() {
        Book withId = newBook(uniqueAuthor());
        withId.setId(UUID.randomUUID());

        Response response = create(withId);

        Assertions.assertEquals(201, response.statusCode());
        Assertions.assertEquals(withId.getId(), response.as(Book.class).getId());
        delete(withId.getId());
    }

    @Test
    void testCreateBookReturnsConflictForDuplicateId() {
        Book duplicate = newBook(uniqueAuthor());
        duplicate.setId(book.getId());

        Assertions.assertEquals(409, create(duplicate).statusCode());
        Assertions.assertEquals(book.getTitle(), getBook(book.getId()).as(Book.class).getTitle(),
                "Existing book should not be overwritten");
    }

    @ParameterizedTest(name = "price {0} is stored as {1}")
    @CsvSource({"10.005, 10.01", "10.004, 10.00", "9.999, 10.00", "0.1, 0.10", "15, 15.00", "19.994999, 19.99"})
    void testCreateBookScalesPriceToTwoDecimals(String input, String expected) {
        Book priced = newBook(uniqueAuthor());
        priced.setPrice(new BigDecimal(input));

        Book created = create(priced).as(Book.class);

        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(created.getPrice()));
        delete(created.getId());
    }

    @Test
    void testUserIsAbleToCreateBookWithoutOptionalFields() {
        Response response = create(Map.of("title", "Untitled " + UUID.randomUUID()));

        Assertions.assertEquals(201, response.statusCode());
        Book created = response.as(Book.class);
        Assertions.assertNull(created.getPrice());
        Assertions.assertNull(created.getDetails());
        Assertions.assertNull(created.getAuthor());
        delete(created.getId());
    }

    @ParameterizedTest(name = "create rejects body [{0}]")
    @ValueSource(strings = {"{", "not json", "{\"id\": \"abc\"}", "{\"price\": \"abc\"}", "{\"details\": {\"pages\": \"many\"}}",
            "{\"details\": {\"review\": \"good\"}}", "{\"details\": \"text\"}"})
    void testCreateBookRejectsMalformedBody(String body) {
        Response response = RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("book").
            body(body).
            when().
            post().
            then().extract().response();

        Assertions.assertEquals(400, response.statusCode());
    }

    @Test
    void testCreateBookRejectsUnsupportedMediaType() {
        Response response = RestAssured.
            given().
            contentType(ContentType.TEXT).
            basePath("book").
            body("title=abc").
            when().
            post().
            then().extract().response();

        Assertions.assertEquals(415, response.statusCode());
    }

    @Test
    void testUserIsAbleToGetAllBooks() {
        List<Book> books = search(null, null).as(new TypeRef<List<Book>>(){});

        Assertions.assertTrue(books.stream().anyMatch(b -> b.getId().equals(book.getId())));
    }

    @ParameterizedTest(name = "search by author is case insensitive ({0})")
    @ValueSource(strings = {"exact", "upper", "lower"})
    void testSearchByAuthorIsCaseInsensitive(String casing) {
        List<Book> books = search(withCasing(book.getAuthor(), casing), null).as(new TypeRef<List<Book>>(){});

        Assertions.assertEquals(1, books.size());
        Assertions.assertEquals(book.getId(), books.get(0).getId());
    }

    @ParameterizedTest(name = "search by title is case insensitive ({0})")
    @ValueSource(strings = {"exact", "upper", "lower"})
    void testSearchByTitleIsCaseInsensitive(String casing) {
        List<Book> books = search(null, withCasing(book.getTitle(), casing)).as(new TypeRef<List<Book>>(){});

        Assertions.assertEquals(1, books.size());
        Assertions.assertEquals(book.getId(), books.get(0).getId());
    }

    @Test
    void testUserIsAbleToSearchByAuthorAndTitle() {
        List<Book> books = search(book.getAuthor(), book.getTitle()).as(new TypeRef<List<Book>>(){});

        Assertions.assertEquals(1, books.size());
        Assertions.assertEquals(book.getId(), books.get(0).getId());
    }

    @ParameterizedTest(name = "search returns nothing for author [{0}] and title [{1}]")
    @CsvSource({"MATCH, Unknown Title", "Unknown Author, MATCH", "Unknown Author, ", ", Unknown Title", "PARTIAL, ", ", PARTIAL"})
    void testSearchReturnsEmptyListWhenNothingMatches(String author, String title) {
        List<Book> books = search(resolve(author, book.getAuthor()), resolve(title, book.getTitle()))
                .as(new TypeRef<List<Book>>(){});

        Assertions.assertTrue(books.isEmpty());
    }

    @ParameterizedTest(name = "patch price {0} is stored as {1}")
    @CsvSource({"25.5, 25.50", "0.005, 0.01", "99.999, 100.00", "7, 7.00"})
    void testUserIsAbleToPatchPrice(String input, String expected) {
        Response response = patch(book.getId(), Map.of("price", new BigDecimal(input)));

        Assertions.assertEquals(200, response.statusCode());
        Book patched = response.as(Book.class);
        Assertions.assertEquals(0, new BigDecimal(expected).compareTo(patched.getPrice()));
        Assertions.assertEquals(book.getTitle(), patched.getTitle());
        Assertions.assertEquals(book.getDetails(), patched.getDetails());
    }

    @ParameterizedTest(name = "patch only details.{0}")
    @CsvSource({"about, Patched about text", "review, 5", "pages, 999", "edition, 3.5"})
    void testUserIsAbleToPatchSingleDetailField(String field, String value) {
        Object typedValue = switch (field) {
            case "review", "pages" -> Integer.valueOf(value);
            case "edition" -> Double.valueOf(value);
            default -> value;
        };

        Response response = patch(book.getId(), Map.of("details", Map.of(field, typedValue)));

        Assertions.assertEquals(200, response.statusCode());
        BookDetails expected = book.getDetails();
        switch (field) {
            case "about" -> expected.setAbout(value);
            case "review" -> expected.setReview((Integer) typedValue);
            case "pages" -> expected.setPages((Integer) typedValue);
            default -> expected.setEdition((Double) typedValue);
        }
        Assertions.assertEquals(expected, response.as(Book.class).getDetails());
        Assertions.assertEquals(expected, getBook(book.getId()).as(Book.class).getDetails(), "Patch should be stored");
    }

    @Test
    void testPatchWithEmptyBodyLeavesBookUnchanged() {
        Response response = patch(book.getId(), Map.of());

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(book, response.as(Book.class));
    }

    @Test
    void testPatchIgnoresTitleAndAuthor() {
        Response response = patch(book.getId(), Map.of("title", "Changed", "author", "Changed"));

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(book.getTitle(), response.as(Book.class).getTitle());
        Assertions.assertEquals(book.getAuthor(), response.as(Book.class).getAuthor());
    }

    @Test
    void testPatchAddsDetailsToBookWithoutDetails() {
        Book created = create(Map.of("title", "No details " + UUID.randomUUID())).as(Book.class);

        Response response = patch(created.getId(), Map.of("details", Map.of("pages", 120)));

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(new BookDetails(null, null, 120, null), response.as(Book.class).getDetails());
        delete(created.getId());
    }

    @Test
    void testPatchReturnsNotFoundForUnknownId() {
        Assertions.assertEquals(404, patch(UUID.randomUUID(), Map.of("price", 10)).statusCode());
    }

    @ParameterizedTest(name = "patch rejects id [{0}]")
    @ValueSource(strings = {"abc", "123"})
    void testPatchRejectsInvalidId(String id) {
        Assertions.assertEquals(400, patch(id, Map.of("price", 10)).statusCode());
    }

    @Test
    void testUserIsAbleToDeleteBook() {
        Assertions.assertEquals(200, delete(book.getId()).statusCode());
        Assertions.assertEquals(404, getBook(book.getId()).statusCode());
        Assertions.assertEquals(404, delete(book.getId()).statusCode(), "Deleting twice should return 404");
    }

    @ParameterizedTest(name = "delete rejects id [{0}]")
    @ValueSource(strings = {"abc", "123"})
    void testDeleteRejectsInvalidId(String id) {
        Assertions.assertEquals(400, delete(id).statusCode());
    }

    @Test
    void testBookIsPersistedToDataFile() throws IOException {
        Assertions.assertTrue(Files.readString(Path.of(BOOKS_FILE)).contains(book.getId().toString()));
    }

    @RepeatedTest(value = 50, name = "book lifecycle {currentRepetition}/{totalRepetitions}")
    void testBookLifecycle() {
        Book created = create(newBook(uniqueAuthor())).as(Book.class);

        Assertions.assertEquals(created, getBook(created.getId()).as(Book.class));
        Assertions.assertEquals(200, patch(created.getId(), Map.of("price", 1.5)).statusCode());
        Assertions.assertEquals(0, new BigDecimal("1.50").compareTo(getBook(created.getId()).as(Book.class).getPrice()));
        Assertions.assertEquals(200, delete(created.getId()).statusCode());
        Assertions.assertEquals(404, getBook(created.getId()).statusCode());
    }

    @ParameterizedTest(name = "bulk create, search and delete {0} books")
    @ValueSource(ints = {100, 200})
    void testBulkCreateSearchAndDelete(int count) {
        String author = uniqueAuthor();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Response response = create(newBook(author));
            Assertions.assertEquals(201, response.statusCode());
            ids.add(response.as(Book.class).getId());
        }

        Assertions.assertEquals(count, search(author, null).as(new TypeRef<List<Book>>(){}).size());

        ids.forEach(id -> Assertions.assertEquals(200, delete(id).statusCode()));
        Assertions.assertTrue(search(author, null).as(new TypeRef<List<Book>>(){}).isEmpty());
    }

    private Book newBook(String author) {
        BookDetails details = new BookDetails(faker.lorem().sentence(), faker.random().nextInt(1, 5),
                faker.random().nextInt(100, 800), faker.random().nextDouble(0.0, 5.0));
        return new Book(null, faker.book().title() + " " + UUID.randomUUID(),
                new BigDecimal(faker.random().nextDouble(4.99, 19.99)), author, details);
    }

    private String uniqueAuthor() {
        return faker.book().author() + " " + UUID.randomUUID();
    }

    private String withCasing(String value, String casing) {
        return switch (casing) {
            case "upper" -> value.toUpperCase();
            case "lower" -> value.toLowerCase();
            default -> value;
        };
    }

    private String resolve(String value, String actual) {
        if ("MATCH".equals(value)) return actual;
        if ("PARTIAL".equals(value)) return actual.substring(0, actual.length() / 2);
        return value;
    }

    private Response create(Object body) {
        return RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("book").
            body(body).
            when().
            post().
            then().extract().response();
    }

    private Response getBook(Object id) {
        return RestAssured.given().contentType(ContentType.JSON).basePath("book/" + id).when().get().then().extract().response();
    }

    private Response patch(Object id, Map<String, ?> body) {
        return RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("book/" + id).
            body(new LinkedHashMap<>(body)).
            when().
            patch().
            then().extract().response();
    }

    private Response delete(Object id) {
        return RestAssured.given().contentType(ContentType.JSON).basePath("book/" + id).when().delete().then().extract().response();
    }

    private Response search(String author, String title) {
        RequestSpecification spec = RestAssured.given().contentType(ContentType.JSON).basePath("books");
        if (author != null) spec.queryParam("author", author);
        if (title != null) spec.queryParam("title", title);
        return spec.when().get().then().extract().response();
    }
}
