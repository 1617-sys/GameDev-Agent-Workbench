package com.example.gameworkbench.dto.episode;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PersistMachineEpisodeBatchRequest {
    @NotBlank @Pattern(regexp = "episode/1\\.0")
    private String episodeProtocolVersion;
    @NotBlank @Size(max = 80)
    private String clientBatchKey;
    @NotEmpty @Size(max = 100) @Valid
    private List<PersistMachineEpisodeResultRequest> episodes;
}
