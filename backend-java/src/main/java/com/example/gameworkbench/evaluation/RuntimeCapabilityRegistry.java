package com.example.gameworkbench.evaluation;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.gameworkbench.gameconfig.GameConfigContract;

@Component
public class RuntimeCapabilityRegistry {
    public static final String VERSION = "arcade-collect-runtime/1";

    public boolean supportsGameType(String gameType) {
        return Set.of(GameConfigContract.GAME_TYPE).contains(gameType);
    }

    public String version() {
        return VERSION;
    }
}
