# V6 Mini-Game Platform Boundary

> 状态：DEFERRED TO V6
> V5 不实现本文件中的任何平台能力

V5 只生成可下载、可在本地启动的 Cocos Web Mobile 游戏包。微信、抖音、支付宝、OPPO、vivo、华为、荣耀等小游戏或快游戏平台统一放到 V6 评估。

V6 可以复用的 V5 边界只有：

- GameSpec 不包含平台密钥、AppID 或平台专用 API；
- Java Artifact 记录 Runtime Shell、资源、构建配置和 package digest；
- Cocos Runtime 将平台能力放在独立 adapter 后面；
- 游戏核心不依赖登录、支付、广告和排行榜才能运行。

V6 开始前必须重新核对各平台当时有效的官方规则，并分别设计：

- build profile 与平台扩展版本；
- 包体、分包、资源和网络策略；
- 生命周期、输入和开放能力桥接；
- 开发者工具、真机与兼容矩阵；
- 账号、凭据、审核、上传、回滚和合规边界。

不同平台产生不同开发包，不存在一个无需适配即可跨平台提交的“通用小游戏包”。
