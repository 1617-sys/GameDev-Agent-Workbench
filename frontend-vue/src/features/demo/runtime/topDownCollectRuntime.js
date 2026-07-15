import Phaser from "phaser";
import { normalizeGameConfig } from "./gameConfig";
import { ArcadeCollectStateMachine, RUNTIME_STATES } from "./runtimeState";
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
    this.autoStart = Boolean(data.autoStart);
    this.externalDirections = new Set();
    this.assetWarnings = new Set();
    this.lastHudSecond = null;
    this.machine = new ArcadeCollectStateMachine(this.gameConfig);
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

    this.physics.add.collider(this.player, this.obstacles);
    this.physics.add.collider(this.enemies, this.obstacles, (enemy) => this.reverseEnemy(enemy));
    this.physics.add.overlap(this.player, this.items, (_, item) => this.collectItem(item));
    this.physics.add.overlap(this.player, this.enemies, (_, enemy) => this.hitPlayer(enemy));
    this.physics.add.overlap(this.player, this.exitZone, () => this.tryWin());

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
      warnings: [...this.assetWarnings]
    });
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
      guard.setData("origin", patrol.axis === "x" ? entry.x : entry.y);
      guard.setData("axis", patrol.axis).setData("range", patrol.distance).setData("speed", entry.speed);
      guard.setData("direction", this.machine.state.enemyDirections[entry.id]);
      guard.setCollideWorldBounds(true).setDepth(3);
      this.enemies.add(guard);
      const path = this.add.graphics().lineStyle(2, color(palette.enemy, "#FB7185"), 0.18);
      if (patrol.axis === "x") path.lineBetween(entry.x - patrol.distance, entry.y, entry.x + patrol.distance, entry.y);
      else path.lineBetween(entry.x, entry.y - patrol.distance, entry.x, entry.y + patrol.distance);
    });
  }

  createPlayer(player, palette) {
    this.player = this.physics.add.sprite(player.x, player.y, textureKeyFor(player.spriteKey));
    this.player.setDisplaySize(player.size * 1.45, player.size * 1.45).setTint(color(palette.player, "#5EEAD4"));
    this.player.setCollideWorldBounds(true).setDepth(4);
    this.player.body.setCircle(Math.min(this.player.width, this.player.height) * 0.36);
    this.playerGlow = this.add.circle(this.player.x, this.player.y, player.size, color(palette.player, "#5EEAD4"), 0.1).setDepth(2);
  }

  update(_time, delta) {
    if (!this.player || this.machine.state.status !== RUNTIME_STATES.PLAYING) return;
    const previousStatus = this.machine.state.status;
    this.machine.tick(delta);
    if (this.machine.state.status !== previousStatus) {
      this.finishRuntime();
      return;
    }
    const second = Math.ceil(this.machine.state.remainingMs / 1000);
    if (second !== this.lastHudSecond) {
      this.lastHudSecond = second;
      this.pushHud();
    }
    this.movePlayer();
    this.enemies.children.iterate((enemy) => {
      if (!enemy?.active) return;
      const current = enemy.getData("axis") === "x" ? enemy.x : enemy.y;
      if (Math.abs(current - enemy.getData("origin")) >= enemy.getData("range")) this.reverseEnemy(enemy);
    });
  }

  movePlayer() {
    const speed = this.gameConfig.player.speed;
    const left = this.cursors.left.isDown || this.keys.A.isDown || this.externalDirections.has("left");
    const right = this.cursors.right.isDown || this.keys.D.isDown || this.externalDirections.has("right");
    const up = this.cursors.up.isDown || this.keys.W.isDown || this.externalDirections.has("up");
    const down = this.cursors.down.isDown || this.keys.S.isDown || this.externalDirections.has("down");
    this.player.body.setVelocity((right ? speed : 0) - (left ? speed : 0), (down ? speed : 0) - (up ? speed : 0));
    if (this.player.body.velocity.lengthSq() > 0) {
      this.player.body.velocity.normalize().scale(speed);
      this.player.rotation = Math.atan2(this.player.body.velocity.y, this.player.body.velocity.x) + Math.PI / 2;
    }
    this.playerGlow.setPosition(this.player.x, this.player.y);
  }

  setEnemyVelocity(enemy) {
    const velocity = enemy.getData("speed") * enemy.getData("direction");
    enemy.body.setVelocity(0);
    if (enemy.getData("axis") === "y") enemy.body.setVelocityY(velocity);
    else enemy.body.setVelocityX(velocity);
  }

  reverseEnemy(enemy) {
    if (!enemy?.active || this.machine.state.status !== RUNTIME_STATES.PLAYING) return;
    enemy.setData("direction", enemy.getData("direction") * -1);
    this.setEnemyVelocity(enemy);
  }

  startGame() {
    if (!this.machine.start()) return false;
    this.physics.resume();
    this.enemies.children.iterate((enemy) => enemy?.active && this.setEnemyVelocity(enemy));
    this.pushHud("游戏开始，收集目标并前往出口。");
    return true;
  }

  togglePause() {
    if (this.machine.pause()) {
      this.physics.pause();
      this.pushHud("游戏已暂停。");
      return true;
    }
    if (this.machine.resume()) {
      this.physics.resume();
      this.pushHud("继续游戏。");
      return true;
    }
    return false;
  }

  primaryAction() {
    if (this.machine.state.status === RUNTIME_STATES.READY) return this.startGame();
    if (this.machine.state.status === RUNTIME_STATES.PAUSED) return this.togglePause();
    if ([RUNTIME_STATES.WON, RUNTIME_STATES.LOST].includes(this.machine.state.status)) return this.restartGame();
    return false;
  }

  restartGame() {
    this.scene.restart({ autoStart: true });
    return true;
  }

  setDirection(direction, active) {
    if (!['left', 'right', 'up', 'down'].includes(direction)) return;
    if (active) this.externalDirections.add(direction);
    else this.externalDirections.delete(direction);
  }

  collectItem(item) {
    const itemId = item.getData("id");
    if (!item.active || !this.machine.collect(itemId)) return;
    const label = item.getData("label");
    item.disableBody(true, true);
    playManifestSound(this.gameConfig.presentation.audio.collect, this.onWarning);
    if (this.machine.state.exitUnlocked) this.unlockExit();
    this.pushHud(`已取得${label}，得分 +${this.gameConfig.entities.collectibles.find((entry) => entry.id === itemId).score}。`);
  }

  hitPlayer() {
    if (!this.machine.hit()) return;
    playManifestSound(this.gameConfig.presentation.audio.hit, this.onWarning);
    this.player.setAlpha(0.42);
    this.time.delayedCall(this.gameConfig.player.hitInvulnerabilityMs, () => this.player?.active && this.player.setAlpha(1));
    this.cameras.main.shake(160, 0.008);
    if (this.machine.state.status === RUNTIME_STATES.LOST) this.finishRuntime();
    else this.pushHud(`受到伤害，剩余生命 ${this.machine.state.health}。`);
  }

  unlockExit() {
    this.exitVisual.setAlpha(0.96);
    this.exitLabel.setText(this.gameConfig.entities.exit.label);
    this.cameras.main.flash(180, 34, 197, 94, false);
  }

  tryWin() {
    if (this.machine.state.status !== RUNTIME_STATES.PLAYING) return;
    if (!this.machine.state.exitUnlocked) {
      this.pushHud(`出口尚未解锁，还差 ${this.machine.state.total - this.machine.state.collected} 个目标。`);
      return;
    }
    if (this.machine.reachExit()) this.finishRuntime();
  }

  finishRuntime() {
    this.physics.pause();
    this.player?.body?.setVelocity(0);
    if (this.machine.state.status === RUNTIME_STATES.WON) {
      playManifestSound(this.gameConfig.presentation.audio.win, this.onWarning);
      this.cameras.main.flash(300, 34, 197, 94);
      this.pushHud(`通关成功，胜利奖励 +${this.gameConfig.balance.winBonus}。`);
    } else {
      playManifestSound(this.gameConfig.presentation.audio.lose, this.onWarning);
      this.cameras.main.shake(220, 0.012);
      const message = this.machine.state.outcomeReason === "TIME_EXPIRED" ? "时间耗尽，挑战失败。" : "生命耗尽，挑战失败。";
      this.pushHud(message);
    }
  }

  pushHud(message = "") {
    const state = this.machine.snapshot();
    const payload = {
      title: this.gameConfig.metadata.title,
      objective: this.gameConfig.presentation.ui.objective,
      controls: this.gameConfig.presentation.ui.controls,
      ...state,
      remainingSeconds: Math.ceil(state.remainingMs / 1000),
      message
    };
    this.onHud(payload);
    if (message) this.onMessage(message);
  }

  cleanup() {
    this.input.keyboard.off("keydown-SPACE", this.keyHandlers?.space);
    this.input.keyboard.off("keydown-P", this.keyHandlers?.pause);
    this.input.keyboard.off("keydown-R", this.keyHandlers?.restart);
    this.load.off(Phaser.Loader.Events.FILE_LOAD_ERROR, this.handleLoadError, this);
    this.externalDirections.clear();
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
    destroy: () => game.destroy(true)
  };
}
