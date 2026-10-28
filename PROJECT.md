# 重序用文档

这份文件只管一件事：下次回来（可能就是几周后的我自己），怎么不重新摸一遍就能接上。

代码停在 `c609fb6`，共 9 笔提交，79 只入库文件（README 与本文件是第十笔，落下去之后这两个数各加一）。其中 50 只 .kt / 6,746 行，12 张表 / 10 个索引，`src/test` 与 `src/androidTest` 都不存在（零只测试）。

闸口读数：`BUILD SUCCESSFUL`，lint `0 errors, 2 warnings`。这两条是基线，改动之后必须回到这两个数。

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

**只有真机能验** —— 荣耀上"点一下碎成一片雪花"那个渲染毛病。这一版所有点击都是自绘、`indication = null`，禁用清单（`ripple` / `AnimatedVisibility` / `AnimatedContent` / `graphicsLayer` / `.scale(` / 无 interactionSource 的 `clickable`）在源码里命中 0 次，但那条毛病模拟器不复现，最终还是要真机点头。

## 五、下次开工怎么起

```
build.cmd                        # 无参数＝assembleDebug + lintDebug
```

在脚本里跑要带 `FV_SCRIPTED=1`，否则末尾会 `pause` 等按键。跑 lint 之后必须现读报告，别信上一次：`app/build/reports/lint-results-debug.txt`。

量包体只认两个数：ZIP 条目的未压缩总量、压缩后总量。整个 APK 的字节数在两次构建之间能差 475 KB（同样的输入，zip 对齐补白不一样），拿它报"涨了多少"一定是假数。APK 也不保证字节可复现，认包只认 SHA-256。

装到模拟器：`deploy.cmd` 走的是真机；模拟器直接 `adb install -r -t` 那一只 debug APK。

## 六、写代码前必看的坑

- **三条只有 lint 拦得住的 Compose 规则**：`modifier` 必须是第一个可选参数（`ModifierParameter`）；返回 Unit 的 `@Composable` 函数首字母大写（`ComposableNaming`）；判宽窄用 `BoxWithConstraints.maxWidth`，不要用 `screenWidthDp`（`ConfigurationScreenWidthHeight`）。
- `animateColorAsState` 在 Compose 1.12.1 已删。`clickable {}` 那个不带 interactionSource 的重载自带波纹，荣耀上会碎，必须写 `clickable(interactionSource = ..., indication = null)`。
- 后台编译跑着的时候别改源码。
- 中文别照抄英文的负字距；等宽只留给日志；读数档不动字体（那支快乐体没有等宽数字）。
- 全库不出现 SQLite 3.24+ 语法（`upsert` / `RETURNING`），minSdk 26。
- 备份恢复的回滚只认抛异常：androidx 的 `transaction { }` 在 API 30+ 是"块里返回 null 照样提交"，中途出问题一律 throw。

## 七、公开之前要动的三处

`build.cmd` 顶部三行、`deploy.cmd` 两行、`gradle/wrapper/gradle-wrapper.properties` 两行里写的是本机工具链位置（JDK、Gradle 缓存、SDK、以及一个指向本地 Gradle 发行版的 `file:` 地址）。它们不是身份凭据，但会暴露这台机器的目录布局，而且换一台机器就构建不动。发出去之前换成环境变量或者删掉，仓库里留一份能用的默认值。

`.gitignore` 里已经排掉了 `local.properties`、`adb-target.txt`（无线调试地址只落这一只文件，从不入库）。

## 八、还能做什么（按性价比排）

1. 给 `CaptureRouter` 写第一批测试，纯函数，成本最低、收益最直。
2. 补 `planned_pomos` 的入口（先定形态：表单还是语法记号）。
3. 四个列表读端的故障标志（第三节第 2 条那版设计）。
4. 真机装一次，把"雪花"那条判掉——需要他先在手机上打开"USB 安装"那一门。
5. 读书侧的翻页手感（现在只有上一页/下一页两枚按钮，没有跟手拖动）。
