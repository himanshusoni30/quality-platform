package quality.platform.lab.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Tier {
    @JsonProperty("FIRST CLASS") FIRST_CLASS("H"),
    @JsonProperty("AC 2 TIER") AC_2_TIER("A"),
    @JsonProperty("AC 3 TIER") AC_3_TIER("B"),
    @JsonProperty("GENERAL") GENERAL("G");

    private final String coachPrefix;
}
