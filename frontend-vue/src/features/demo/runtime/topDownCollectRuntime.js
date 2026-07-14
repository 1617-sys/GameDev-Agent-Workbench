import Phaser from "phaser";
import { normalizeGameConfig } from "./gameConfig";

const color = (value, fallback) => Phaser.Display.Color.HexStringToColor(value || fallback).color;

class GeneratedGameScene extends Phaser.Scene {
  constructor() {
    super("GeneratedGameScene");
  }

  init(data) {
    this.gameConfig = normalizeGameConfig(data.config);
    this.onHud = data.onHud || (() => {});
    this.onMessage = data.onMessage || (() => {});
    this.onReady = data.onReady || (() => {});
    this.collected = 0;
    this.finished = false;
  }

  create() {
    const config = this.gameConfig;
    const palette = config.theme.palette;
    const width = Number(config.world.width);
    const height = Number(config.world.height);

    this.cameras.main.setBackgroundColor(config.world.backgroundColor || palette.floor);
    this.physics.world.setBounds(36, 36, width - 72, height - 72);
    this.createSceneTextures(palette);
    this.drawFloor(width, height, palette);
    this.createObstacles(config.obstacles, palette);
    this.createExit(config.exit, palette);
    this.createItems(config.items, palette);
    this.createEnemies(config.enemies, palette);
    this.createPlayer(config.player, palette);

    this.physics.add.collider(this.player, this.obstacles);
    this.physics.add.collider(this.enemies, this.obstacles, (enemy) => this.reverseEnemy(enemy));
    this.physics.add.overlap(this.player, this.items, (_, item) => this.collectItem(item));
    this.physics.add.overlap(this.player, this.enemies, () => this.failGame());
    this.physics.add.overlap(this.player, this.exitZone, () => this.tryWin());

    this.cursors = this.input.keyboard.createCursorKeys();
    this.keys = this.input.keyboard.addKeys("W,A,S,D,R");
    this.input.keyboard.on("keydown-R", () => this.scene.restart({
      config: this.gameConfig,
      onHud: this.onHud,
      onMessage: this.onMessage,
      onReady: this.onReady
    }));

    this.onReady({
      engine: "Phaser 3",
      renderer: this.game.renderer.type === Phaser.WEBGL ? "WebGL" : "Canvas",
      physics: "arcade",
      scene: "GeneratedGameScene",
      obstacleCount: this.obstacles.getLength(),
      itemCount: this.items.getLength(),
      enemyCount: this.enemies.getLength()
    });
    this.pushHud("RUNNING", "Demo 已就绪，收集目标并避开巡逻单位。");
  }

  createSceneTextures(palette) {
    const player = this.make.graphics({ add: false });
    player.fillStyle(color(palette.player, "#5eead4"), 0.2).fillCircle(20, 20, 19);
    player.fillStyle(color(palette.player, "#5eead4"), 1).fillCircle(20, 20, 12);
    player.fillStyle(0xffffff, 0.9).fillTriangle(20, 6, 14, 21, 26, 21);
    player.generateTexture("generated-player", 40, 40).destroy();

    const enemy = this.make.graphics({ add: false });
    enemy.fillStyle(color(palette.enemy, "#fb7185"), 0.18).fillCircle(20, 20, 19);
    enemy.fillStyle(color(palette.enemy, "#fb7185"), 1).fillRoundedRect(7, 9, 26, 22, 7);
    enemy.fillStyle(0x111827, 1).fillRoundedRect(11, 14, 18, 6, 3);
    enemy.fillStyle(0xffffff, 0.9).fillCircle(16, 17, 2).fillCircle(24, 17, 2);
    enemy.generateTexture("generated-enemy", 40, 40).destroy();

    const item = this.make.graphics({ add: false });
    item.fillStyle(color(palette.item, "#facc15"), 0.18).fillCircle(18, 18, 17);
    item.fillStyle(color(palette.item, "#facc15"), 1).fillPoints([
      new Phaser.Geom.Point(18, 3), new Phaser.Geom.Point(31, 18),
      new Phaser.Geom.Point(18, 33), new Phaser.Geom.Point(5, 18)
    ], true);
    item.lineStyle(2, 0xffffff, 0.85).strokeCircle(18, 18, 9);
    item.generateTexture("generated-item", 36, 36).destroy();
  }

