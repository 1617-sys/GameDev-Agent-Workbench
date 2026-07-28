package com.example.gameworkbench.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;
import org.springframework.stereotype.Component;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class PrototypePackageBuilder {
    public static final String RUNTIME_VERSION="offline-arcade-collect/1";
    private static final Pattern SENSITIVE_CONTENT=Pattern.compile(
            "(?i)(https?://|jdbc:|mysql://|bearer\\s+[a-z0-9._~-]+|api[_-]?key\\s*[:=]|password\\s*[:=]|secret\\s*[:=]|(?:access[_-]?|refresh[_-]?|auth[_-]?)?token\\s*[:=])");
    private final ObjectMapper json;

    public byte[] build(FrozenPrototypeExport input,String frozenDigest) {
        try {
            SortedMap<String,byte[]> files=new TreeMap<>();
            text(files,"README.md",readme(input));
            text(files,"design/brief.md",heading("Prototype Brief",input.prototypeBrief()));
            text(files,"design/game-concept.md",artifact(input,"gameConcept"));
            text(files,"design/core-loop.md",artifact(input,"coreLoop"));
            text(files,"development/tasks.md",artifact(input,"tasks"));
            text(files,"config/game-config.json",input.gameConfig());
            text(files,"resources/manifest.json",input.resourceManifest());
            text(files,"resources/offline-map.json",offlineMap(input.resourceManifest()));
            addSvgAssets(files);
            text(files,"playtest/summary.json",input.playtestSummary());
            text(files,"evaluation/balance-suggestion.json",input.balanceSuggestion().content());
            text(files,"demo/index.html",INDEX_HTML);
            text(files,"demo/game-config.js","window.PROTOTYPE_CONFIG="+input.gameConfig().strip()+";\n");
            text(files,"demo/runtime.js",RUNTIME_JS);
            byte[] manifest=manifest(input,frozenDigest,files);
            text(files,"manifest.json",new String(manifest,StandardCharsets.UTF_8));
            security(files);
            return zip(files,input.versionCreatedAt().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        } catch(BusinessException exception) { throw exception; }
        catch(Exception exception) { throw new IllegalStateException("Unable to build prototype package",exception); }
    }

    private byte[] manifest(FrozenPrototypeExport in,String frozenDigest,SortedMap<String,byte[]> files) throws Exception {
        ObjectNode root=json.createObjectNode();root.put("packageFormatVersion",in.formatVersion());root.put("runtimeVersion",RUNTIME_VERSION);
        root.put("prototypeVersionUuid",in.versionUuid());root.put("versionNumber",in.versionNumber());root.put("configDigest",in.configDigest());
        root.put("resourceManifestDigest",in.resourceManifestDigest());root.put("playtestSnapshotAt",in.playtestSnapshotAt());
        root.put("playtestSummaryDigest",in.playtestSummaryDigest());root.put("frozenInputDigest",frozenDigest);root.put("timestampSource","PROTOTYPE_VERSION_CREATED_AT");
        ArrayNode entries=root.putArray("files"); for(var entry:files.entrySet()) entries.addObject().put("path",entry.getKey()).put("sha256",digest(entry.getValue())).put("size",entry.getValue().length);
        root.put("manifestSelfExcludedFromFileList",true);return canonical(root).getBytes(StandardCharsets.UTF_8);
    }
    private byte[] zip(SortedMap<String,byte[]> files,long timestamp) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) { zip.setLevel(9); for(var file:files.entrySet()) {
            safePath(file.getKey()); ZipEntry entry=new ZipEntry(file.getKey());entry.setTime(timestamp);entry.setComment(null);zip.putNextEntry(entry);zip.write(file.getValue());zip.closeEntry(); } }
        return output.toByteArray();
    }
    private void security(SortedMap<String,byte[]> files) { for(var file:files.entrySet()) { safePath(file.getKey());String value=new String(file.getValue(),StandardCharsets.UTF_8).replace("http://www.w3.org/2000/svg","");if(SENSITIVE_CONTENT.matcher(value).find())throw new BusinessException(ErrorCode.EXPORT_SECURITY_REJECTED); } }
    private void safePath(String path){if(path.isBlank()||path.startsWith("/")||path.contains("\\")||Arrays.asList(path.split("/")).contains("..")||path.chars().anyMatch(c->c<32))throw new BusinessException(ErrorCode.EXPORT_SECURITY_REJECTED);}
    private void text(Map<String,byte[]> files,String path,String value){files.put(path,(value.replace("\r\n","\n").replace('\r','\n').stripTrailing()+"\n").getBytes(StandardCharsets.UTF_8));}
    private String artifact(FrozenPrototypeExport in,String key){return heading(key,displayContent(in.designArtifacts().get(key).content()));}
    private String displayContent(String value){try{JsonNode node=json.readTree(value);if(node.isObject()&&node.path("content").isTextual())return node.path("content").asText();}catch(Exception ignored){/* Plain text artifacts are already displayable. */}return value;}
    private String heading(String title,String body){return "# "+title+"\n\n"+(body==null?"":body.strip())+"\n";}
    private String readme(FrozenPrototypeExport in){return "# "+in.projectName()+"\n\nImmutable prototype package for version "+in.versionNumber()+" (`"+in.versionUuid()+"`).\n\nOpen `demo/index.html` directly in a modern browser. All runtime code and resources are local to this archive.\n";}
    private String offlineMap(String manifest) throws Exception {ObjectNode root=json.createObjectNode();root.put("schemaVersion","1.0");ObjectNode assets=root.putObject("assets");for(JsonNode item:json.readTree(manifest).path("resources")){String category=item.path("category").asText();assets.put(item.path("key").asText(),category.equals("sound")?"generated-tone":"images/"+category+".svg");}return canonical(root);}
    private String canonical(ObjectNode node) throws Exception {return json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(node);}
    private String digest(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private void addSvgAssets(Map<String,byte[]> files){text(files,"resources/images/player.svg",svg("circle","#5EEAD4"));text(files,"resources/images/collectible.svg",svg("diamond","#FACC15"));text(files,"resources/images/enemy.svg",svg("square","#FB7185"));text(files,"resources/images/exit.svg",svg("portal","#22C55E"));text(files,"resources/images/obstacle.svg",svg("square","#64748B"));}
    private String svg(String shape,String color){String body=switch(shape){case"circle"->"<circle cx=\"32\" cy=\"32\" r=\"25\"/>";case"diamond"->"<path d=\"M32 4 60 32 32 60 4 32Z\"/>";case"portal"->"<rect x=\"10\" y=\"4\" width=\"44\" height=\"56\" rx=\"18\"/>";default->"<rect x=\"5\" y=\"8\" width=\"54\" height=\"48\" rx=\"8\"/>";};return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\"><g fill=\""+color+"\">"+body+"</g></svg>";}

    private static final String INDEX_HTML="""
<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Offline Prototype</title><style>body{margin:0;background:#080d18;color:#fff;font:16px system-ui;display:grid;place-items:center;min-height:100vh}main{width:min(100%,1000px)}canvas{width:100%;height:auto;background:#111827;touch-action:none}.bar{display:flex;gap:12px;align-items:center;justify-content:space-between;padding:10px}button{padding:10px 16px}.pad{display:flex;gap:8px;justify-content:center}.pad button{min-width:52px}</style></head><body><main><div class="bar"><strong id="title">Prototype</strong><span id="hud">Ready</span><button id="start">Start / Restart</button></div><canvas id="game"></canvas><div class="pad"><button data-key="ArrowLeft">Left</button><button data-key="ArrowUp">Up</button><button data-key="ArrowDown">Down</button><button data-key="ArrowRight">Right</button></div></main><script src="game-config.js"></script><script src="runtime.js"></script></body></html>
""";
    private static final String RUNTIME_JS="""
(()=>{'use strict';const c=window.PROTOTYPE_CONFIG,canvas=document.querySelector('#game'),x=canvas.getContext('2d'),hud=document.querySelector('#hud');canvas.width=c.viewport.width;canvas.height=c.viewport.height;const keys=new Set(),radius=(n)=>n.size/2;let state;
const reset=()=>{state={playing:false,won:false,lost:false,px:c.world.spawn.x,py:c.world.spawn.y,hp:c.player.maxHealth,items:new Set(),score:0,left:c.balance.timeLimitSeconds,last:performance.now(),hitAt:-9999};draw();};
const key=(name,on)=>on?keys.add(name):keys.delete(name);addEventListener('keydown',e=>key(e.key,true));addEventListener('keyup',e=>key(e.key,false));document.querySelectorAll('[data-key]').forEach(b=>{b.onpointerdown=()=>key(b.dataset.key,true);b.onpointerup=b.onpointerleave=()=>key(b.dataset.key,false)});document.querySelector('#start').onclick=()=>{reset();state.playing=true;state.last=performance.now()};
const overlap=(ax,ay,ar,bx,by,br)=>Math.hypot(ax-bx,ay-by)<ar+br;const blocked=(nx,ny)=>c.world.obstacles.some(o=>nx>o.x-o.width/2-radius(c.player)&&nx<o.x+o.width/2+radius(c.player)&&ny>o.y-o.height/2-radius(c.player)&&ny<o.y+o.height/2+radius(c.player));
function update(now){const dt=Math.min(.05,(now-state.last)/1000);state.last=now;if(state.playing){let dx=(keys.has('ArrowRight')||keys.has('d')?1:0)-(keys.has('ArrowLeft')||keys.has('a')?1:0),dy=(keys.has('ArrowDown')||keys.has('s')?1:0)-(keys.has('ArrowUp')||keys.has('w')?1:0),l=Math.hypot(dx,dy)||1,nx=Math.max(radius(c.player),Math.min(c.world.width-radius(c.player),state.px+dx/l*c.player.speed*dt)),ny=Math.max(radius(c.player),Math.min(c.world.height-radius(c.player),state.py+dy/l*c.player.speed*dt));if(!blocked(nx,ny)){state.px=nx;state.py=ny}state.left=Math.max(0,state.left-dt);if(!state.left&&c.objectives.loseConditions.includes('time_expired'))finish(false);c.entities.collectibles.forEach(i=>{if(!state.items.has(i.id)&&overlap(state.px,state.py,radius(c.player),i.x,i.y,i.size/2)){state.items.add(i.id);state.score+=i.score}});c.entities.enemies.forEach(e=>{if(overlap(state.px,state.py,radius(c.player),e.x,e.y,e.size/2)&&now-state.hitAt>=c.player.hitInvulnerabilityMs){state.hitAt=now;state.hp=Math.max(0,state.hp-c.behaviors.contact.damage);if(!state.hp&&c.objectives.loseConditions.includes('health_depleted'))finish(false)}});const q=c.entities.exit;if(state.items.size>=c.objectives.targetCollectibles&&state.px>q.x-q.width/2&&state.px<q.x+q.width/2&&state.py>q.y-q.height/2&&state.py<q.y+q.height/2){state.score+=c.balance.winBonus;finish(true)}}draw();requestAnimationFrame(update)}
function finish(win){state.playing=false;state.won=win;state.lost=!win}function rect(o,color){x.fillStyle=color;x.fillRect(o.x-o.width/2,o.y-o.height/2,o.width,o.height)}function draw(){x.fillStyle=c.presentation.palette.floor;x.fillRect(0,0,canvas.width,canvas.height);c.world.obstacles.forEach(o=>rect(o,c.presentation.palette.wall));const q=c.entities.exit;rect(q,state.items.size>=c.objectives.targetCollectibles?c.presentation.palette.exit:'#334155');c.entities.collectibles.forEach(i=>{if(!state.items.has(i.id)){x.fillStyle=c.presentation.palette.item;x.beginPath();x.arc(i.x,i.y,i.size/2,0,7);x.fill()}});c.entities.enemies.forEach(e=>{x.fillStyle=c.presentation.palette.enemy;x.beginPath();x.arc(e.x,e.y,e.size/2,0,7);x.fill()});x.fillStyle=c.presentation.palette.player;x.beginPath();x.arc(state.px,state.py,radius(c.player),0,7);x.fill();hud.textContent=(state.won?'WON':state.lost?'LOST':state.playing?'PLAYING':'READY')+' | '+state.items.size+'/'+c.objectives.targetCollectibles+' | Score '+state.score+' | HP '+state.hp+' | '+Math.ceil(state.left)+'s'}reset();requestAnimationFrame(update)})();
""";
}
