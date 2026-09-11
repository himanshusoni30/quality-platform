package quality.platform.lab.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import quality.platform.lab.dto.Passenger;
import quality.platform.lab.dto.Reservation;
import quality.platform.lab.dto.ReservationRequest;
import quality.platform.lab.dto.ReservationStatus;
import quality.platform.lab.dto.Train;
import quality.platform.lab.dto.TrainSchedule;

@Service
public class TrainService {
    private static final Pattern STATION_CODE = Pattern.compile("^[A-Za-z]{3}$");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final Map<Integer, TrainSchedule> trains = new LinkedHashMap<>();
    private final Map<Long, Reservation> reservations = new ConcurrentHashMap<>();
    private final JsonMapper jsonMapper;
    private final File reservationsFile;

    public TrainService(JsonMapper jsonMapper,
                        @Value("${trains.seed-file:trains.json}") String seedFile,
                        @Value("${reservations.data-file:data/reservations.json}") String reservationsFilePath) {
        this.jsonMapper = jsonMapper;
        try (InputStream in = new ClassPathResource(seedFile).getInputStream()) {
            List<TrainSchedule> seed = jsonMapper.readValue(in, new TypeReference<List<TrainSchedule>>() {});
            seed.forEach(t -> trains.put(t.getTrainNumber(), t));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load train seed data: " + seedFile, e);
        }
        this.reservationsFile = new File(reservationsFilePath);
        if (reservationsFile.exists()) {
            try (Stream<String> lines = Files.lines(reservationsFile.toPath())) {
                lines.filter(line -> !line.isBlank())
                        .map(line -> jsonMapper.readValue(line, Reservation.class))
                        .forEach(r -> reservations.put(r.getPnr(), r));
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to load reservations: " + reservationsFilePath, e);
            }
        }
    }

    // one JSON line per reservation, appended, so a booking never rewrites the whole file
    private synchronized void persist(Reservation reservation) {
        File parent = reservationsFile.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            Files.writeString(reservationsFile.toPath(), jsonMapper.writeValueAsString(reservation) + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to persist reservation " + reservation.getPnr(), e);
        }
    }

    public List<Train> search(String fromStation, String toStation, String startDate) {
        validateStationCode("fromStation", fromStation);
        validateStationCode("toStation", toStation);
        if (fromStation.equalsIgnoreCase(toStation)) {
            throw badRequest("fromStation and toStation must be different");
        }
        LocalDate date = parseDate("startDate", startDate);
        validateNotPast(date);
        return trains.values().stream()
                .filter(t -> t.getSourceCode().equalsIgnoreCase(fromStation))
                .filter(t -> t.getDestinationCode().equalsIgnoreCase(toStation))
                .sorted(Comparator.comparing(TrainSchedule::getDepartureTime))
                .map(t -> toTrain(t, date, false))
                .toList();
    }

    public Train getTrain(Integer id, String journeyDate) {
        LocalDate date = journeyDate == null ? LocalDate.now() : parseDate("journeyDate", journeyDate);
        return toTrain(getSchedule(id), date, true);
    }

    public synchronized Reservation book(ReservationRequest request) {
        validate(request);
        TrainSchedule schedule = getSchedule(request.getTrainNumber());
        if (schedule.getTier() != request.getTier()) {
            throw badRequest("Tier " + request.getTier() + " is not available on train " + schedule.getTrainNumber());
        }
        if (!schedule.getSourceCode().equalsIgnoreCase(request.getSourceStation())
                || !schedule.getDestinationCode().equalsIgnoreCase(request.getDestinationStation())) {
            throw badRequest("Train " + schedule.getTrainNumber() + " does not run between "
                    + request.getSourceStation() + " and " + request.getDestinationStation());
        }

        LocalDate date = request.getJourneyDate();
        int booked = confirmedCount(schedule.getTrainNumber(), date);
        Passenger in = request.getPassenger();
        Passenger passenger = new Passenger(in.getFirstName(), in.getLastName(), in.getAge(), null, null);
        ReservationStatus status = ReservationStatus.NOT_CONFIRMED;
        if (booked < schedule.getTotalSeats()) {
            int seatsPerCoach = (int) Math.ceil((double) schedule.getTotalSeats() / schedule.getNumberOfCoaches());
            passenger.setCoachNumber(schedule.getTier().getCoachPrefix() + (booked / seatsPerCoach + 1));
            passenger.setSeatNumber(booked % seatsPerCoach + 1);
            status = ReservationStatus.CONFIRMED;
        }

        Reservation reservation = Reservation.builder()
                .pnr(generatePnr())
                .trainNumber(schedule.getTrainNumber())
                .tier(schedule.getTier())
                .sourceStation(stationName(schedule.getSourceName(), schedule.getSourceCode()))
                .destinationStation(stationName(schedule.getDestinationName(), schedule.getDestinationCode()))
                .journeyDate(date)
                .departureTime(date.atTime(schedule.getDepartureTime()))
                .status(status)
                .passenger(passenger)
                .build();
        reservations.put(reservation.getPnr(), reservation);
        persist(reservation);
        return reservation;
    }

