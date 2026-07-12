package com.example.gameworkbench.evaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class GameConfigRuleEvaluatorTest {
 private final GameConfigRuleEvaluator evaluator=new GameConfigRuleEvaluator(new ObjectMapper(),new RuntimeCapabilityRegistry());
 @Test void rejectsUnsupportedOutOfBoundsAndDuplicateIds(){var r=evaluator.evaluate("{\"gameType\":\"unsupported\",\"world\":{\"width\":100,\"height\":100},\"player\":{\"x\":-1,\"y\":0,\"speed\":0},\"exit\":{\"x\":200,\"y\":0},\"items\":[{\"id\":\"a\"},{\"id\":\"a\"}],\"enemies\":[]}");assertThat(r.status()).isEqualTo("FAILED");assertThat(r.violations()).extracting(RuleViolation::code).contains("UNSUPPORTED_GAME_TYPE","WORLD_BOUNDS","DUPLICATE_OR_MISSING_ID");}
 @Test void acceptsSupportedValidConfig(){var r=evaluator.evaluate("{\"gameType\":\"top_down_collect\",\"world\":{\"width\":100,\"height\":100},\"player\":{\"x\":1,\"y\":1,\"speed\":1},\"exit\":{\"x\":2,\"y\":2},\"items\":[{\"id\":\"a\"}],\"enemies\":[]}");assertThat(r.status()).isEqualTo("PASSED");}
}
