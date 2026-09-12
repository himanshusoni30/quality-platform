package quality.platform.lab.train;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public class TestReservationEndpoints extends TrainApiTestBase {
    private static final Set<Long> ISSUED_PNRS = ConcurrentHashMap.newKeySet();

    private TrainData train;
    private LocalDate journeyDate;
    private Map<String, Object> request;
    private long pnr;

    @BeforeEach
    void setUp() {
        train = faker.options().nextElement(TRAINS);
        journeyDate = freshDate();
        request = reservationRequest(train, journeyDate);

        Response response = book(request);

        Assertions.assertEquals(200, response.statusCode());
        JsonPath jPath = response.jsonPath();
        pnr = jPath.getLong("pnr");
        Assertions.assertEquals(10, String.valueOf(pnr).length(), "PNR should be a 10 digit number");
        Assertions.assertEquals("CONFIRMED", jPath.getString("status"));
    }

    static Stream<Arguments> unavailableTiers() {
        return TRAINS.stream().flatMap(t -> TIERS.stream()
                .filter(tier -> !tier.equals(t.tier()))
                .map(tier -> Arguments.of(t, tier)));
    }

    static Stream<Arguments> unservedRoutes() {
        return TRAINS.stream().flatMap(t -> Stream.of(
                Arguments.of(t, t.to(), t.from()),
                Arguments.of(t, t.from(), "XYZ"),
                Arguments.of(t, "XYZ", t.to())));
    }

    static Stream<Arguments> invalidFieldValues() {
        return Stream.of(
                Arguments.of("trainNumber", "abc"),
                Arguments.of("tier", "SLEEPER"),
                Arguments.of("tier", "first class"),
                Arguments.of("sourceStation", "ND"),
                Arguments.of("sourceStation", "NDLS"),
                Arguments.of("sourceStation", "N1L"),
                Arguments.of("destinationStation", ""),
                Arguments.of("destinationStation", "12A"),
                Arguments.of("journeyDate", "2030-01-15"),
                Arguments.of("journeyDate", "32-01-2030"),
                Arguments.of("journeyDate", "15-13-2030"),
                Arguments.of("journeyDate", LocalDate.now().minusDays(1).format(DATE_FORMAT)),
                Arguments.of("journeyDate", LocalDate.now().minusYears(1).format(DATE_FORMAT)),
                Arguments.of("passenger.firstName", ""),
                Arguments.of("passenger.firstName", "   "),
                Arguments.of("passenger.lastName", ""),
                Arguments.of("passenger.age", 0),
                Arguments.of("passenger.age", -1),
                Arguments.of("passenger.age", 126),
                Arguments.of("passenger.age", "abc"));
    }

    @Test
    void testUserIsAbleToGetReservationByPnr() {
        Response response = getReservation(pnr);

        Assertions.assertEquals(200, response.statusCode());
        JsonPath jPath = response.jsonPath();
        @SuppressWarnings("unchecked")
        Map<String, Object> passenger = (Map<String, Object>) request.get("passenger");
        Assertions.assertEquals(pnr, jPath.getLong("pnr"));
        Assertions.assertEquals(train.number(), jPath.getInt("trainNumber"));
        Assertions.assertEquals(train.tier(), jPath.getString("tier"));
        Assertions.assertEquals(journeyDate.format(DATE_FORMAT), jPath.getString("journeyDate"));
        Assertions.assertEquals(passenger.get("firstName"), jPath.getString("passenger.firstName"));
        Assertions.assertEquals(passenger.get("lastName"), jPath.getString("passenger.lastName"));
        Assertions.assertEquals(passenger.get("age"), jPath.getInt("passenger.age"));
    }

    @ParameterizedTest(name = "book train {0}")
    @MethodSource("trains")
    void testUserIsAbleToBookEachTrain(TrainData train) {
        LocalDate date = freshDate();
        Response response = book(reservationRequest(train, date));

        Assertions.assertEquals(200, response.statusCode());
        JsonPath jPath = response.jsonPath();
        Assertions.assertEquals("CONFIRMED", jPath.getString("status"));
        Assertions.assertEquals(train.number(), jPath.getInt("trainNumber"));
        Assertions.assertEquals(train.tier(), jPath.getString("tier"));
        Assertions.assertTrue(jPath.getString("sourceStation").endsWith("(" + train.from() + ")"));
        Assertions.assertTrue(jPath.getString("destinationStation").endsWith("(" + train.to() + ")"));
        Assertions.assertEquals(date.format(DATE_FORMAT), jPath.getString("journeyDate"));
        Assertions.assertEquals(date, LocalDateTime.parse(jPath.getString("departureTime")).toLocalDate());
        Assertions.assertEquals(train.coachPrefix() + "1", jPath.getString("passenger.coachNumber"));
        Assertions.assertEquals(1, jPath.getInt("passenger.seatNumber"));
    }

    @Test
    void testUserIsAbleToBookForToday() {
        Response response = book(reservationRequest(TRAINS.get(2), LocalDate.now()));

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(LocalDate.now().format(DATE_FORMAT), response.jsonPath().getString("journeyDate"));
    }

    @Test
    void testBookingIsCaseInsensitiveForStationCodes() {
        Map<String, Object> lowerCase = reservationRequest(train, freshDate());
        lowerCase.put("sourceStation", train.from().toLowerCase());
        lowerCase.put("destinationStation", train.to().toLowerCase());

        Response response = book(lowerCase);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.jsonPath().getString("sourceStation").endsWith("(" + train.from() + ")"));
    }

    @ParameterizedTest(name = "booking accepts passenger age {0}")
    @ValueSource(ints = {1, 18, 60, 124, 125})
    void testBookingAcceptsValidPassengerAges(int age) {
        Map<String, Object> body = reservationRequest(train, freshDate());
        withField(body, "passenger.age", age);

        Response response = book(body);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(age, response.jsonPath().getInt("passenger.age"));
    }

    @ParameterizedTest(name = "booking rejects missing {0}")
    @ValueSource(strings = {"trainNumber", "tier", "sourceStation", "destinationStation", "journeyDate", "passenger",
            "passenger.firstName", "passenger.lastName", "passenger.age"})
    void testBookingRejectsMissingField(String field) {
        Map<String, Object> body = reservationRequest(train, freshDate());
        target(body, field).remove(key(field));

        Response response = book(body);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "booking rejects {0} = [{1}]")
    @MethodSource("invalidFieldValues")
    void testBookingRejectsInvalidFieldValue(String field, Object value) {
        Map<String, Object> body = reservationRequest(train, freshDate());
        withField(body, field, value);

        Response response = book(body);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "train {0} rejects tier {1}")
    @MethodSource("unavailableTiers")
    void testBookingRejectsTierNotAvailableOnTrain(TrainData train, String tier) {
        Map<String, Object> body = reservationRequest(train, freshDate());
        body.put("tier", tier);

        Response response = book(body);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "train {0} rejects route {1} -> {2}")
    @MethodSource("unservedRoutes")
    void testBookingRejectsRouteNotServedByTrain(TrainData train, String from, String to) {
        Map<String, Object> body = reservationRequest(train, freshDate());
        body.put("sourceStation", from);
        body.put("destinationStation", to);

        Response response = book(body);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "booking returns 404 for train {0}")
    @ValueSource(ints = {0, 1, 122950, 999999})
    void testBookingReturnsNotFoundForUnknownTrain(int trainNumber) {
        Map<String, Object> body = reservationRequest(train, freshDate());
        body.put("trainNumber", trainNumber);

        Response response = book(body);

        Assertions.assertEquals(404, response.statusCode());
    }

    @ParameterizedTest(name = "get reservation returns 404 for pnr {0}")
    @ValueSource(longs = {1L, 999_999_999L, 99_999_999_999L})
    void testGetReservationReturnsNotFoundForUnknownPnr(long unknownPnr) {
        Response response = getReservation(unknownPnr);

        Assertions.assertEquals(404, response.statusCode());
    }

    @ParameterizedTest(name = "get reservation rejects pnr [{0}]")
    @ValueSource(strings = {"abc", "12.3", "1e9", ""})
    void testGetReservationRejectsInvalidPnr(String invalidPnr) {
        Response response = getReservation(invalidPnr);

        Assertions.assertEquals(400, response.statusCode());
    }

    @Test
    void testGetReservationRejectsMissingPnr() {
        Response response = getReservation(null);

        Assertions.assertEquals(400, response.statusCode());
    }

    @Test
    void testReservationIsPersistedToDataFile() throws IOException {
        String content = Files.readString(Path.of(RESERVATIONS_FILE));

        Assertions.assertTrue(content.contains(String.valueOf(pnr)), "Reservation should be written to the data file");
    }

    @ParameterizedTest(name = "available seats on train {0} decrease after booking")
    @MethodSource("trains")
    void testAvailableSeatsDecreaseAfterBooking(TrainData train) {
        LocalDate date = freshDate();
        String formattedDate = date.format(DATE_FORMAT);
        Assertions.assertEquals(train.totalSeats(), getTrain(train.number(), formattedDate).jsonPath().getInt("availableSeats"));

        Assertions.assertEquals(200, book(reservationRequest(train, date)).statusCode());

        Assertions.assertEquals(train.totalSeats() - 1, getTrain(train.number(), formattedDate).jsonPath().getInt("availableSeats"));
    }

    @RepeatedTest(value = 50, name = "booking {currentRepetition}/{totalRepetitions} gets a unique retrievable PNR")
    void testEveryBookingGetsUniqueRetrievablePnr() {
        TrainData randomTrain = faker.options().nextElement(TRAINS);
        JsonPath booked = book(reservationRequest(randomTrain, freshDate())).jsonPath();
        long newPnr = booked.getLong("pnr");

        Assertions.assertTrue(ISSUED_PNRS.add(newPnr), "PNR " + newPnr + " was issued more than once");
        Assertions.assertNotEquals(pnr, newPnr);

        Response response = getReservation(newPnr);
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals(randomTrain.number(), response.jsonPath().getInt("trainNumber"));
        Assertions.assertEquals(booked.getString("passenger.lastName"), response.jsonPath().getString("passenger.lastName"));
    }

    // each round fills the train on a different date, proving seats are counted per journey date
    static Stream<Arguments> capacityRuns() {
        return Stream.of(1, 2).flatMap(round -> TRAINS.stream().map(t -> Arguments.of(t, round)));
    }

    @ParameterizedTest(name = "train {0} is booked to capacity and then waitlisted (round {1})")
    @MethodSource("capacityRuns")
    void testTrainIsBookedToCapacityAndThenWaitlisted(TrainData train, int round) {
        LocalDate date = freshDate();
        for (int i = 0; i < train.totalSeats(); i++) {
            Response response = book(reservationRequest(train, date));
            Assertions.assertEquals(200, response.statusCode());
            JsonPath jPath = response.jsonPath();
            Assertions.assertEquals("CONFIRMED", jPath.getString("status"));
            Assertions.assertEquals(train.coachPrefix() + (i / train.seatsPerCoach() + 1), jPath.getString("passenger.coachNumber"));
            Assertions.assertEquals(i % train.seatsPerCoach() + 1, jPath.getInt("passenger.seatNumber"));
        }
        Assertions.assertEquals(0, getTrain(train.number(), date.format(DATE_FORMAT)).jsonPath().getInt("availableSeats"));

        Response waitlisted = book(reservationRequest(train, date));

        Assertions.assertEquals(200, waitlisted.statusCode());
        JsonPath jPath = waitlisted.jsonPath();
        Assertions.assertEquals("NOT CONFIRMED", jPath.getString("status"));
        Assertions.assertNull(jPath.get("passenger.coachNumber"));
        Assertions.assertNull(jPath.get("passenger.seatNumber"));
        Assertions.assertEquals("NOT CONFIRMED", getReservation(jPath.getLong("pnr")).jsonPath().getString("status"));
        Assertions.assertEquals(0, getTrain(train.number(), date.format(DATE_FORMAT)).jsonPath().getInt("availableSeats"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> target(Map<String, Object> body, String field) {
        return field.startsWith("passenger.") ? (Map<String, Object>) body.get("passenger") : body;
    }

    private String key(String field) {
        return field.substring(field.indexOf('.') + 1);
    }

    private void withField(Map<String, Object> body, String field, Object value) {
        target(body, field).put(key(field), value);
    }
}
