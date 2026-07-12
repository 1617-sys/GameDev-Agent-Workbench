package com.example.gameworkbench.evaluation;
import org.springframework.stereotype.Component;
import java.util.Set;
@Component public class RuntimeCapabilityRegistry {
 public static final String VERSION="phaser-runtime-1";
 public boolean supportsGameType(String gameType){ return Set.of("top_down_collect").contains(gameType); }
 public String version(){ return VERSION; }
}
