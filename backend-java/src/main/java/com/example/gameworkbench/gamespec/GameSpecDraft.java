package com.example.gameworkbench.gamespec;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GameSpecDraft(
        String specVersion,
        String archetype,
        Metadata metadata,
        World world,
        Player player,
        List<Entity> entities,
        List<Rule> rules,
        Presentation presentation) {

    public record Metadata(String title, Integer seed, @JsonProperty(required = false) String description) {}
    public record World(Integer width, Integer height, Integer timeLimitSeconds,
            @JsonProperty(required = false) String backgroundColor) {}
    public record Player(String movement, Integer speed, Integer health, Integer radius, Point spawn) {}
    public record Point(Integer x, Integer y) {}
    public record Entity(String id, String type, Integer x, Integer y, Integer size,
            @JsonProperty(required = false) Integer score,
            @JsonProperty(required = false) Integer speed,
            @JsonProperty(required = false) String patrolAxis,
            @JsonProperty(required = false) Integer patrolRange) {}
    public record Rule(String when, @JsonProperty(value = "if", required = true) Condition condition,
            List<Action> then) {}
    public record Condition(String counter, Integer equals) {}
    public record Action(String action) {}
    public record Presentation(String visualThemeId, String assetPackId, String animationProfileId,
            String cameraProfileId, String feedbackProfileId, String uiSkinId, String audioProfileId) {}
}
