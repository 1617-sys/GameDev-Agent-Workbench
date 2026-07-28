import Phaser from "phaser";
import { normalizeGameConfig } from "./gameConfig";
import {
  RuntimeSimulationAdapter,
  SIMULATION_PROTOCOL_VERSION,
  TICK_MS
} from "./simulation/index.ts";
import {
  RUNTIME_RESOURCE_MANIFEST,
  configuredImageKeys,
  playManifestSound,
  textureKeyFor
} from "./resourceManifest";

const color = (value, fallback) => Phaser.Display.Color.HexStringToColor(value || fallback).color;

class ArcadeCollectScene extends Phaser.Scene {
  constructor() {
    super("ArcadeCollectScene");
  }

  init(data = {}) {
    const bootstrap = this.mountData || {};
    const callbacks = bootstrap.callbacks || {};
    this.gameConfig = bootstrap.config;
    this.onHud = callbacks.onHud || (() => {});
    this.onMessage = callbacks.onMessage || (() => {});
    this.onReady = callbacks.onReady || (() => {});
    this.onWarning = callbacks.onWarning || (() => {});
    this.onTelemetry = callbacks.onTelemetry || (() => {});
    this.autoStart = Boolean(data.autoStart);
    this.externalDirections = new Set();
    this.externalDirectionOrder = [];
    this.assetWarnings = new Set();
    this.lastHudSecond = null;
    this.adapter = new RuntimeSimulationAdapter(this.gameConfig);
  }

  preload() {
    this.load.on(Phaser.Loader.Events.FILE_LOAD_ERROR, this.handleLoadError, this);
    for (const resourceKey of configuredImageKeys(this.gameConfig)) {
      const descriptor = RUNTIME_RESOURCE_MANIFEST[resourceKey];
      if (descriptor?.kind === "image") this.load.image(textureKeyFor(resourceKey), descriptor.url);
    }
  }

  create() {
    const config = this.gameConfig;
    const palette = config.presentation.palette;
    this.cameras.main.setBackgroundColor(palette.floor);
    this.physics.world.setBounds(0, 0, config.world.width, config.world.height);
    this.ensureFallbackTextures();
    this.drawFloor(config.world.width, config.world.height, palette);
    this.createObstacles(config.world.obstacles, palette);
    this.createExit(config.entities.exit, palette);
    this.createItems(config.entities.collectibles, palette);
    this.createEnemies(config.entities.enemies, config.behaviors.enemyPatrols, palette);
    this.createPlayer({ ...config.player, ...config.world.spawn }, palette);

    this.cursors = this.input.keyboard.createCursorKeys();
    this.keys = this.input.keyboard.addKeys("W,A,S,D");
    this.keyHandlers = {
      space: () => this.primaryAction(),
      pause: () => this.togglePause(),
      restart: () => this.restartGame()
    };
    this.input.keyboard.on("keydown-SPACE", this.keyHandlers.space);
    this.input.keyboard.on("keydown-P", this.keyHandlers.pause);
    this.input.keyboard.on("keydown-R", this.keyHandlers.restart);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, this.cleanup, this);

