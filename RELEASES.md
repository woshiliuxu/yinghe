# 影盒发版与版本管理

> 影盒是 TVBox（takagen99/Box）的壳 + UI 重写。**纯壳原则：出厂不内置任何数据源、
> 不内置任何第三方密钥，所有配置由用户通过手机端（`http://电视IP:9978`）自行添加。**

## 版本号规范

- 唯一来源：`app/build.gradle` 顶部 `appVerName` / `appVerCode`（verCode = verName × 10）
- Git tag：`v<版本号>`（如 `v0.11.6`），与 APK 元数据、文件名三者一致
- APK 命名：`yinghe-<版本>-glass.apk`，产物统一放仓库外的 `dist/` 目录
- 只打 **armeabi 单包**（目标设备 Z9X 4K 高亮版是 32 位机）：
  ```
  JAVA_HOME="/e/kaifa/IntelliJ IDEA 2024.1.2/jbr" ./gradlew.bat assembleArmeabiGenericNormalDebug --offline
  cp app/build/outputs/apk/armeabiGenericNormal/debug/TVBox_debug-armeabi-generic-java.apk dist/yinghe-<版本>-glass.apk
  ```

## 发版流程

1. 改 `app/build.gradle` 的 `appVerName` / `appVerCode`
2. 编译 → 拷贝 APK 到 `dist/`
3. `git commit` + `git tag -a v<版本> -m "<版本要点>"`
4. `git push mine master --tags`（远端 `mine` = github.com/woshiliuxu/Box）
5. GitHub Releases 页面新建 Release（选 tag），上传对应 APK 到附件

## 版本历史

| 版本 | 要点 |
| --- | --- |
| 0.1 | 首版（TVBox 壳 + 基础改造） |
| 0.2 ~ 0.8 | 液态玻璃 UI 迭代（1280×720 设计稿、首页海报墙、详情页重排） |
| 0.9 ~ 0.9.9 | 搜索 / 快搜 / 播放器细节打磨 |
| 0.10.0 ~ 0.10.3 | 手机助手页（9978）iOS 化重做 |
| 0.10.4 | 多配置接口管理 + 全弹窗玻璃化 |
| 0.10.5 ~ 0.10.8 | 设置页 A 方案（左栏三分组） |
| 0.10.9 ~ 0.10.10 | 首页内容垂直居中 + 导航回底部 |
| 0.10.11 | 手机端搜索历史 |
| 0.11.0 | TMDB 首页大图轮播（周榜 + 右上切换器） |
| 0.11.1 | TMDB 密钥可在设置页配置 |
| 0.11.2 | 首屏焦点修复（userInteracted 门）+ 手机端配 TMDB token |
| 0.11.3 | 大图切换修复（焦点回调）+ 间距 + 回程记忆 |
| 0.11.4 | 方案 B 右缘竖排切换器 + 单片多剧照轮播（8 秒） |
| 0.11.5 | 配置弹窗手机优先（TV 只出二维码）+ 删 13 个弹窗阴影 |
| 0.11.6 | **纯壳化**：删除内置默认源与内置 TMDB token；首次启动自动弹扫码配置；手机端配置接口/直播/EPG/代理真正落盘并自动重启；版本号入 build.gradle |

## 历史版本说明

`v0.1 ~ v0.11.5` 时期的代码没有按版本快照提交（只有零散 commit），
APK 产物保留在本地 `dist/` 目录；如需在 GitHub Release 提供历史版本，
直接把对应 APK 作为附件上传即可（一个版本一个 Release 或合并为一个
「历史版本」Release 均可）。自 `v0.11.6` 起每个版本都有 commit + tag 对应。

## 0.11.8（2026-11-09）— 批量配置 + TMDB 弹窗重设计

- 配置接口弹窗：副标题显示「共 N 个数据源」，列表超过 3 行时底部渐隐提示可滚
- 数据源批量添加：手机端一次粘贴多个（换行 / 逗号 / 分号分隔），第 1 个默认生效，其余进配置列表
- TMDB 密钥弹窗重做（方案 A）：左信息面板（徽标 / 白底等宽掩码 / 配置日期）+ 右二维码，与配置接口弹窗同宽；未配置时居中一张码
- 手机端全部配置输入改多行文本域：超长 token 自动折行可逐段核对；多行字段回车 = 换行，Ctrl/⌘+回车提交
