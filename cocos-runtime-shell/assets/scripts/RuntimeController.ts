import {
    _decorator, Color, Component, EventKeyboard, EventTouch, Graphics, input, Input,
    JsonAsset, KeyCode, Label, Node, resources, tween, UITransform, Vec2, Vec3,
} from 'cc';

const { ccclass } = _decorator;

type EntityType = 'collectible' | 'enemy' | 'obstacle' | 'exit';
type GameState = 'loading' | 'playing' | 'paused' | 'won' | 'lost' | 'error';

interface RuntimeEntitySpec {
    id: string;
    type: EntityType;
    x: number;
    y: number;
    size: number;
    score?: number;
    speed?: number;
    patrolAxis?: 'x' | 'y';
    patrolRange?: number;
}

interface RuntimeIr {
    irVersion: string;
    archetype: string;
    sourceDigest: string;
    runtimeIrDigest?: string;
    metadata: { title: string; seed: number; description?: string };
    world: { width: number; height: number; timeLimitSeconds: number; backgroundColor?: string };
    player: { movement: string; speed: number; health: number; radius: number; spawn: { x: number; y: number } };
    entities: RuntimeEntitySpec[];
    presentation: Record<string, string>;
}

interface RuntimeEntity {
    spec: RuntimeEntitySpec;
    node: Node;
    origin: Vec3;
    direction: number;
    active: boolean;
}

@ccclass('RuntimeController')
export class RuntimeController extends Component {
    private ir: RuntimeIr | null = null;
    private state: GameState = 'loading';
    private worldRoot!: Node;
    private playerNode!: Node;
    private entities: RuntimeEntity[] = [];
    private pressed = new Set<KeyCode>();
    private touchVector = new Vec2();
    private touchOrigin: Vec2 | null = null;
    private score = 0;
    private health = 0;
    private remaining = 0;
    private secondsLeft = 0;
    private scoreLabel!: Label;
    private healthLabel!: Label;
    private timeLabel!: Label;
    private objectiveLabel!: Label;
    private statusLabel!: Label;
    private exitUnlocked = false;
    private invulnerable = 0;

    protected onLoad(): void {
        input.on(Input.EventType.KEY_DOWN, this.onKeyDown, this);
        input.on(Input.EventType.KEY_UP, this.onKeyUp, this);
        input.on(Input.EventType.TOUCH_START, this.onTouchStart, this);
        input.on(Input.EventType.TOUCH_MOVE, this.onTouchMove, this);
        input.on(Input.EventType.TOUCH_END, this.onTouchEnd, this);
        input.on(Input.EventType.TOUCH_CANCEL, this.onTouchEnd, this);
        this.showLoading();
        resources.load('generated/runtime-ir', JsonAsset, (error, asset) => {
            if (error || !asset) {
                this.failClosed('Runtime IR is missing or unreadable');
                return;
            }
            try {
                this.bootstrap(asset.json as unknown as RuntimeIr);
            } catch (cause) {
                console.error('[RuntimeShell] rejected runtime IR', cause);
                this.failClosed('Runtime IR failed capability validation');
            }
        });
    }

    protected onDestroy(): void {
        input.off(Input.EventType.KEY_DOWN, this.onKeyDown, this);
        input.off(Input.EventType.KEY_UP, this.onKeyUp, this);
        input.off(Input.EventType.TOUCH_START, this.onTouchStart, this);
        input.off(Input.EventType.TOUCH_MOVE, this.onTouchMove, this);
        input.off(Input.EventType.TOUCH_END, this.onTouchEnd, this);
        input.off(Input.EventType.TOUCH_CANCEL, this.onTouchEnd, this);
    }

    protected update(dt: number): void {
        if (this.state !== 'playing' || !this.ir) return;
        this.invulnerable = Math.max(0, this.invulnerable - dt);
        this.secondsLeft = Math.max(0, this.secondsLeft - dt);
        if (this.secondsLeft <= 0) {
            this.finish(false, 'TIME UP');
            return;
        }
        this.updatePlayer(dt);
        this.updateEnemies(dt);
        this.resolveContacts();
        this.updateHud();
    }

    private bootstrap(ir: RuntimeIr): void {
        this.validate(ir);
        this.ir = ir;
        this.clearRuntimeChildren();
        this.entities = [];
        this.score = 0;
        this.exitUnlocked = false;
        this.pressed.clear();
        this.touchOrigin = null;
        this.touchVector.set(0, 0);
        this.createWorld();
        this.createHud();
        this.health = ir.player.health;
        this.secondsLeft = ir.world.timeLimitSeconds;
        this.remaining = ir.entities.filter(entity => entity.type === 'collectible').length;
        this.createPlayer();
        ir.entities.forEach(entity => this.createEntity(entity));
        this.state = 'playing';
        this.updateHud();
        console.info('[RuntimeShell] ready', {
            irVersion: ir.irVersion,
            sourceDigest: ir.sourceDigest,
            seed: ir.metadata.seed,
        });
    }

