package quality.platform.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Reservation {
    private Long pnr;
    private Integer trainNumber;
    private Tier tier;
    private String sourceStation;
    private String destinationStation;
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate journeyDate;
    private LocalDateTime departureTime;
    private ReservationStatus status;
    private Passenger passenger;
}
