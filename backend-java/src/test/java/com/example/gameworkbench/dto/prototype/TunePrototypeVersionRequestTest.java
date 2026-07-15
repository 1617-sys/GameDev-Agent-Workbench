package com.example.gameworkbench.dto.prototype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;

class TunePrototypeVersionRequestTest {
    @Test
    void rejectsUnknownFieldsAndOutOfBoundsValues() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue("{\"script\":\"evil\"}", TunePrototypeVersionRequest.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        TunePrototypeVersionRequest request = new TunePrototypeVersionRequest(); request.setPlayerSpeed(401);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).extracting(value -> value.getPropertyPath().toString())
                    .contains("playerSpeed");
        }
    }
}