  drawFloor(width, height, palette) {
    this.add.rectangle(width / 2, height / 2, width, height, color(palette.floor, "#14213d"));
    const grid = this.add.graphics().lineStyle(1, color(palette.wall, "#24324a"), 0.38);
    for (let x = 44; x < width; x += 44) grid.lineBetween(x, 0, x, height);
    for (let y = 44; y < height; y += 44) grid.lineBetween(0, y, width, y);

    const border = this.add.graphics().lineStyle(4, color(palette.wall, "#24324a"), 1);
    border.strokeRect(34, 34, width - 68, height - 68);
    this.add.text(50, 48, this.gameConfig.title, {
      fontFamily: "Arial", fontSize: "13px", color: "#ffffff", alpha: 0.56
    });
  }

  createObstacles(obstacles, palette) {
    this.obstacles = this.physics.add.staticGroup();
    (obstacles || []).slice(0, 8).forEach((entry, index) => {
      const width = Math.max(24, Number(entry.width || 100));
      const height = Math.max(20, Number(entry.height || 24));
      const block = this.add.rectangle(
        Number(entry.x), Number(entry.y), width, height,
        color(entry.color || palette.wall, "#24324a"), 0.96
      ).setStrokeStyle(2, 0xffffff, 0.12);
      block.setData("id", entry.id || `wall-${index + 1}`);
      this.obstacles.add(block);
    });
  }

  createExit(exit, palette) {
    const width = Number(exit.width || 54);
    const height = Number(exit.height || 72);
    this.exitVisual = this.add.rectangle(
      Number(exit.x), Number(exit.y), width, height,
      color(palette.exit, "#22c55e"), 0.28
    ).setStrokeStyle(3, color(palette.exit, "#22c55e"), 0.9);
    this.exitLabel = this.add.text(Number(exit.x), Number(exit.y), "已锁定", {
      fontFamily: "Arial", fontSize: "12px", fontStyle: "bold", color: "#ffffff"
    }).setOrigin(0.5);
    this.exitZone = this.physics.add.staticGroup();
    this.exitZone.add(this.exitVisual);
    this.tweens.add({ targets: this.exitVisual, alpha: { from: 0.22, to: 0.48 }, duration: 900, yoyo: true, repeat: -1 });
  }

  createItems(items, palette) {
    this.items = this.physics.add.staticGroup();
    items.forEach((entry, index) => {
      const sprite = this.add.sprite(Number(entry.x), Number(entry.y), "generated-item");
      sprite.setScale(Math.max(0.75, Number(entry.size || 18) / 18));
      sprite.setData("id", entry.id || `item-${index + 1}`);
      sprite.setData("label", entry.label || `目标 ${index + 1}`);
      this.items.add(sprite);
      this.tweens.add({ targets: sprite, scaleX: sprite.scaleX * 1.12, scaleY: sprite.scaleY * 1.12, duration: 650 + index * 80, yoyo: true, repeat: -1 });
    });
  }

  createEnemies(enemies, palette) {
    this.enemies = this.physics.add.group({ allowGravity: false });
    enemies.forEach((entry, index) => {
      const axis = entry.axis === "y" ? "y" : "x";
      const guard = this.physics.add.sprite(Number(entry.x), Number(entry.y), "generated-enemy");
      guard.setScale(Math.max(0.78, Number(entry.size || 28) / 28));
      guard.setData("origin", axis === "x" ? Number(entry.x) : Number(entry.y));
      guard.setData("axis", axis);
      guard.setData("range", Math.max(40, Number(entry.range || 150)));
      guard.setData("speed", Math.max(30, Number(entry.speed || 90)));
      guard.setData("direction", index % 2 === 0 ? 1 : -1);
      guard.setCollideWorldBounds(true);
      this.enemies.add(guard);
      this.setEnemyVelocity(guard);

      const path = this.add.graphics().lineStyle(2, color(palette.enemy, "#fb7185"), 0.18);
      const range = guard.getData("range");
      if (axis === "x") path.lineBetween(guard.x - range, guard.y, guard.x + range, guard.y);
      else path.lineBetween(guard.x, guard.y - range, guard.x, guard.y + range);
      path.setDepth(0);
      guard.setDepth(3);
    });
  }

  createPlayer(player, palette) {
    this.player = this.physics.add.sprite(Number(player.x), Number(player.y), "generated-player");
    this.player.setScale(Math.max(0.78, Number(player.size || 28) / 28));
    this.player.setCollideWorldBounds(true).setDepth(4);
    this.player.body.setCircle(13, 7, 7);
    this.playerGlow = this.add.circle(this.player.x, this.player.y, 24, color(palette.player, "#5eead4"), 0.08).setDepth(2);
  }

