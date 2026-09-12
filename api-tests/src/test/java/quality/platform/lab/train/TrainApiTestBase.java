package quality.platform.lab.train;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import net.datafaker.Faker;
import quality.platform.lab.TestDataFiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class TrainApiTestBase {
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    static final String RESERVATIONS_FILE = TestDataFiles.RESERVATIONS_FILE;
    static final List<String> TIERS = List.of("FIRST CLASS", "AC 2 TIER", "AC 3 TIER", "GENERAL");
    static final List<TrainData> TRAINS = List.of(
            new TrainData(122951, "FIRST CLASS", "H", "NDL", "MUM", 10, 240),
            new TrainData(122952, "AC 2 TIER", "A", "NDL", "MUM", 12, 480),
            new TrainData(112903, "GENERAL", "G", "NDL", "MUM", 20, 1200),
            new TrainData(122953, "AC 3 TIER", "B", "MUM", "NDL", 16, 1024),
            new TrainData(126027, "AC 2 TIER", "A", "BLR", "CHN", 10, 10),
            new TrainData(112658, "AC 3 TIER", "B", "CHN", "BLR", 14, 896),
            new TrainData(112302, "FIRST CLASS", "H", "KOL", "NDL", 11, 264),
            new TrainData(154801, "GENERAL", "G", "NDL", "JPR", 18, 1080));

    // every test that depends on seat counts books on its own date so tests never share seats
    private static final AtomicInteger DAY_OFFSET = new AtomicInteger(30);

    record TrainData(int number, String tier, String coachPrefix, String from, String to, int coaches, int totalSeats) {
        int seatsPerCoach() {
            return (int) Math.ceil((double) totalSeats / coaches);
        }

        @Override
        public String toString() {
            return String.valueOf(number);
        }
    }

    Faker faker = new Faker();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void reservationStore(DynamicPropertyRegistry registry) {
        registry.add("reservations.data-file", () -> RESERVATIONS_FILE);
    }

    @BeforeEach
    void configurePort() {
        RestAssured.port = port;
    }

    static Stream<TrainData> trains() {
        return TRAINS.stream();
    }

    static LocalDate freshDate() {
        return LocalDate.now().plusDays(DAY_OFFSET.getAndIncrement());
    }

    Map<String, Object> reservationRequest(TrainData train, LocalDate journeyDate) {
        Map<String, Object> passenger = new LinkedHashMap<>();
        passenger.put("firstName", faker.name().firstName());
        passenger.put("lastName", faker.name().lastName());
        passenger.put("age", faker.random().nextInt(1, 125));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("trainNumber", train.number());
        request.put("tier", train.tier());
        request.put("sourceStation", train.from());
        request.put("destinationStation", train.to());
        request.put("journeyDate", journeyDate.format(DATE_FORMAT));
        request.put("passenger", passenger);
        return request;
    }

    Response book(Map<String, Object> request) {
        return RestAssured.
            given().
            contentType(ContentType.JSON).
            basePath("reservation").
            body(request).
            when().
            post().
            then().extract().response();
    }

    Response getReservation(Object pnr) {
        RequestSpecification spec = RestAssured.given().contentType(ContentType.JSON).basePath("reservation");
        if (pnr != null) spec.queryParam("pnr", pnr);
        return spec.when().get().then().extract().response();
    }

    Response getTrain(Object id, String journeyDate) {
        RequestSpecification spec = RestAssured.given().contentType(ContentType.JSON).basePath("train");
        if (id != null) spec.queryParam("id", id);
        if (journeyDate != null) spec.queryParam("journeyDate", journeyDate);
        return spec.when().get().then().extract().response();
    }

    Response searchTrains(String fromStation, String toStation, String startDate) {
        RequestSpecification spec = RestAssured.given().contentType(ContentType.JSON).basePath("trains");
        if (fromStation != null) spec.queryParam("fromStation", fromStation);
        if (toStation != null) spec.queryParam("toStation", toStation);
        if (startDate != null) spec.queryParam("startDate", startDate);
        return spec.when().get().then().extract().response();
    }
}
