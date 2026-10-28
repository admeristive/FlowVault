# 重序用文档

这份文件只管一件事：下次回来（可能就是几周后的我自己），怎么不重新摸一遍就能接上。

代码最后一笔是 `c609fb6`，其后只动过文档。入库 81 只文件，其中 50 只 .kt / 6,757 行，12 张表 / 10 个索引，`src/test` 与 `src/androidTest` 都不存在（零只测试）。
笔数和哈希是会变的读数，回来先 `git rev-list --count HEAD`、`git log --oneline -1` 现取，别照抄这一段。

闸口读数：`BUILD SUCCESSFUL`，lint `0 errors, 3 warnings`。这三条是基线，改动之后必须回到这三个数。

第三条（`AndroidGradlePluginVersion`，提示 Gradle 9.8.2 可用）是公开前那次改动才露出来的：wrapper 的发行版地址原来指向本地 `file:`，那个探测器只在 `http(s)` 地址上看，于是它一直不响。想消掉它得升到 9.8.2 并重下一次发行版，一个补丁版本，不值。

## 一、做了什么

### 功能面（阶段 1-7，全在基线那一笔里）

三态各自一屏：攻坚（计时 + 当日待办 + 胶囊）、回血（休息 + TXT 阅读器 + 闪卡 + 胶囊）、日常（时间/钱/抽查/轨迹/备份）。
底层是裸 SQLite（`SQLiteOpenHelper`，不是 Room），12 张表；每一次写都在事务里同笔补一条 `timeline_events`，所以"今天的轨迹"能回放当天干过什么。
读书侧只存来源地址和读到第几字，正文不入库；闪卡整组入库，来源路径只是出处记录。
过路费闸：抽卡前先问几张，答够六成（向上取整，全整数算）才放行，答没答都留痕。
备份：一张 JSON 装整个库，两步确认，整表替换，一个事务，中途挂了就回滚。

### 这一轮（美化 + 收尾）

| 提交 | 做了什么 |
| --- | --- |
| `0f88b9b` | 色板改三态分色，卡片上赛璐璐材质；中性底往暖里挪 |
| `f12db34` | 标题换站酷快乐体，只上标题档，授权文本入库 |
| `bcbc9d2` | 看板娘常驻标题下方，随态换姿势（三张图叠着只动透明度） |
| `4cd69dc` | 按钮分"动作/选项"两型、间距收进组件；空态换六枚自绘描边小图，删掉 `EmptyLine` |
| `668bb10` | 六个分组头挂真实计数；标题加一圈 1.2dp 描边（贴纸感） |
| `1ccb5ed` | 正在走一趟时不许恢复（两道闸）；卡组结算的两个读数挪回 IO 线程 |
| `7284875` | 删 9 个只写不读的符号；摘掉预览工具链依赖；备份规则按系统版本写全；图标目录去掉多余的 v26 后缀；三处过期注释 |
| `c609fb6` | 恢复那颗按钮在计时时当场挡下，不再等他挑完文件才拒绝 |
| `b6375c3` | 补 README 与这份重序文档 |

`b6375c3` 之后还有几笔，全是这份文档自己的修订，不逐条列。

`7284875` 删掉的九个：`TodoDao.add`、`TodoDao.delete`、`LedgerDao.add`、`LedgerDao.delete`、`LedgerDao.sumByDay`、`LedgerDao.listCategories`、`LedgerCategory`、`NovelSource.sizeOf`、`PendingNote`。
它们全是"接口留着、没人调"——写路径统一走 `insertIn`（胶囊要和它生成的那一行同笔提交），所以外面那层壳是空的。删之前确认过 `TimelineWriter.ACTION_DELETE` 不会变孤儿：`FlashDao` 和 `NovelDao` 还在用它。

## 二、界面上现在有什么（对着这张表找代码）

- 三态切换：`ui/components/ModeSwitcher.kt`，格子实测 48dp 高。
- 每一屏的卡：`ui/screen/FocusScreen.kt`（176 行）、`BreakScreen.kt`（468 行）、`DailyScreen.kt`（326 行）。
- 文本按钮只有一枚组件：`ui/components/ActionLabel.kt`，全项目 45 个调用点（屏上 31、组件里 14）。要么带描边（动作）要么带底色（选项），不混用。
- 空态：`ui/components/EmptyState.kt`，六枚 `EmptyGlyph` 全是 `Canvas` 画的矢量。
- 状态持有者五个：`DayBoard`（今天）、`PomodoroClock`（计时）、`NovelShelf`（书）、`DeckShelf`（卡）、`BackupKit`（备份）。
- 日志浮窗：`ui/debug/DebugPanel.kt`，长按标题开合。

## 三、搁置了什么（原因、代价、从哪起）

