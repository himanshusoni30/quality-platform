package quality.platform.lab.train;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public class TestTrainEndpoints extends TrainApiTestBase {

    static IntStream upcomingDays() {
        return IntStream.rangeClosed(0, 90);
    }

    @ParameterizedTest(name = "search {0} -> {1} returns {2} train(s)")
    @CsvSource({"NDL, MUM, 3", "MUM, NDL, 1", "BLR, CHN, 1", "CHN, BLR, 1", "KOL, NDL, 1", "NDL, JPR, 1"})
    void testUserIsAbleToSearchTrainsBetweenStations(String from, String to, int expectedCount) {
        LocalDate date = LocalDate.now().plusDays(1);
        Response response = searchTrains(from, to, date.format(DATE_FORMAT));

        Assertions.assertEquals(200, response.statusCode());
        List<Map<String, Object>> trains = response.jsonPath().getList("$");
        Assertions.assertEquals(expectedCount, trains.size());

        List<LocalDateTime> departures = trains.stream()
                .map(t -> LocalDateTime.parse((String) t.get("trainDepartureTime"))).toList();
        Assertions.assertEquals(departures.stream().sorted().toList(), departures, "Trains should be sorted by departure time");

        trains.forEach(t -> {
            Assertions.assertTrue(((String) t.get("sourceStation")).endsWith("(" + from + ")"));
            Assertions.assertTrue(((String) t.get("destinationStation")).endsWith("(" + to + ")"));
            Assertions.assertFalse(t.containsKey("totalSeats"), "Search should not expose totalSeats");
            Assertions.assertTrue((Integer) t.get("availableSeats") >= 0);
            Assertions.assertEquals(date, LocalDateTime.parse((String) t.get("trainDepartureTime")).toLocalDate());
        });
    }

    @ParameterizedTest(name = "search is case insensitive for {0} -> {1}")
    @CsvSource({"ndl, mum", "Ndl, MuM", "mum, ndl", "blr, CHN", "KOL, ndl"})
    void testSearchIsCaseInsensitive(String from, String to) {
        Response response = searchTrains(from, to, LocalDate.now().plusDays(2).format(DATE_FORMAT));

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertFalse(response.jsonPath().getList("$").isEmpty());
    }

    @ParameterizedTest(name = "search {0} -> {1} returns no trains")
    @CsvSource({"JPR, NDL", "MUM, BLR", "CHN, KOL", "ABC, XYZ", "NDL, KOL"})
    void testSearchReturnsEmptyListForRouteWithoutTrains(String from, String to) {
        Response response = searchTrains(from, to, LocalDate.now().plusDays(3).format(DATE_FORMAT));

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.jsonPath().getList("$").isEmpty());
    }

    @ParameterizedTest(name = "search NDL -> MUM {0} day(s) from today")
    @MethodSource("upcomingDays")
    void testUserIsAbleToSearchTrainsForUpcomingDates(int daysFromToday) {
        LocalDate date = LocalDate.now().plusDays(daysFromToday);
        Response response = searchTrains("NDL", "MUM", date.format(DATE_FORMAT));

        Assertions.assertEquals(200, response.statusCode());
        List<Map<String, Object>> trains = response.jsonPath().getList("$");
        Assertions.assertEquals(3, trains.size());
        trains.forEach(t -> {
            LocalDateTime departure = LocalDateTime.parse((String) t.get("trainDepartureTime"));
            LocalDateTime arrival = LocalDateTime.parse((String) t.get("trainArrivalTime"));
            double journeyTime = ((Number) t.get("journeyTime")).doubleValue();
            Assertions.assertEquals(date, departure.toLocalDate());
            Assertions.assertEquals(departure.plusMinutes(Math.round(journeyTime * 60)), arrival);
        });
    }

    @ParameterizedTest(name = "search rejects station codes [{0}] -> [{1}]")
    @CsvSource({"ND, MUM", "NDLS, MUM", "N1L, MUM", "123, MUM", "'', MUM", "NDL, MU", "NDL, MUMB", "NDL, M@M", "NDL, ''"})
    void testSearchRejectsInvalidStationCodes(String from, String to) {
        Response response = searchTrains(from, to, LocalDate.now().plusDays(1).format(DATE_FORMAT));

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "search rejects same stations {0} -> {1}")
    @CsvSource({"NDL, NDL", "ndl, NDL", "MUM, mum"})
    void testSearchRejectsSameSourceAndDestination(String from, String to) {
        Response response = searchTrains(from, to, LocalDate.now().plusDays(1).format(DATE_FORMAT));

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "search rejects date [{0}]")
    @ValueSource(strings = {"2030-01-15", "15/01/2030", "15-1-2030", "32-01-2030", "15-13-2030", "29-02-2031", "abc", ""})
    void testSearchRejectsInvalidDateFormats(String startDate) {
        Response response = searchTrains("NDL", "MUM", startDate);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "search rejects date {0} day(s) in the past")
    @ValueSource(ints = {1, 7, 30, 365})
    void testSearchRejectsPastDates(int daysAgo) {
        Response response = searchTrains("NDL", "MUM", LocalDate.now().minusDays(daysAgo).format(DATE_FORMAT));

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "search rejects missing parameters [{0}, {1}, {2}]")
    @CsvSource({", MUM, 15-01-2030", "NDL, , 15-01-2030", "NDL, MUM, ", ", , "})
    void testSearchRejectsMissingParameters(String from, String to, String startDate) {
        Response response = searchTrains(from, to, startDate);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "get train {0}")
    @MethodSource("trains")
    void testUserIsAbleToGetTrainById(TrainData train) {
        Response response = getTrain(train.number(), null);

        Assertions.assertEquals(200, response.statusCode());
        JsonPath jPath = response.jsonPath();
        Assertions.assertEquals(train.number(), jPath.getInt("trainNumber"));
        Assertions.assertEquals(train.tier(), jPath.getString("tier"));
        Assertions.assertEquals(train.coaches(), jPath.getInt("numberOfCoaches"));
        Assertions.assertEquals(train.totalSeats(), jPath.getInt("totalSeats"));
        Assertions.assertTrue(jPath.getInt("availableSeats") <= train.totalSeats());
        Assertions.assertTrue(jPath.getString("sourceStation").endsWith("(" + train.from() + ")"));
        Assertions.assertTrue(jPath.getString("destinationStation").endsWith("(" + train.to() + ")"));
        Assertions.assertNotNull(jPath.getString("trainName"));
        Assertions.assertNotNull(jPath.getString("trainSpeed"));
        Assertions.assertEquals(LocalDate.now(), LocalDateTime.parse(jPath.getString("trainDepartureTime")).toLocalDate(),
                "Journey date should default to today");
    }

    @ParameterizedTest(name = "get train {0} for a journey date")
    @MethodSource("trains")
    void testUserIsAbleToGetTrainForJourneyDate(TrainData train) {
        LocalDate date = freshDate();
        Response response = getTrain(train.number(), date.format(DATE_FORMAT));

        Assertions.assertEquals(200, response.statusCode());
        JsonPath jPath = response.jsonPath();
        LocalDateTime departure = LocalDateTime.parse(jPath.getString("trainDepartureTime"));
        LocalDateTime arrival = LocalDateTime.parse(jPath.getString("trainArrivalTime"));
        Assertions.assertEquals(date, departure.toLocalDate());
        Assertions.assertEquals(departure.plusMinutes(Math.round(jPath.getDouble("journeyTime") * 60)), arrival);
        Assertions.assertEquals(train.totalSeats(), jPath.getInt("availableSeats"), "No seats should be booked on a fresh date");
    }

    @ParameterizedTest(name = "get train {0} matches search result")
    @MethodSource("trains")
    void testSearchAndGetTrainAreConsistent(TrainData train) {
        String date = LocalDate.now().plusDays(5).format(DATE_FORMAT);
        List<Map<String, Object>> trains = searchTrains(train.from(), train.to(), date).jsonPath().getList("$");
        Map<String, Object> searched = trains.stream()
                .filter(t -> train.number() == (Integer) t.get("trainNumber"))
                .findFirst().orElseThrow();

        JsonPath jPath = getTrain(train.number(), date).jsonPath();
        Assertions.assertEquals(searched.get("trainName"), jPath.getString("trainName"));
        Assertions.assertEquals(searched.get("availableSeats"), jPath.getInt("availableSeats"));
        Assertions.assertEquals(searched.get("trainDepartureTime"), jPath.getString("trainDepartureTime"));
        Assertions.assertEquals(searched.get("trainArrivalTime"), jPath.getString("trainArrivalTime"));
    }

    @Test
    void testUserIsAbleToGetTrainForPastJourneyDate() {
        Response response = getTrain(TRAINS.get(0).number(), LocalDate.now().minusDays(10).format(DATE_FORMAT));

        Assertions.assertEquals(200, response.statusCode());
    }

    @ParameterizedTest(name = "get train returns 404 for id {0}")
    @ValueSource(ints = {0, 1, -5, 122950, 999999})
    void testGetTrainReturnsNotFoundForUnknownId(int id) {
        Response response = getTrain(id, null);

        Assertions.assertEquals(404, response.statusCode());
    }

    @ParameterizedTest(name = "get train rejects id [{0}]")
    @ValueSource(strings = {"abc", "12.5", "1e3", "122951abc"})
    void testGetTrainRejectsNonNumericId(String id) {
        Response response = getTrain(id, null);

        Assertions.assertEquals(400, response.statusCode());
    }

    @Test
    void testGetTrainRejectsMissingId() {
        Response response = getTrain(null, null);

        Assertions.assertEquals(400, response.statusCode());
    }

    @ParameterizedTest(name = "get train rejects journey date [{0}]")
    @ValueSource(strings = {"2030-01-15", "15/01/2030", "32-01-2030", "29-02-2031", "tomorrow"})
    void testGetTrainRejectsInvalidJourneyDate(String journeyDate) {
        Response response = getTrain(TRAINS.get(0).number(), journeyDate);

        Assertions.assertEquals(400, response.statusCode());
    }
}
