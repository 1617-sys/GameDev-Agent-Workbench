package com.example.gameworkbench.dto.player;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePlayerRunRequest {
    @NotBlank @Size(max=80) private String prototypeVersionUuid;
    @NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{1,80}") private String clientBatchKey;
    @NotEmpty @Size(max=100) @Valid private List<Item> episodes;
    @Min(1) @Max(8) private int concurrency = 2;

    @Data
    public static class Item {
        @NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{1,80}") private String clientEpisodeKey;
        @NotBlank @Pattern(regexp="baseline-neutral|NOVICE|REGULAR|EXPERT") private String personaId;
        @NotBlank @Pattern(regexp="DETERMINISTIC|LLM") private String policyKind;
        @Min(0) @Max(4294967295L) private long seed;
        @Min(1) @Max(10000) private int maxSteps = 2000;
        @Min(0) @Max(4294967295L) private long policySeed;
        @Pattern(regexp="default") private String modelKey;
    }
}