    private validate(ir: RuntimeIr): void {
        if (!ir || ir.irVersion !== 'cocos-runtime-ir/1' || ir.archetype !== 'arcade_collect') throw new Error('unsupported IR');
        if (!ir.sourceDigest || !ir.metadata?.title || !ir.world || !ir.player || !Array.isArray(ir.entities)) throw new Error('incomplete IR');
        if (ir.player.movement !== 'four_way') throw new Error('unsupported movement');
        const types = new Set<EntityType>(['collectible', 'enemy', 'obstacle', 'exit']);
        if (ir.entities.some(entity => !types.has(entity.type))) throw new Error('unsupported entity');
        const requiredProfiles: Record<string, string> = {
            visualThemeId: 'forest-01', assetPackId: 'forest-adventure-01',
            animationProfileId: 'topdown-character-01', cameraProfileId: 'follow-soft-01',
            feedbackProfileId: 'arcade-juice-01', uiSkinId: 'forest-hud-01', audioProfileId: 'forest-light-01',
        };
        Object.entries(requiredProfiles).forEach(([key, value]) => {
            if (ir.presentation?.[key] !== value) throw new Error(`unregistered profile: ${key}`);
        });
    }

    private showLoading(): void {
        this.clearRuntimeChildren();
        const background = this.makeGraphics('LoadingBackground', this.node, 1280, 720);
        background.fillColor = this.color('#071a16');
        background.rect(-640, -360, 1280, 720);
        background.fill();
        this.makeLabel('Loading', this.node, 'AWAKENING THE FOREST…', 28, this.color('#8fffd1'), new Vec3(0, 0));
    }

    private createWorld(): void {
        const ir = this.ir!;
        const backdrop = this.makeGraphics('Backdrop', this.node, 1280, 720);
        backdrop.fillColor = this.color(ir.world.backgroundColor || '#10251b');
        backdrop.rect(-640, -360, 1280, 720);
        backdrop.fill();

        backdrop.fillColor = this.color('#183d2b');
        for (let x = -620; x < 640; x += 92) {
            for (let y = -340; y < 360; y += 92) {
                const offset = ((x + y) / 92) % 2 === 0 ? 11 : -8;
                backdrop.circle(x + offset, y, 3);
                backdrop.fill();
            }
        }

        this.worldRoot = new Node('World');
        this.worldRoot.layer = this.node.layer;
        this.worldRoot.addComponent(UITransform).setContentSize(ir.world.width, ir.world.height);
        this.node.addChild(this.worldRoot);

        const frame = this.worldRoot.addComponent(Graphics);
        frame.lineWidth = 5;
        frame.strokeColor = this.color('#4f8f67');
        frame.fillColor = this.color('#0d2019');
        frame.roundRect(-ir.world.width / 2, -ir.world.height / 2, ir.world.width, ir.world.height, 22);
        frame.fill();
        frame.stroke();
        frame.lineWidth = 1;
        frame.strokeColor = this.color('#244c36');
        for (let x = -ir.world.width / 2 + 48; x < ir.world.width / 2; x += 48) {
            frame.moveTo(x, -ir.world.height / 2 + 18);
            frame.lineTo(x, ir.world.height / 2 - 18);
        }
        for (let y = -ir.world.height / 2 + 48; y < ir.world.height / 2; y += 48) {
            frame.moveTo(-ir.world.width / 2 + 18, y);
            frame.lineTo(ir.world.width / 2 - 18, y);
        }
        frame.stroke();
    }

    private createHud(): void {
        const title = this.makeLabel('Title', this.node, this.ir!.metadata.title.toUpperCase(), 30, this.color('#ecfff6'), new Vec3(-430, 312));
        title.horizontalAlign = Label.HorizontalAlign.LEFT;
        this.scoreLabel = this.makeLabel('Score', this.node, '', 22, this.color('#ffd978'), new Vec3(-430, 272));
        this.healthLabel = this.makeLabel('Health', this.node, '', 22, this.color('#ff8b91'), new Vec3(-120, 272));
        this.timeLabel = this.makeLabel('Time', this.node, '', 22, this.color('#8edcff'), new Vec3(160, 272));
        this.objectiveLabel = this.makeLabel('Objective', this.node, '', 20, this.color('#a9d9bc'), new Vec3(390, 272));
        this.statusLabel = this.makeLabel('Status', this.node, 'MOVE: WASD / ARROWS · TOUCH: DRAG · P: PAUSE', 16, this.color('#79a990'), new Vec3(0, -326));
    }

