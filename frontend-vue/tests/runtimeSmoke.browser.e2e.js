import { test, expect } from "@playwright/test";
import { spawn } from "node:child_process";
let vite;
async function ready(url) { const deadline=Date.now()+10_000; while(Date.now()<deadline){ try{if((await fetch(url)).ok)return;}catch{} await new Promise(r=>setTimeout(r,100)); } throw new Error("runtime preview timeout"); }
test.beforeAll(async()=>{ vite=spawn(process.execPath,["node_modules/vite/bin/vite.js","--host","127.0.0.1","--port","5175"],{stdio:"ignore"}); await ready("http://127.0.0.1:5175"); });
test.afterAll(()=>vite?.kill());
for (const viewport of [{width:1280,height:800},{width:375,height:812}]) test(`Phaser readiness ${viewport.width}px`,async({page})=>{ const errors=[]; page.on("pageerror",e=>errors.push(e.message)); await page.setViewportSize(viewport); await page.goto("http://127.0.0.1:5175/demo/play"); await expect(page.locator("canvas")).toBeVisible({timeout:10_000}); await expect(page.locator(".game-canvas")).toHaveAttribute("data-runtime-ready","true",{timeout:10_000}); expect(errors).toEqual([]); });
