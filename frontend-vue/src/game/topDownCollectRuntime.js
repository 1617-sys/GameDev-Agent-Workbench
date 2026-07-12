import Phaser from "phaser";
import { normalizeGameConfig } from "./gameConfig";

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

    this.cameras.main.setBackgroundColor(config.world.backgroundColor || palette.floor);
    this.add.rectangle(config.world.width / 2, config.world.height / 2, config.world.width, config.world.height, Phaser.Display.Color.HexStringToColor(palette.floor).color);

    this.add.rectangle(config.world.width / 2, 22, config.world.width, 44, Phaser.Display.Color.HexStringToColor(palette.wall).color);
    this.add.rectangle(config.world.width / 2, config.world.height - 22, config.world.width, 44, Phaser.Display.Color.HexStringToColor(palette.wall).color);
    this.add.rectangle(22, config.world.height / 2, 44, config.world.height, Phaser.Display.Color.HexStringToColor(palette.wall).color);
    this.add.rectangle(config.world.width - 22, config.world.height / 2, 44, config.world.height, Phaser.Display.Color.HexStringToColor(palette.wall).color);

    this.player = this.add.circle(
      Number(config.player.x),
      Number(config.player.y),
      Number(config.player.size) / 2,
      Phaser.Display.Color.HexStringToColor(config.player.color || palette.player).color
    );
    this.physics.add.existing(this.player);
    this.player.body.setCollideWorldBounds(true);

    this.exit = this.physics.add.staticGroup();
    const exitRect = this.add.rectangle(
      Number(config.exit.x),
      Number(config.exit.y),
      Number(config.exit.width),
      Number(config.exit.height),
      Phaser.Display.Color.HexStringToColor(palette.exit).color,
      0.85
    );
    this.exit.add(exitRect);
    this.add.text(Number(config.exit.x) - 18, Number(config.exit.y) - 8, config.exit.label || "EXIT", {
      fontFamily: "Arial",
      fontSize: "14px",
      color: "#ffffff"
    });

    this.items = this.physics.add.staticGroup();
    config.items.forEach((item, index) => {
      const star = this.add.star(
        Number(item.x),
        Number(item.y),
        5,
        Number(item.size || 18) / 2,
        Number(item.size || 18),
        Phaser.Display.Color.HexStringToColor(palette.item).color
      );
      star.setData("id", item.id || `item-${index + 1}`);
      this.items.add(star);
    });

    this.enemies = this.physics.add.group();
    config.enemies.forEach((enemy, index) => {
      const guard = this.add.rectangle(
        Number(enemy.x),
        Number(enemy.y),
        Number(enemy.size || 28),
        Number(enemy.size || 28),
        Phaser.Display.Color.HexStringToColor(palette.enemy).color
      );
      this.physics.add.existing(guard);
      guard.setData("originX", Number(enemy.x));
      guard.setData("range", Number(enemy.range || 160));
      guard.setData("direction", index % 2 === 0 ? 1 : -1);
      guard.body.setVelocityX(Number(enemy.speed || 100) * guard.getData("direction"));
      guard.body.setCollideWorldBounds(true);
      this.enemies.add(guard);
    });

    this.physics.add.overlap(this.player, this.items, (_, item) => this.collectItem(item));
    this.physics.add.overlap(this.player, this.enemies, () => this.failGame());
    this.physics.add.overlap(this.player, this.exit, () => this.tryWin());

    this.onReady({ scene: "GeneratedGameScene", itemCount: this.items.getLength(), enemyCount: this.enemies.getLength() });
    this.cursors = this.input.keyboard.createCursorKeys();
    this.keys = this.input.keyboard.addKeys("W,A,S,D,R");
    this.input.keyboard.on("keydown-R", () => this.scene.restart({ config: this.gameConfig, onHud: this.onHud, onMessage: this.onMessage }));

    this.pushHud("RUNNING", "Demo 已就绪，开始试玩。");
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
    this.player.body.velocity.normalize().scale(speed);

    this.enemies.children.iterate((enemy) => {
      const originX = enemy.getData("originX");
      const range = enemy.getData("range");
      if (Math.abs(enemy.x - originX) > range) {
        const direction = enemy.getData("direction") * -1;
        enemy.setData("direction", direction);
        enemy.body.setVelocityX(Math.abs(enemy.body.velocity.x) * direction);
      }
    });
  }

  collectItem(item) {
    if (!item.active || this.finished) return;
    item.disableBody(true, true);
    this.collected += 1;
    this.pushHud("RUNNING", `已收集 ${this.collected}/${this.targetItems()} 个目标。`);
  }

  tryWin() {
    if (this.finished) return;
    if (this.collected < this.targetItems()) {
      this.pushHud("RUNNING", "还没有收集全部目标，暂时不能离开。");
      return;
    }
    this.finished = true;
    this.player.body.setVelocity(0);
    this.pushHud("SUCCESS", "通关成功，Demo 验证完成。");
  }

  failGame() {
    if (this.finished) return;
    this.finished = true;
    this.player.body.setVelocity(0);
    this.pushHud("FAILED", "碰到敌人，试玩失败。按 R 可以重开。");
  }

  targetItems() {
    return Number(this.gameConfig.rules.targetItems || this.gameConfig.items.length);
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
    scale: {
      mode: Phaser.Scale.FIT,
      autoCenter: Phaser.Scale.CENTER_BOTH
    },
    physics: {
      default: "arcade",
      arcade: {
        debug: false
      }
    },
    scene: [GeneratedGameScene]
  });

  game.scene.start("GeneratedGameScene", {
    config,
    onHud: callbacks.onHud,
    onMessage: callbacks.onMessage,
    onReady: callbacks.onReady || (() => {})
  });

  return () => {
    game.destroy(true);
  };
}