  update() {
    if (!this.player || this.finished) return;
    const speed = Number(this.gameConfig.player.speed || 210);
    const left = this.cursors.left.isDown || this.keys.A.isDown;
    const right = this.cursors.right.isDown || this.keys.D.isDown;
    const up = this.cursors.up.isDown || this.keys.W.isDown;
    const down = this.cursors.down.isDown || this.keys.S.isDown;

    this.player.body.setVelocity(0);
    if (left) this.player.body.setVelocityX(-speed);
    if (right) this.player.body.setVelocityX(speed);
    if (up) this.player.body.setVelocityY(-speed);
    if (down) this.player.body.setVelocityY(speed);
    if (this.player.body.velocity.lengthSq() > 0) {
      this.player.body.velocity.normalize().scale(speed);
      this.player.rotation = Math.atan2(this.player.body.velocity.y, this.player.body.velocity.x) + Math.PI / 2;
    }
    this.playerGlow.setPosition(this.player.x, this.player.y);

    this.enemies.children.iterate((enemy) => {
      if (!enemy?.active) return;
      const current = enemy.getData("axis") === "x" ? enemy.x : enemy.y;
      if (Math.abs(current - enemy.getData("origin")) >= enemy.getData("range")) this.reverseEnemy(enemy);
    });
  }

  setEnemyVelocity(enemy) {
    const velocity = enemy.getData("speed") * enemy.getData("direction");
    enemy.body.setVelocity(0);
    if (enemy.getData("axis") === "y") {
      enemy.body.setVelocityY(velocity);
      enemy.rotation = velocity > 0 ? Math.PI : 0;
    } else {
      enemy.body.setVelocityX(velocity);
      enemy.rotation = velocity > 0 ? Math.PI / 2 : -Math.PI / 2;
    }
  }

  reverseEnemy(enemy) {
    if (!enemy?.active) return;
    enemy.setData("direction", enemy.getData("direction") * -1);
    this.setEnemyVelocity(enemy);
  }

  collectItem(item) {
    if (!item.active || this.finished) return;
    const label = item.getData("label");
    item.disableBody(true, true);
    this.collected += 1;
    if (this.collected >= this.targetItems()) this.unlockExit();
    this.pushHud("RUNNING", `已取得${label}，进度 ${this.collected}/${this.targetItems()}。`);
  }

  unlockExit() {
    this.exitVisual.setFillStyle(color(this.gameConfig.theme.palette.exit, "#22c55e"), 0.86);
    this.exitLabel.setText(this.gameConfig.exit.label || "出口");
    this.cameras.main.flash(180, 34, 197, 94, false);
  }

  tryWin() {
    if (this.finished) return;
    if (this.collected < this.targetItems()) {
      this.pushHud("RUNNING", `出口尚未解锁，还差 ${this.targetItems() - this.collected} 个目标。`);
      return;
    }
    this.finished = true;
    this.player.body.setVelocity(0);
    this.cameras.main.flash(300, 34, 197, 94);
    this.pushHud("SUCCESS", "通关成功。按 R 可以重新体验本关。");
  }

  failGame() {
    if (this.finished) return;
    this.finished = true;
    this.player.body.setVelocity(0);
    this.cameras.main.shake(220, 0.012);
    this.pushHud("FAILED", "被巡逻单位发现。按 R 重新开始。");
  }

  targetItems() {
    return Math.min(Number(this.gameConfig.rules.targetItems || this.gameConfig.items.length), this.gameConfig.items.length);
  }

  pushHud(status, message) {
    const payload = {
      title: this.gameConfig.title,
      objective: this.gameConfig.ui.objective,
      controls: this.gameConfig.ui.controls,
      status,
      message,
      collected: this.collected,
      total: this.targetItems()
    };
    this.onHud(payload);
    this.onMessage(message);
  }
}

export function mountGeneratedGame(container, rawConfig, callbacks = {}) {
  const config = normalizeGameConfig(rawConfig);
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: container,
    width: Number(config.world.width),
    height: Number(config.world.height),
    backgroundColor: config.world.backgroundColor,
    render: { antialias: true, pixelArt: false },
    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
    physics: { default: "arcade", arcade: { debug: false } },
    scene: [GeneratedGameScene]
  });

  game.scene.start("GeneratedGameScene", {
    config,
    onHud: callbacks.onHud,
    onMessage: callbacks.onMessage,
    onReady: callbacks.onReady || (() => {})
  });

  return () => game.destroy(true);
}