    private createPlayer(): void {
        const ir = this.ir!;
        this.playerNode = new Node('Player');
        this.playerNode.layer = this.node.layer;
        this.playerNode.addComponent(UITransform).setContentSize(ir.player.radius * 2, ir.player.radius * 2);
        const graphics = this.playerNode.addComponent(Graphics);
        graphics.fillColor = this.color('#f2fbf7');
        graphics.circle(0, 0, ir.player.radius);
        graphics.fill();
        graphics.fillColor = this.color('#45d6a3');
        graphics.circle(0, 0, ir.player.radius * 0.58);
        graphics.fill();
        graphics.strokeColor = this.color('#baffdf');
        graphics.lineWidth = 3;
        graphics.moveTo(0, ir.player.radius * 0.65);
        graphics.lineTo(ir.player.radius * 0.65, 0);
        graphics.lineTo(0, -ir.player.radius * 0.65);
        graphics.lineTo(-ir.player.radius * 0.65, 0);
        graphics.close();
        graphics.stroke();
        this.playerNode.setPosition(this.toLocal(ir.player.spawn.x, ir.player.spawn.y));
        this.worldRoot.addChild(this.playerNode);
    }

    private createEntity(spec: RuntimeEntitySpec): void {
        const node = new Node(spec.id);
        node.layer = this.node.layer;
        node.addComponent(UITransform).setContentSize(spec.size, spec.size);
        node.setPosition(this.toLocal(spec.x, spec.y));
        const graphics = node.addComponent(Graphics);
        if (spec.type === 'collectible') {
            graphics.fillColor = this.color('#ffd65c');
            graphics.moveTo(0, spec.size / 2);
            graphics.lineTo(spec.size / 2, 0);
            graphics.lineTo(0, -spec.size / 2);
            graphics.lineTo(-spec.size / 2, 0);
            graphics.close();
            graphics.fill();
            graphics.strokeColor = this.color('#fff1ad');
            graphics.lineWidth = 2;
            graphics.stroke();
            tween(node).repeatForever(tween().to(0.8, { scale: new Vec3(1.18, 1.18, 1) }).to(0.8, { scale: Vec3.ONE })).start();
        } else if (spec.type === 'enemy') {
            graphics.fillColor = this.color('#dc5363');
            graphics.circle(0, 0, spec.size / 2);
            graphics.fill();
            graphics.fillColor = this.color('#5b1825');
            graphics.circle(-spec.size * 0.16, spec.size * 0.08, 3);
            graphics.circle(spec.size * 0.16, spec.size * 0.08, 3);
            graphics.fill();
        } else if (spec.type === 'obstacle') {
            graphics.fillColor = this.color('#466456');
            graphics.roundRect(-spec.size / 2, -spec.size / 2, spec.size, spec.size, 14);
            graphics.fill();
            graphics.strokeColor = this.color('#759987');
            graphics.lineWidth = 4;
            graphics.stroke();
        } else {
            graphics.fillColor = this.color('#263a31');
            graphics.circle(0, 0, spec.size / 2);
            graphics.fill();
            graphics.strokeColor = this.color('#53715f');
            graphics.lineWidth = 6;
            graphics.circle(0, 0, spec.size / 2 - 4);
            graphics.stroke();
        }
        this.worldRoot.addChild(node);
        this.entities.push({ spec, node, origin: node.position.clone(), direction: 1, active: true });
    }

    private updatePlayer(dt: number): void {
        const ir = this.ir!;
        let x = 0;
        let y = 0;
        if (this.pressed.has(KeyCode.KEY_A) || this.pressed.has(KeyCode.ARROW_LEFT)) x--;
        if (this.pressed.has(KeyCode.KEY_D) || this.pressed.has(KeyCode.ARROW_RIGHT)) x++;
        if (this.pressed.has(KeyCode.KEY_W) || this.pressed.has(KeyCode.ARROW_UP)) y++;
        if (this.pressed.has(KeyCode.KEY_S) || this.pressed.has(KeyCode.ARROW_DOWN)) y--;
        x += this.touchVector.x;
        y += this.touchVector.y;
        const length = Math.hypot(x, y);
        if (length === 0) return;
        const previous = this.playerNode.position.clone();
        const next = previous.clone().add(new Vec3(x / length * ir.player.speed * dt, y / length * ir.player.speed * dt));
        const radius = ir.player.radius;
        next.x = Math.max(-ir.world.width / 2 + radius, Math.min(ir.world.width / 2 - radius, next.x));
        next.y = Math.max(-ir.world.height / 2 + radius, Math.min(ir.world.height / 2 - radius, next.y));
        this.playerNode.setPosition(next);
        if (this.entities.some(entity => entity.active && entity.spec.type === 'obstacle' && this.overlaps(entity, radius * 0.9))) {
            this.playerNode.setPosition(previous);
        }
    }