    this.physics.pause();
    this.onReady({
      engine: "Phaser 3",
      renderer: this.game.renderer.type === Phaser.WEBGL ? "WebGL" : "Canvas",
      physics: "arcade",
      scene: "ArcadeCollectScene",
      obstacleCount: this.obstacles.getLength(),
      itemCount: this.items.getLength(),
      enemyCount: this.enemies.getLength(),
      protocolVersion: SIMULATION_PROTOCOL_VERSION,
      tickMs: TICK_MS,
      ...this.adapter.simulationOptions(),
      warnings: [...this.assetWarnings]
    });
    this.projectSnapshot(this.adapter.snapshot());
    if (this.autoStart) this.startGame();
    else this.pushHud("准备就绪，开始后倒计时才会启动。");
  }

  handleLoadError(file) {
    const warning = `资源 ${file.key} 加载失败，已使用内置几何占位。`;
    if (this.assetWarnings.has(warning)) return;
    this.assetWarnings.add(warning);
    this.onWarning(warning);
  }

  ensureFallbackTextures() {
    for (const resourceKey of configuredImageKeys(this.gameConfig)) {
      const textureKey = textureKeyFor(resourceKey);
      if (this.textures.exists(textureKey)) continue;
      const category = RUNTIME_RESOURCE_MANIFEST[resourceKey]?.category || "obstacle";
      const graphics = this.make.graphics({ add: false });
      graphics.fillStyle(0xffffff, 1);
      if (category === "collectible") graphics.fillPoints([
        new Phaser.Geom.Point(24, 2), new Phaser.Geom.Point(46, 24),
        new Phaser.Geom.Point(24, 46), new Phaser.Geom.Point(2, 24)
      ], true);
      else if (category === "player") graphics.fillCircle(24, 24, 21);
      else if (category === "enemy") graphics.fillRoundedRect(3, 6, 42, 36, 9);
      else if (category === "exit") graphics.fillRoundedRect(4, 2, 40, 60, 14);
      else graphics.fillRoundedRect(2, 2, 92, 44, 7);
      graphics.generateTexture(textureKey, category === "exit" ? 48 : category === "obstacle" ? 96 : 48, category === "exit" ? 64 : 48);
      graphics.destroy();
    }
  }

  drawFloor(width, height, palette) {
    this.add.rectangle(width / 2, height / 2, width, height, color(palette.floor, "#14213D"));
    const grid = this.add.graphics().lineStyle(1, color(palette.wall, "#24324A"), 0.38);
    for (let x = 44; x < width; x += 44) grid.lineBetween(x, 0, x, height);
    for (let y = 44; y < height; y += 44) grid.lineBetween(0, y, width, y);
    this.add.graphics().lineStyle(4, color(palette.wall, "#24324A"), 1).strokeRect(2, 2, width - 4, height - 4);
    this.add.text(18, 14, this.gameConfig.metadata.title, {
      fontFamily: "Arial", fontSize: "13px", color: "#ffffff", alpha: 0.56
    });
  }

  createObstacles(obstacles, palette) {
    this.obstacles = this.physics.add.staticGroup();
    obstacles.forEach((entry) => {
      const block = this.obstacles.create(entry.x, entry.y, textureKeyFor(entry.spriteKey));
      block.setDisplaySize(entry.width, entry.height).setTint(color(palette.wall, "#24324A")).refreshBody();
      block.setData("id", entry.id);
    });
  }

  createExit(exit, palette) {
    this.exitZone = this.physics.add.staticGroup();
    this.exitVisual = this.exitZone.create(exit.x, exit.y, textureKeyFor(exit.spriteKey));
    this.exitVisual.setDisplaySize(exit.width, exit.height).setTint(color(palette.exit, "#22C55E")).setAlpha(0.36).refreshBody();
    this.exitLabel = this.add.text(exit.x, exit.y, "已锁定", {
      fontFamily: "Arial", fontSize: "12px", fontStyle: "bold", color: "#ffffff"
    }).setOrigin(0.5).setDepth(4);
    this.tweens.add({ targets: this.exitVisual, alpha: { from: 0.24, to: 0.48 }, duration: 900, yoyo: true, repeat: -1 });
  }

  createItems(items, palette) {
    this.items = this.physics.add.staticGroup();
    items.forEach((entry, index) => {
      const sprite = this.items.create(entry.x, entry.y, textureKeyFor(entry.spriteKey));
      sprite.setDisplaySize(entry.size * 2, entry.size * 2).setTint(color(palette.item, "#FACC15")).refreshBody();
      sprite.setData("id", entry.id).setData("label", entry.label);
      this.tweens.add({ targets: sprite, scaleX: sprite.scaleX * 1.1, scaleY: sprite.scaleY * 1.1, duration: 650 + index * 80, yoyo: true, repeat: -1 });
    });
  }

  createEnemies(enemies, patrols, palette) {
    this.enemies = this.physics.add.group({ allowGravity: false });
    enemies.forEach((entry) => {
      const patrol = patrols.find((value) => value.enemyId === entry.id);
      const guard = this.physics.add.sprite(entry.x, entry.y, textureKeyFor(entry.spriteKey));
      guard.setDisplaySize(entry.size * 1.45, entry.size * 1.45).setTint(color(palette.enemy, "#FB7185"));
      guard.setData("id", entry.id);
      guard.setDepth(3);
      guard.body.enable = false;
      this.enemies.add(guard);
      const path = this.add.graphics().lineStyle(2, color(palette.enemy, "#FB7185"), 0.18);
      if (patrol.axis === "x") path.lineBetween(entry.x - patrol.distance, entry.y, entry.x + patrol.distance, entry.y);
      else path.lineBetween(entry.x, entry.y - patrol.distance, entry.x, entry.y + patrol.distance);
    });
  }

  createPlayer(player, palette) {
    this.player = this.physics.add.sprite(player.x, player.y, textureKeyFor(player.spriteKey));
    this.player.setDisplaySize(player.size * 1.45, player.size * 1.45).setTint(color(palette.player, "#5EEAD4"));
    this.player.setDepth(4);
    this.player.body.enable = false;
    this.playerGlow = this.add.circle(this.player.x, this.player.y, player.size, color(palette.player, "#5EEAD4"), 0.1).setDepth(2);
  }

  update(_time, delta) {
    if (!this.player || this.adapter.status() !== "PLAYING") return;
    for (const result of this.adapter.advance(delta, this.inputAction())) this.consumeStepResult(result);
  }

  inputAction() {
    const external = [...this.externalDirectionOrder].reverse().find((direction) => this.externalDirections.has(direction));
    const keyboard = [
      ["up", this.cursors.up.isDown || this.keys.W.isDown],
      ["down", this.cursors.down.isDown || this.keys.S.isDown],
      ["left", this.cursors.left.isDown || this.keys.A.isDown],
      ["right", this.cursors.right.isDown || this.keys.D.isDown]
    ].find(([, active]) => active)?.[0];
    const direction = external || keyboard;
    return { type: direction ? `MOVE_${direction.toUpperCase()}` : "WAIT" };
  }

  projectSnapshot(state) {
    const playerX = state.player.position.xMp / 1000;
    const playerY = state.player.position.yMp / 1000;
    this.player.setPosition(playerX, playerY);
    this.playerGlow.setPosition(playerX, playerY);
    const velocity = state.player.velocity;
    if (velocity.xMpPerSecond || velocity.yMpPerSecond) {
      this.player.rotation = Math.atan2(velocity.yMpPerSecond, velocity.xMpPerSecond) + Math.PI / 2;
    }
    this.player.setAlpha(state.elapsedMs < state.player.invulnerableUntilMs ? 0.42 : 1);

    const enemies = new Map(state.enemies.map((enemy) => [enemy.id, enemy]));
    this.enemies.children.iterate((sprite) => {
      const enemy = sprite && enemies.get(sprite.getData("id"));
      if (enemy) sprite.setPosition(enemy.position.xMp / 1000, enemy.position.yMp / 1000);
    });
    const collectibles = new Map(state.collectibles.map((item) => [item.id, item]));
    this.items.children.iterate((sprite) => {
      const item = sprite && collectibles.get(sprite.getData("id"));
      if (item) sprite.setActive(item.active).setVisible(item.active);
    });
    this.exitVisual.setAlpha(state.exitUnlocked ? 0.96 : 0.36);
    this.exitLabel.setText(state.exitUnlocked ? this.gameConfig.entities.exit.label : "已锁定");
  }

  startGame() {
    if (!this.adapter.start()) return false;
    if (!this.autoStart) this.onTelemetry("SESSION_STARTED", 0, {});
    this.pushHud("游戏开始，收集目标并前往出口。");
    return true;
  }

  togglePause() {
    const before = this.adapter.status();
    if (!this.adapter.togglePause()) return false;
    this.pushHud(before === "PLAYING" ? "游戏已暂停。" : "继续游戏。");
    return true;
  }

  primaryAction() {
    if (this.adapter.status() === "READY") return this.startGame();
    if (this.adapter.status() === "PAUSED") return this.togglePause();
    if (["WON", "LOST"].includes(this.adapter.status())) return this.restartGame();
    return false;
  }

  restartGame() {
    if (this.adapter.status() === "READY") {
      if (!this.adapter.start()) return false;
      this.pushHud("游戏已重新开始。");
      return true;
    }
    const restart = this.adapter.restart();
    if (restart.result) this.consumeStepResult(restart.result, restart.elapsedMs);
    else if (restart.recreated) this.onTelemetry("SESSION_RESTARTED", restart.elapsedMs, {});
    this.projectSnapshot(this.adapter.snapshot());
    this.pushHud("游戏已重新开始。");
    return true;
  }

  setDirection(direction, active) {
    if (!['left', 'right', 'up', 'down'].includes(direction)) return;
    if (active) {
      this.externalDirections.add(direction);
      this.externalDirectionOrder = this.externalDirectionOrder.filter((value) => value !== direction);
      this.externalDirectionOrder.push(direction);
    } else {
      this.externalDirections.delete(direction);
      this.externalDirectionOrder = this.externalDirectionOrder.filter((value) => value !== direction);
    }
  }

  consumeStepResult(result, restartElapsedMs = null) {
    const state = this.adapter.snapshot();
    this.projectSnapshot(state);
    let message = "";
    for (const event of result.events) {
      const elapsedMs = event.type === "SESSION_RESTARTED" && restartElapsedMs !== null ? restartElapsedMs : state.elapsedMs;
      this.onTelemetry(event.type, elapsedMs, event.payload);
      if (event.type === "ITEM_COLLECTED") {
        const item = this.gameConfig.entities.collectibles.find((entry) => entry.id === event.payload.itemId);
        playManifestSound(this.gameConfig.presentation.audio.collect, this.onWarning);
        message = `已取得${item?.label || event.payload.itemId}，得分 +${item?.score || result.scoreDelta}。`;
        if (state.exitUnlocked) this.cameras.main.flash(180, 34, 197, 94, false);
      } else if (event.type === "PLAYER_HIT") {
        playManifestSound(this.gameConfig.presentation.audio.hit, this.onWarning);
        this.cameras.main.shake(160, 0.008);
        message = `受到伤害，剩余生命 ${state.player.health}。`;
      } else if (event.type === "GAME_WON") {
        playManifestSound(this.gameConfig.presentation.audio.win, this.onWarning);
        this.cameras.main.flash(300, 34, 197, 94);
        message = `通关成功，胜利奖励 +${this.gameConfig.balance.winBonus}。`;
      } else if (event.type === "GAME_LOST") {
        playManifestSound(this.gameConfig.presentation.audio.lose, this.onWarning);
        this.cameras.main.shake(220, 0.012);
        message = event.payload.reason === "TIME_EXPIRED" ? "时间耗尽，挑战失败。" : "生命耗尽，挑战失败。";
      } else if (event.type === "SESSION_RESTARTED") {
        message = "游戏已重新开始。";
      }
    }
    const second = Math.ceil(state.remainingMs / 1000);
    if (message || second !== this.lastHudSecond || state.status === "TERMINATED") {
      this.lastHudSecond = second;
      this.pushHud(message);
    }
  }

  pushHud(message = "") {
    const state = this.adapter.hudState();
    const payload = {
      title: this.gameConfig.metadata.title,
      objective: this.gameConfig.presentation.ui.objective,
      controls: this.gameConfig.presentation.ui.controls,
      ...state,
      remainingSeconds: Math.ceil(state.remainingMs / 1000),
      message
    };
    this.onHud(payload);
    const parent = this.game.canvas?.parentElement;
    if (parent) {
      parent.dataset.simulationProtocol = SIMULATION_PROTOCOL_VERSION;
      parent.dataset.simulationStateHash = state.stateHash;
    }
    if (message) this.onMessage(message);
  }

  cleanup() {
    this.input.keyboard.off("keydown-SPACE", this.keyHandlers?.space);
    this.input.keyboard.off("keydown-P", this.keyHandlers?.pause);
    this.input.keyboard.off("keydown-R", this.keyHandlers?.restart);
    this.load.off(Phaser.Loader.Events.FILE_LOAD_ERROR, this.handleLoadError, this);
    this.externalDirections.clear();
    this.externalDirectionOrder = [];
  }
}

export function mountGeneratedGame(container, rawConfig, callbacks = {}) {
  const config = JSON.parse(JSON.stringify(normalizeGameConfig(rawConfig)));
  class MountedArcadeCollectScene extends ArcadeCollectScene {
    constructor() {
      super();
      this.mountData = { config, callbacks };
    }
  }
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: container,
    width: config.viewport.width,
    height: config.viewport.height,
    backgroundColor: config.presentation.palette.floor,
    render: { antialias: true, pixelArt: false },
    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
    physics: { default: "arcade", arcade: { debug: false } },
    scene: [MountedArcadeCollectScene]
  });
  const scene = () => game.scene.getScene("ArcadeCollectScene");
  return {
    start: () => scene()?.startGame(),
    togglePause: () => scene()?.togglePause(),
    restart: () => scene()?.restartGame(),
    setDirection: (direction, active) => scene()?.setDirection(direction, active),
    getReplayTrace: () => scene()?.adapter.replayTrace(),
    getSimulationOptions: () => scene()?.adapter.simulationOptions(),
    destroy: () => game.destroy(true)
  };
}
