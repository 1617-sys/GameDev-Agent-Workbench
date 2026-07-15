package com.example.gameworkbench.dto.prototype;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class TunePrototypeVersionRequest {
    @Min(value = 30, message = "Time limit must be at least 30 seconds")
    @Max(value = 600, message = "Time limit must not exceed 600 seconds")
    private Integer timeLimitSeconds;

    @Min(value = 80, message = "Player speed must be at least 80")
    @Max(value = 400, message = "Player speed must not exceed 400")
    private Integer playerSpeed;

    @Min(value = 1, message = "Player health must be at least 1")
    @Max(value = 5, message = "Player health must not exceed 5")
    private Integer playerMaxHealth;

    @Min(value = 1, message = "Target collectibles must be at least 1")
    @Max(value = 20, message = "Target collectibles must not exceed 20")
    private Integer targetCollectibles;

    @Min(value = 0, message = "Enemy count must not be negative")
    @Max(value = 12, message = "Enemy count must not exceed 12")
    private Integer enemyCount;

    @Valid
    @Size(max = 12, message = "Enemy speed overrides must not exceed 12 entries")
    private Map<@Pattern(regexp = "[a-z][a-z0-9-]{0,31}", message = "Enemy id is invalid") String,
            @Min(value = 20, message = "Enemy speed must be at least 20")
            @Max(value = 240, message = "Enemy speed must not exceed 240") Integer> enemySpeeds;

    @AssertTrue(message = "At least one tuning parameter is required")
    public boolean isAnyParameterPresent() {
        return timeLimitSeconds != null || playerSpeed != null || playerMaxHealth != null
                || targetCollectibles != null || enemyCount != null
                || (enemySpeeds != null && !enemySpeeds.isEmpty());
    }

    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Unknown tuning field: " + field);
    }
}
