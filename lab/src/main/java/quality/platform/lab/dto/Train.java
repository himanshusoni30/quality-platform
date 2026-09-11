package quality.platform.lab.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Train {
    private Integer trainNumber;
    private String trainName;
    private TrainSpeed trainSpeed;
    private Integer availableSeats;
    private Integer totalSeats;
    private Tier tier;
    private String sourceStation;
    private String destinationStation;
    private Double journeyTime;
    private LocalDateTime trainDepartureTime;
    private LocalDateTime trainArrivalTime;
    private Integer numberOfCoaches;
}
