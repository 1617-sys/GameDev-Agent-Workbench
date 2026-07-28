import { readFileSync } from "node:fs";
import { createSimulation } from "../../frontend-vue/src/features/demo/runtime/simulation/index.ts";

const input = JSON.parse(readFileSync(0, "utf8"));
const simulation = createSimulation(input.gameConfig, input.options);
for (const [index, expected] of input.steps.entries()) {
  const result = simulation.step(expected.action);
  if (result.previousStateHash !== expected.previousStateHash || result.stateHash !== expected.stateHash) {
    process.stdout.write(JSON.stringify({ verified: false, sequence: index + 1, expected: expected.stateHash, actual: result.stateHash }));
    process.exit(2);
  }
}
process.stdout.write(JSON.stringify({ verified: true, stepCount: input.steps.length, finalStateHash: simulation.stateHash() }));
