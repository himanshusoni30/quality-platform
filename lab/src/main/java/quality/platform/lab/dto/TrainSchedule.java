package quality.platform.lab.dto;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrainSchedule {
    private Integer trainNumber;
    private String trainName;
    private TrainSpeed trainSpeed;
    private Tier tier;
    private String sourceCode;
    private String sourceName;
    private String destinationCode;
    private String destinationName;
    private LocalTime departureTime;
    private Double journeyTime;
    private Integer numberOfCoaches;
    private Integer totalSeats;
}
