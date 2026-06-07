const params = new URLSearchParams(window.location.search);
const title = params.get("title") || "AI Game Demo";
const projectUuid = params.get("projectUuid") || "-";

document.querySelector("#gameTitle").textContent = title;
document.querySelector("#projectMeta").textContent = `projectUuid: ${projectUuid}`;

const canvas = document.querySelector("#gameCanvas");
const ctx = canvas.getContext("2d");
const gemCountEl = document.querySelector("#gemCount");
const statusEl = document.querySelector("#gameStatus");
const restartButton = document.querySelector("#restartButton");

const keys = new Set();
const walls = [
  { x: 130, y: 80, w: 30, h: 260 },
  { x: 300, y: 0, w: 30, h: 230 },
  { x: 470, y: 190, w: 30, h: 230 }
];

let player;
let gems;
let portal;
let gameStatus;

restart();
requestAnimationFrame(loop);

window.addEventListener("keydown", (event) => {
  keys.add(event.key.toLowerCase());
});

window.addEventListener("keyup", (event) => {
  keys.delete(event.key.toLowerCase());
});

restartButton.addEventListener("click", restart);

function restart() {
  player = { x: 40, y: 40, size: 24, speed: 3 };
  gems = [
    { x: 230, y: 70, size: 16, collected: false },
    { x: 410, y: 330, size: 16, collected: false },
    { x: 620, y: 110, size: 16, collected: false }
  ];
  portal = { x: 650, y: 340, w: 42, h: 42 };
  gameStatus = "RUNNING";
  updateHud();
}

function loop() {
  update();
  draw();
  requestAnimationFrame(loop);
}

function update() {
  if (gameStatus !== "RUNNING") {
    return;
  }

  let dx = 0;
  let dy = 0;

  if (keys.has("arrowleft") || keys.has("a")) dx -= player.speed;
  if (keys.has("arrowright") || keys.has("d")) dx += player.speed;
  if (keys.has("arrowup") || keys.has("w")) dy -= player.speed;
  if (keys.has("arrowdown") || keys.has("s")) dy += player.speed;

  movePlayer(dx, 0);
  movePlayer(0, dy);

  gems.forEach((gem) => {
    if (!gem.collected && rectsOverlap(playerRect(), gemRect(gem))) {
      gem.collected = true;
    }
  });

  if (gems.every((gem) => gem.collected) && rectsOverlap(playerRect(), portal)) {
    gameStatus = "WIN";
  }

  updateHud();
}

function movePlayer(dx, dy) {
  const next = { ...player, x: player.x + dx, y: player.y + dy };
  const nextRect = { x: next.x, y: next.y, w: next.size, h: next.size };

  const outside = next.x < 0 || next.y < 0 || next.x + next.size > canvas.width || next.y + next.size > canvas.height;
  const blocked = walls.some((wall) => rectsOverlap(nextRect, wall));

  if (!outside && !blocked) {
    player.x = next.x;
    player.y = next.y;
  }
}

function draw() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  drawGrid();

  ctx.fillStyle = "#26354d";
  walls.forEach((wall) => ctx.fillRect(wall.x, wall.y, wall.w, wall.h));

  ctx.fillStyle = gems.every((gem) => gem.collected) ? "#a78bfa" : "#475569";
  ctx.fillRect(portal.x, portal.y, portal.w, portal.h);
  ctx.fillStyle = "#e9d5ff";
  ctx.fillText("EXIT", portal.x + 7, portal.y + 27);

  gems.forEach((gem) => {
    if (!gem.collected) {
      ctx.fillStyle = "#facc15";
      ctx.beginPath();
      ctx.arc(gem.x, gem.y, gem.size, 0, Math.PI * 2);
      ctx.fill();
    }
  });

  ctx.fillStyle = "#38bdf8";
  ctx.fillRect(player.x, player.y, player.size, player.size);

  if (gameStatus === "WIN") {
    ctx.fillStyle = "rgba(15, 23, 42, 0.78)";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 34px Arial";
    ctx.fillText("Demo Cleared!", 245, 190);
    ctx.font = "18px Arial";
    ctx.fillText("This playable page was opened from the AI workflow demoUrl.", 145, 225);
  }
}

function drawGrid() {
  ctx.fillStyle = "#111827";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = "#1f2937";
  ctx.lineWidth = 1;

  for (let x = 0; x <= canvas.width; x += 30) {
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, canvas.height);
    ctx.stroke();
  }
  for (let y = 0; y <= canvas.height; y += 30) {
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(canvas.width, y);
    ctx.stroke();
  }
}

function updateHud() {
  gemCountEl.textContent = gems.filter((gem) => gem.collected).length;
  statusEl.textContent = gameStatus;
}

function playerRect() {
  return { x: player.x, y: player.y, w: player.size, h: player.size };
}

function gemRect(gem) {
  return { x: gem.x - gem.size, y: gem.y - gem.size, w: gem.size * 2, h: gem.size * 2 };
}

function rectsOverlap(a, b) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}
