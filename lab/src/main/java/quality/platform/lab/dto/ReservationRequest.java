package quality.platform.lab.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReservationRequest {
    private Integer trainNumber;
    private Tier tier;
    private String sourceStation;
    private String destinationStation;
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate journeyDate;
    private Passenger passenger;
}