1. **`planned_pomos` 没有输入口。** `CaptureDao.kt:55` 把它硬写成 0，而 `DayRows.kt:109-112` 已经会显示 `已完成/计划`。因为待办只能由胶囊以 `!` 前缀生成，界面上根本没有一张待办表单。要补就先决定：给待办加编辑表单，还是在胶囊语法里加一个记号。显示侧不用动。
2. **`guarding` 把异常吞成"这一栏空着"。** 全库 47 处 `guarding(...)` 调用，失败只进日志浮窗。做过一版全局横幅的设计，否掉了：`novel`（14 处）和 `backup`（6 处）在正常使用里就会失败——SAF 权限被撤、文件被移走——天天弹一条"库有问题"是误报。剩下的设计是只做四个列表读端（`DayBoard` / `DeckShelf` / `NovelShelf` / `TimelineDao`）各自的故障标志，日常屏顶部一条话，别的一律不动。
3. **字体子集化。** 站酷快乐体在包里 1,513,964 字节（压缩后 1,109,424）。标题字面是固定几十个汉字，理论上能裁。搁置：改一句文案就得重裁一次，裁漏的字会当场掉回系统字体、标题跳字，这个维护账换 1.4 MB 不值。
4. **看板娘三张图 573,052 字节**，在 APK 里按原样存（不压缩）。真要省，动的是这三张而不是字体。没动。
5. **零测试。** 最该先测的是 `CaptureRouter`：纯函数、无 Android 依赖、三条语法规则，注释里自己写着"随时可脱机断言"。
6. **崩溃上报。** 要网络，和全离线口径直接冲突，永久搁置。替代品就是那个日志浮窗。
7. **宽屏两列的细调。** 上限与断点已落（720dp 断两列，内容上限 720/1080dp），但只在 1080×2400 的竖屏上看过。
8. **荣耀真机那条安装路。** 三道确认门（MIDI / 监控 ADB 安装 / 安全模式）把 `adb install` 挡住，装不回去，所以真机验收全线延后。

## 四、验收状态分三档（别混着说）

**只过了编译和 lint** —— 这一轮没有残留项。以前那种"只过了编译"的批次，后面都在模拟器上补看了。

**模拟器上眼睛看过**（API 36，1080×2400@420dpi）：
- 三屏逐屏点过，看板娘随态换姿势，标题贴纸描边，按钮 48dp 实测，空态小图。
- 分组头计数：`记账 · 1 笔`、`今天的轨迹 · 4 条` 这类串在屏上。
- 计时中点"选一份来恢复"：拒绝那句当场出现，文件选择器没弹出来。
- **备份整条回路走通**：导出 2,246 个字符 → 选回来 → "要装回来 9 行 / 现在库里的 9 行会一起没了" → 确认 → "装回来 9 行，分布在 6 张表"，屏上数据一条没少，`操作失败` 零次，崩溃零次。
- 启动图标在应用抽屉里正常渲染（去掉 v26 后缀之后重验过）。
- 封版这只 APK（SHA 前缀 `f5e709c9`）在模拟器上装成、跑起，三态各点一次：前台停在 `MainActivity`，`E AndroidRuntime` 零条，crash 缓冲区零条。装完又按设备的包抽回本地比过哈希，与构建产物一字不差——发出去的就是跑过的那只。

**只有真机能验** —— 荣耀上"点一下碎成一片雪花"那个渲染毛病。这一版所有点击都是自绘、`indication = null`，禁用清单（`ripple` / `AnimatedVisibility` / `AnimatedContent` / `graphicsLayer` / `.scale(` / 无 interactionSource 的 `clickable`）在源码里命中 0 次，但那条毛病模拟器不复现，最终还是要真机点头。

## 五、下次开工怎么起

```
build.cmd                        # 无参数＝assembleDebug + lintDebug
```

在脚本里跑要带 `FV_SCRIPTED=1`，否则末尾会 `pause` 等按键。跑 lint 之后必须现读报告，别信上一次：`app/build/reports/lint-results-debug.txt`。

量包体只认两个数：ZIP 条目的未压缩总量、压缩后总量。整个 APK 的字节数在两次构建之间能差 475 KB（同样的输入，zip 对齐补白不一样），拿它报"涨了多少"一定是假数。APK 也不保证字节可复现，认包只认 SHA-256。

装到模拟器：`deploy.cmd` 走的是真机；模拟器直接 `adb install -r -t` 那一只 debug APK。

### 这一版封了什么

发布目录在仓库外（不入库，具体位置见本机笔记）：debug APK、源码 zip、README、本文件，加一份 `SHA256SUMS.txt`。APK 不可字节复现，认包只认 SHA-256；文件字节数会因 zip 对齐补白来回摆几百 KB，别拿它当"变了多少"。

源码包落下去之后逐只解压回环 `cmp` 过，81 只全对得上当时的工作树。以后重打这个包，这条要重跑一遍——改文档也算改内容。重打之前顺手确认一下没有 `.kt` 带着 CRLF 落在工作树里：`.gitattributes` 写的是 `* text=auto eol=lf`，git 会在提交时把 CR 吃掉，所以 `git status` 报干净而 `git archive` 出来的字节比工作树少一截 CR，只有逐只 cmp 能发现。

### 已经公开的地方

