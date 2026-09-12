package quality.platform.lab.book;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import net.datafaker.Faker;
import quality.platform.lab.dto.BookDetails;
import quality.platform.lab.dto.Book;
import io.restassured.path.json.JsonPath;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "books.data-file=target/test-data/books.json")
public class TestCrudOpsInBookEntity {
    Faker faker = new Faker();
    BookDetails bookDetails = new BookDetails(faker.book().title(), faker.random().nextInt(1,5), faker.random().nextInt(100, 800), faker.random().nextDouble(0.0, 5.0));
    Book book = new Book(null, faker.book().title(), new BigDecimal(faker.random().nextDouble(4.99, 19.99)), faker.book().author(), bookDetails);

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        Response response = RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("book").
            body(book).
            when().
            post().
            then().extract().response();

        Assertions.assertEquals(response.statusCode(), 201);
        JsonPath jPath = response.jsonPath();
        Assertions.assertEquals(jPath.getString("title"), book.getTitle());
        Assertions.assertDoesNotThrow(() -> UUID.fromString(jPath.getString("id")), 
            "The returned ID should be a valid UUID format");
    }

    @Test 
    void testUserIsAbleGetBookRecords(){
        Response response = RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("books").
            when().
            get().
            then().extract().response();

        List<Book> books = response.as(new TypeRef<List<Book>>(){});

        Assertions.assertEquals(response.statusCode(), 200);
        Assertions.assertTrue(books.size() >= 1);
    }

    @Test 
    void testUserIsAbleGetBookRecordByAuthor(){
        Response response = RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("books").
            queryParam("author", book.getAuthor()).
            when().
            get().
            then().extract().response();

        List<Book> books = response.as(new TypeRef<List<Book>>(){});

        Assertions.assertEquals(response.statusCode(), 200);
        Assertions.assertTrue(books.size() >= 1);
        books.forEach(b -> Assertions.assertEquals(b.getAuthor(), book.getAuthor()));
    }

    @Test 
    void testUserIsAbleGetBookRecordByTitle(){
        Response response = RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("books").
            queryParam("title", book.getTitle()).
            when().
            get().
            then().extract().response();

        List<Book> books = response.as(new TypeRef<List<Book>>(){});

        Assertions.assertEquals(response.statusCode(), 200);
        Assertions.assertTrue(books.size() >= 1);
        books.forEach(b -> Assertions.assertEquals(b.getTitle(), book.getTitle()));
    }

    @AfterAll
    static void cleanUpBookRecords(){
        List<Book> books = RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("books").
            when().
            get().
            then().extract().response().as(new TypeRef<List<Book>>(){});
        
        books.forEach(b -> RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("book/"+b.getId().toString()).
            when().
            delete().
            then().statusCode(200));
    }
}
