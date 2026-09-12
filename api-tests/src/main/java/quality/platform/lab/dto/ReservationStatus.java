package quality.platform.lab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ReservationStatus {
    @JsonProperty("CONFIRMED") CONFIRMED,
    @JsonProperty("NOT CONFIRMED") NOT_CONFIRMED
}