仓库：<https://github.com/admeristive/FlowVault>。tag `v0.1.0` 打在发布那一刻（`git rev-parse v0.1.0` 现取），Release 上挂了 APK、源码 zip、`SHA256SUMS.txt` 三只。上传后按 GitHub 返回的每个资产的 digest 核过，三只与本地同串；那只 APK 就是模拟器上跑过的那一份。

发版之后仓库又添了几笔文档提交，所以 Release 里的源码 zip 停在 tag 那一刻，不等于当前 HEAD。以后要发新版本：`build.cmd` 出包 → 重打 zip → 更新 `SHA256SUMS.txt` → `gh release create`（这台机器上 `gh` 已按用户级装好并登录过）。

### 隐私闸怎么重跑（脚本是一次性的，按这几条重写）

- 针不硬编：账户名、邮箱那类身份字面在运行时从 `git config user.email`、远程 ref 现取，不写进脚本源码。
- 含反斜杠的针（盘符、目录）一律在脚本里现拼一个反斜杠字符，不要写两根反斜杠的字面——过一层 shell 会被折叠成永不命中的死针。
- 每轮必带一只正对照和一只负对照：正对照拿已知一定存在的串（来源要跟针同源，不能是脑补的），负对照拿一份干净样本要求放行。少了负对照，"零命中"可能是闸坏了；正对照复用同一个错表达式会连自证一起假。
- 扫的是 `HEAD` 整棵树，不是暂存表——提交之后 `--cached` 是空的，扫 0 只也照样会报"无"。必须把"扫了 N 只文件"打出来，N=0 是闸坏，不是干净。
- 提交之前查暂存表逐只过一遍，提交之后再对 `HEAD` 过一遍；文档、注释、提交说明都算内容，说明性文字里别落针的字面，否则扫描咬自己。

这一台留下的两件工具也在仓库外、和模拟器数据放一起：一只 `fvscan.py`（上面那套闸，脚本里没有身份字面，针从环境变量和 git 配置现取）、一只 `avd_boot.cmd`（起模拟器，`ANDROID_AVD_HOME` 写在脚本里，不落注册表）。这两个不清理，但如果哪天清了，按上面几条重写即可。

## 六、写代码前必看的坑

- **三条只有 lint 拦得住的 Compose 规则**：`modifier` 必须是第一个可选参数（`ModifierParameter`）；返回 Unit 的 `@Composable` 函数首字母大写（`ComposableNaming`）；判宽窄用 `BoxWithConstraints.maxWidth`，不要用 `screenWidthDp`（`ConfigurationScreenWidthHeight`）。
- `animateColorAsState` 在 Compose 1.12.1 已删。`clickable {}` 那个不带 interactionSource 的重载自带波纹，荣耀上会碎，必须写 `clickable(interactionSource = ..., indication = null)`。
- 后台编译跑着的时候别改源码。
- 中文别照抄英文的负字距；等宽只留给日志；读数档不动字体（那支快乐体没有等宽数字）。
- 全库不出现 SQLite 3.24+ 语法（`upsert` / `RETURNING`），minSdk 26。
- 备份恢复的回滚只认抛异常：androidx 的 `transaction { }` 在 API 30+ 是"块里返回 null 照样提交"，中途出问题一律 throw。

## 七、工具链路径怎么摆（入库前已改过一次）

`build.cmd`、`deploy.cmd`、`gradle/wrapper/gradle-wrapper.properties` 原来写死了本机的 JDK／Gradle 缓存／SDK／本地发行版地址，既暴露目录布局，换台机器也构建不动。现在：

- 两个脚本先 `call` 同目录的 `fv-local.cmd`（已在 `.gitignore` 里，不入库），再用环境变量或标准默认值兜底（`ANDROID_HOME` 或 `ANDROID_SDK_ROOT`、`%LOCALAPPDATA%\Android\Sdk`、`%USERPROFILE%\.gradle`、Android Studio 自带 JBR）。本机的三行放在 `fv-local.cmd` 里，内容就是三个 `set`。
- wrapper 的 `distributionUrl` 是官方 https 地址。本机不想重下 144 MB，可以把已解压的发行版放到 `$GRADLE_USER_HOME/wrapper/dists/gradle-9.8.1-bin/<hash>/` 下——`<hash>` 是**发行版 URL 的 MD5 转 36 进制**（`file:/` 地址和 `https://` 地址算出来是两个目录），外加一个空的 `<名字>.zip.ok` 标记文件。这件事已经做过，所以本机构建不会再下载。

`.gitignore` 里排掉了 `local.properties`、`adb-target.txt`（无线调试地址只落这一只文件，从不入库）、`fv-local.cmd`。

## 八、还能做什么（按性价比排）

1. 给 `CaptureRouter` 写第一批测试，纯函数，成本最低、收益最直。
2. 补 `planned_pomos` 的入口（先定形态：表单还是语法记号）。
3. 四个列表读端的故障标志（第三节第 2 条那版设计）。
4. 真机装一次，把"雪花"那条判掉——需要他先在手机上打开"USB 安装"那一门。
5. 读书侧的翻页手感（现在只有上一页/下一页两枚按钮，没有跟手拖动）。
