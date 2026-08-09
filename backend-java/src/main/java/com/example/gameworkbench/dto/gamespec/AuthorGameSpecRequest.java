package com.example.gameworkbench.dto.gamespec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorGameSpecRequest(@NotBlank @Size(min = 10, max = 2000) String idea, ObjectNode currentSpec) {}