    private updateEnemies(dt: number): void {
        for (const entity of this.entities) {
            if (!entity.active || entity.spec.type !== 'enemy') continue;
            const axis = entity.spec.patrolAxis || 'x';
            const speed = entity.spec.speed || 60;
            const range = entity.spec.patrolRange || 100;
            const position = entity.node.position.clone();
            position[axis] += speed * entity.direction * dt;
            if (Math.abs(position[axis] - entity.origin[axis]) >= range / 2) {
                position[axis] = entity.origin[axis] + Math.sign(position[axis] - entity.origin[axis]) * range / 2;
                entity.direction *= -1;
            }
            entity.node.setPosition(position);
        }
    }

    private resolveContacts(): void {
        const radius = this.ir!.player.radius;
        for (const entity of this.entities) {
            if (!entity.active || !this.overlaps(entity, radius)) continue;
            if (entity.spec.type === 'collectible') {
                entity.active = false;
                this.remaining--;
                this.score += entity.spec.score || 0;
                this.burst(entity.node.position, this.color('#ffe986'));
                entity.node.destroy();
                if (this.remaining === 0) this.unlockExit();
            } else if (entity.spec.type === 'enemy' && this.invulnerable <= 0) {
                this.health--;
                this.invulnerable = 1.15;
                this.burst(this.playerNode.position, this.color('#ff6f7f'));
                this.playerNode.setPosition(this.toLocal(this.ir!.player.spawn.x, this.ir!.player.spawn.y));
                if (this.health <= 0) this.finish(false, 'THE GUARDIAN PREVAILED');
            } else if (entity.spec.type === 'exit' && this.exitUnlocked) {
                this.finish(true, 'FOREST RESTORED');
            }
        }
    }

    private unlockExit(): void {
        this.exitUnlocked = true;
        const exit = this.entities.find(entity => entity.spec.type === 'exit');
        if (!exit) return;
        const graphics = exit.node.getComponent(Graphics)!;
        graphics.clear();
        graphics.fillColor = this.color('#2b604c');
        graphics.circle(0, 0, exit.spec.size / 2);
        graphics.fill();
        graphics.strokeColor = this.color('#7fffc8');
        graphics.lineWidth = 7;
        graphics.circle(0, 0, exit.spec.size / 2 - 5);
        graphics.stroke();
        tween(exit.node).repeatForever(tween().to(0.7, { scale: new Vec3(1.08, 1.08, 1) }).to(0.7, { scale: Vec3.ONE })).start();
        this.statusLabel.string = 'THE PORTAL IS OPEN — REACH IT!';
        this.statusLabel.color = this.color('#8fffd1');
    }

    private finish(won: boolean, message: string): void {
        if (this.state !== 'playing') return;
        this.state = won ? 'won' : 'lost';
        const overlay = this.makeGraphics('ResultOverlay', this.node, 1280, 720);
        overlay.fillColor = new Color(3, 14, 11, 224);
        overlay.roundRect(-360, -150, 720, 300, 28);
        overlay.fill();
        overlay.strokeColor = this.color(won ? '#67efb7' : '#ef6675');
        overlay.lineWidth = 4;
        overlay.stroke();
        this.makeLabel('ResultTitle', this.node, won ? 'VICTORY' : 'DEFEAT', 54, this.color(won ? '#8fffd1' : '#ff8b96'), new Vec3(0, 58));
        this.makeLabel('ResultMessage', this.node, message, 26, this.color('#ecfff6'), new Vec3(0, 2));
        this.makeLabel('ResultScore', this.node, `SCORE ${this.score}  ·  PRESS R TO RESTART`, 20, this.color('#b7d8c5'), new Vec3(0, -62));
    }

    private updateHud(): void {
        this.scoreLabel.string = `SCORE  ${this.score}`;
        this.healthLabel.string = `HEARTS  ${'◆'.repeat(Math.max(0, this.health))}`;
        this.timeLabel.string = `TIME  ${Math.ceil(this.secondsLeft).toString().padStart(2, '0')}`;
        this.objectiveLabel.string = this.remaining > 0 ? `CRYSTALS  ${this.remaining}` : 'PORTAL  OPEN';
    }

