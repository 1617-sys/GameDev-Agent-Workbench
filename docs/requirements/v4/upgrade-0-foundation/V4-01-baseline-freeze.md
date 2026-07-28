# V4-01 当前基线冻结

## 目标

记录 V4 改造前可复现的构建、测试、API 和 Runtime 基线，不修改生产代码。

## 允许修改

- 新建 `docs/reports/V4-baseline-report.md`
- 必要时在报告中引用已有测试证据

## 禁止修改

- `backend-java/src/**`
- `python-agent/app/**`
- `frontend-vue/src/**`
- 数据库、依赖版本和 Docker 配置

## 工作内容

1. 运行 Java、Python、前端单元测试及 Runtime smoke；失败必须原样记录，不得为了得到绿色结果修代码。
2. 记录 Java/Python/前端版本、测试数量、通过/失败/跳过数和耗时。
3. 列出旧同步/单 Agent API、前端调用方及建议弃用阶段。
4. 记录当前 Phaser Runtime 入口、GameConfig 合约入口和遥测入口。
5. 记录工作树已有修改，明确不把用户改动归入本任务。

## 验收标准

- 新环境可按报告命令复现结果；
- 报告区分测试通过、未运行和环境阻塞；
- API 清单包含路径、调用方和当前用途；
- `git diff --` 只出现报告文件。

## 必须执行

```powershell
cd backend-java; mvn test
cd ..\python-agent; pytest
cd ..\frontend-vue; npm run test:unit
npm run test:runtime-smoke
```

若命令因外部依赖失败，记录命令、错误和环境条件，不扩大任务范围。