    public Reservation getReservation(Long pnr) {
        Reservation reservation = reservations.get(pnr);
        if (reservation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found: " + pnr);
        }
        return reservation;
    }

    private TrainSchedule getSchedule(Integer trainNumber) {
        TrainSchedule schedule = trains.get(trainNumber);
        if (schedule == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Train not found: " + trainNumber);
        }
        return schedule;
    }

    private Train toTrain(TrainSchedule t, LocalDate date, boolean includeTotalSeats) {
        LocalDateTime departure = date.atTime(t.getDepartureTime());
        return Train.builder()
                .trainNumber(t.getTrainNumber())
                .trainName(t.getTrainName())
                .trainSpeed(t.getTrainSpeed())
                .availableSeats(Math.max(0, t.getTotalSeats() - confirmedCount(t.getTrainNumber(), date)))
                .totalSeats(includeTotalSeats ? t.getTotalSeats() : null)
                .tier(t.getTier())
                .sourceStation(stationName(t.getSourceName(), t.getSourceCode()))
                .destinationStation(stationName(t.getDestinationName(), t.getDestinationCode()))
                .journeyTime(t.getJourneyTime())
                .trainDepartureTime(departure)
                .trainArrivalTime(departure.plusMinutes(Math.round(t.getJourneyTime() * 60)))
                .numberOfCoaches(t.getNumberOfCoaches())
                .build();
    }

    private int confirmedCount(Integer trainNumber, LocalDate date) {
        return (int) reservations.values().stream()
                .filter(r -> r.getTrainNumber().equals(trainNumber))
                .filter(r -> r.getJourneyDate().equals(date))
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .count();
    }

    private long generatePnr() {
        long pnr;
        do {
            pnr = ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L);
        } while (reservations.containsKey(pnr));
        return pnr;
    }

    private void validate(ReservationRequest request) {
        if (request.getTrainNumber() == null) throw badRequest("trainNumber is required");
        if (request.getTier() == null) throw badRequest("tier is required");
        validateStationCode("sourceStation", request.getSourceStation());
        validateStationCode("destinationStation", request.getDestinationStation());
        if (request.getJourneyDate() == null) throw badRequest("journeyDate is required");
        validateNotPast(request.getJourneyDate());
        Passenger p = request.getPassenger();
        if (p == null) throw badRequest("passenger is required");
        if (p.getFirstName() == null || p.getFirstName().isBlank()) throw badRequest("passenger.firstName is required");
        if (p.getLastName() == null || p.getLastName().isBlank()) throw badRequest("passenger.lastName is required");
        if (p.getAge() == null || p.getAge() < 1 || p.getAge() > 125) throw badRequest("passenger.age must be between 1 and 125");
    }

    private void validateStationCode(String field, String code) {
        if (code == null || !STATION_CODE.matcher(code).matches()) {
            throw badRequest(field + " must be a 3 letter station code");
        }
    }

    private LocalDate parseDate(String field, String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw badRequest(field + " must be in dd-mm-yyyy format");
        }
    }

    private void validateNotPast(LocalDate date) {
        if (date.isBefore(LocalDate.now())) {
            throw badRequest("Journey date cannot be in the past");
        }
    }

    private String stationName(String name, String code) {
        return name + " (" + code + ")";
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