    private burst(position: Vec3, color: Color): void {
        for (let i = 0; i < 8; i++) {
            const particle = new Node('Spark');
            particle.layer = this.node.layer;
            particle.addComponent(UITransform).setContentSize(8, 8);
            const graphics = particle.addComponent(Graphics);
            graphics.fillColor = color;
            graphics.circle(0, 0, 4);
            graphics.fill();
            particle.setPosition(position);
            this.worldRoot.addChild(particle);
            const angle = i / 8 * Math.PI * 2;
            tween(particle).to(0.38, { position: position.clone().add(new Vec3(Math.cos(angle) * 44, Math.sin(angle) * 44)) })
                .call(() => particle.destroy()).start();
        }
    }

    private overlaps(entity: RuntimeEntity, playerRadius: number): boolean {
        const distance = Vec3.distance(this.playerNode.position, entity.node.position);
        const entityRadius = entity.spec.type === 'obstacle' ? entity.spec.size * 0.55 : entity.spec.size * 0.5;
        return distance < playerRadius + entityRadius;
    }

    private onKeyDown(event: EventKeyboard): void {
        if (event.keyCode === KeyCode.KEY_P && (this.state === 'playing' || this.state === 'paused')) {
            this.state = this.state === 'playing' ? 'paused' : 'playing';
            this.statusLabel.string = this.state === 'paused' ? 'PAUSED · PRESS P TO CONTINUE' : 'MOVE: WASD / ARROWS · TOUCH: DRAG · P: PAUSE';
            return;
        }
        if (event.keyCode === KeyCode.KEY_R && (this.state === 'won' || this.state === 'lost')) {
            this.bootstrap(this.ir!);
            return;
        }
        this.pressed.add(event.keyCode);
    }

    private onKeyUp(event: EventKeyboard): void { this.pressed.delete(event.keyCode); }

    private onTouchStart(event: EventTouch): void { this.touchOrigin = event.getUILocation(); }

    private onTouchMove(event: EventTouch): void {
        if (!this.touchOrigin) return;
        const current = event.getUILocation();
        const dx = current.x - this.touchOrigin.x;
        const dy = current.y - this.touchOrigin.y;
        const length = Math.hypot(dx, dy);
        this.touchVector.set(length > 12 ? dx / length : 0, length > 12 ? dy / length : 0);
    }

    private onTouchEnd(): void { this.touchOrigin = null; this.touchVector.set(0, 0); }

    private failClosed(message: string): void {
        this.state = 'error';
        this.clearRuntimeChildren();
        const background = this.makeGraphics('ErrorBackground', this.node, 1280, 720);
        background.fillColor = this.color('#1b0b10');
        background.rect(-640, -360, 1280, 720);
        background.fill();
        this.makeLabel('ErrorTitle', this.node, 'RUNTIME BLOCKED', 42, this.color('#ff7d89'), new Vec3(0, 35));
        this.makeLabel('ErrorMessage', this.node, message, 21, this.color('#f2c2c7'), new Vec3(0, -25));
    }

    private clearRuntimeChildren(): void {
        for (const child of this.node.children.slice()) {
            if (child.name !== 'Camera') child.destroy();
        }
    }

    private makeGraphics(name: string, parent: Node, width: number, height: number): Graphics {
        const node = new Node(name);
        node.layer = this.node.layer;
        node.addComponent(UITransform).setContentSize(width, height);
        parent.addChild(node);
        return node.addComponent(Graphics);
    }

    private makeLabel(name: string, parent: Node, text: string, size: number, color: Color, position: Vec3): Label {
        const node = new Node(name);
        node.layer = this.node.layer;
        node.addComponent(UITransform).setContentSize(520, size + 16);
        node.setPosition(position);
        parent.addChild(node);
        const label = node.addComponent(Label);
        label.string = text;
        label.fontSize = size;
        label.lineHeight = size + 4;
        label.color = color;
        label.horizontalAlign = Label.HorizontalAlign.CENTER;
        label.verticalAlign = Label.VerticalAlign.CENTER;
        return label;
    }

    private toLocal(x: number, y: number): Vec3 {
        return new Vec3(x - this.ir!.world.width / 2, this.ir!.world.height / 2 - y, 0);
    }

    private color(hex: string): Color {
        const value = hex.replace('#', '');
        if (!/^[0-9a-fA-F]{6}$/.test(value)) return Color.WHITE.clone();
        return new Color(parseInt(value.slice(0, 2), 16), parseInt(value.slice(2, 4), 16), parseInt(value.slice(4, 6), 16), 255);
    }
}
