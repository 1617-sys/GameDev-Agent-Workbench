# Local Playable Artifact Contract

> 状态：DRAFT / 待实现
> 目的：给“生成了一个可交付的本地小游戏”设置不可模糊的验收边界

## 1. Artifact Set

```text
local-game-package/
├── artifact-manifest.json
├── provenance/
│   ├── game-spec.json
│   ├── runtime-ir.json
│   └── build-request.json
├── game/                       # Cocos Web Mobile 构建输出，内部结构不硬编码
├── launch.ps1                  # 或等价的仓库可信启动器
├── README.md
└── evidence/
    ├── gate-summary.json
    ├── build-record.json
    └── screenshots/
```

最终交付可以是上述目录的 ZIP。Cocos 输出结构由锁定的构建器决定；Java manifest 列出全部文件、内容 hash、MIME、大小、来源和用途。

## 2. 本地独立可玩

产物必须：

- 从工作台下载并解压；
- 通过包内说明和仓库可信启动器启动本地静态服务；
- 不要求登录工作台或连接 Java/Python API 才能进入核心玩法；
- 展示开始、进行中、暂停、胜利/失败和重新开始状态；
- 接受真实键盘或触摸输入；
- 无致命控制台错误；
- 终局输出本地 session summary。

由于浏览器模块和资源策略限制，V5 不强制支持直接双击 `file://`。但启动过程必须是本地、确定、无需手工安装额外依赖；是否内嵌静态服务器由任务卡决定。

## 3. 安全约束

- 禁止路径穿越、远程脚本、动态 `eval` 和未登记网络请求；
- 只允许 capability registry 登记的 Runtime Shell、Prefab、Profile 和 Asset Pack；
- Agent 不得修改 Cocos scene/prefab/meta、Runtime 源码、shader、插件和 build template；
- HTML/JSON/资源分别设置大小上限；
- V5 包不得包含 AppID、平台密钥、上传脚本或小游戏平台配置。

## 4. Manifest 必填事实

- artifact id、project id、prototype version；
- `sourceDigest`：冻结 GameSpec、Java compiler、capability、Cocos/Runtime Shell、local build profile 和资源输入的语义身份；
- `packageDigest`：本次 ZIP 完整内容身份；由于 ZIP 不能在自身 manifest 中无环地记录自身摘要，Java 在封包完成后将该值持久化到 `GenerationRun`/Artifact 记录并作为下载校验与审批绑定事实。包内 manifest 记录不含自身的 `payloadDigest` 和 `packageDigestBinding` 规则；
- GameSpec、runtime IR 和 simulation projection digest；
- Java control-plane、Cocos Creator、Runtime Shell、capability registry 版本；
- Asset/Animation/Camera/Feedback/UI/Audio profile digests；
- Build Worker、退出码、日志 digest、文件清单及 SHA-256；
- gate suite/version/result、mock/real model 标记；
- compile run、GenerationRun 和 DirectorRun。

自动缓存和“相同输入”判断使用 `sourceDigest`；下载完整性和人工审批绑定确切的 `packageDigest`。在 clean build 证据证明 Cocos 输出可复现前，不假设两个相同 source 会产生相同 package bytes。

## 5. 发布门禁

| Gate | 要求 | 失败行为 |
| --- | --- | --- |
| GameSpec/Capability | 必须 | 阻断 |
| Manifest 与 hash | 必须 | 阻断 |
| 禁止远程代码/凭据/平台配置 | 必须 | 阻断 |
| Cocos Web Mobile build | 必须 | 阻断 |
| 包外本地启动、加载与输入 smoke | 必须 | 阻断 |
| deterministic simulation/replay | 必须 | 阻断 |
| deterministic Player | 必须 | 阻断 |
| Visual Quality 机器检查 | 必须 | 阻断 |
| LLM Player 对照 | 可选 | 明确标记结果 |
| 人工试玩批准 | APPROVED 必须 | 保持 DRAFT |

## 6. 不算本地可玩产物

- 只有 GameSpec/GameConfig JSON；
- 仍使用 V4 Phaser 页面；
- 只有截图、视频或测试报告；
- 只能嵌在工作台页面内运行；
- 页面能加载但无法完成输入到终局；
- 运行时依赖模型补写代码；
- 只有 Cocos 工程，没有可下载的构建包；
- 伪装成任何小游戏平台开发包。
