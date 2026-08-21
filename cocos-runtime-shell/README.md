# Cocos Runtime Shell

Fixed, human-maintained Cocos Creator 3.8.8 runtime for the V5 `arcade_collect` vertical slice.

- Runtime data is loaded only from `assets/resources/generated/runtime-ir.json`.
- The Java build worker overwrites that JSON inside an isolated project copy.
- Unknown IR versions, archetypes, entity types, movement modes, or presentation profiles fail closed.
- The committed scene and TypeScript are runtime infrastructure; generation runs never edit them.

Open `assets/main.scene` in Cocos Creator 3.8.8. Build target: Web Mobile.
