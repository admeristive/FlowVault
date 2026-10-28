# 流转秘境 FlowVault

一部手机上的自用记录本：番茄钟、本地小说、闪卡、账本、闪念速记，五件事装在同一个应用里。

全离线。不申请任何系统权限，不联网，没有账号，没有后端。数据只在他自己手机的应用目录里，卸载即干净。

## 下载

[Releases](https://github.com/admeristive/FlowVault/releases) 里有 `v0.1.0` 的 debug APK：

```
adb install -r -t FlowVault-0.1.0-debug.apk
```

debug 签名，只适合自己装。API 26 及以上。

## 三态

切到哪一态，整屏换一个主色，标题下面那位看板娘换一个姿势。

| 态 | 这一屏有什么 |
| --- | --- |
| 攻坚 | 专注计时（15 / 25 / 45 分三档）、这段时间要做的事、闪念速记 |
| 回血 | 这轮休息、本地小说（TXT 阅读器）、闪卡卡组、闪念速记 |
| 日常 | 今天的时间、记账、闪卡抽查、今天的轨迹、备份与恢复 |

攻坚那一趟走满，自动接一趟 5 分钟回血；回血超过 5 分钟没收，会被熔断记一笔，不算"走满"。

## 闪念速记的三条语法

只有一个输入框，靠开头那一个符号分家。认不出来就原样存成笔记，不猜：

- `-15 午饭`、`+500 工资` → 账本。负数是支出，正数是进账；金额后面第一个词拿去当分类名，没建过就当场建。
- `!14:00 复习` → 待办。读不出时间也算一条待办，只是不排时刻。
- 其余 → 笔记。

读书时丢的胶囊会带上出处（哪本书、第几字），点回去能落回原文位置。

## 数据与备份

- 库里 12 张表，一个 SQLite 文件。小说正文**不入库**，库里只存那个文件的来源地址和读到的位置。
- 导出＝一张 JSON 装下整个库，落到他亲手选的那一个文件；恢复＝整表替换，中间摆一次"要装回来多少行、会洗掉多少行"，要按两下。
- 恢复之后小说和卡组的来源文件多半接不上，界面上会逐条列出等他重新指认。
- 系统的自动备份和换机传输整个关掉：Android 12 以上看 `res/xml/data_extraction_rules.xml`，11 及以下看 `res/xml/backup_content.xml`，两份都是每个域一律排除。

## 出问题去哪看

长按标题"流转秘境"开合一个日志浮窗。所有读库写库的异常都留痕在那里，界面只拿到"这一栏空着"。

## 构建

```
build.cmd                      # 无参数＝assembleDebug + lintDebug
build.cmd :app:assembleDebug   # 只出包
```

`build.cmd` 和 `deploy.cmd` 不写死工具链位置：先读同目录的 `fv-local.cmd`（在 `.gitignore` 里，不入库），再用环境变量和标准默认值兜底。换台机器要么把 JDK／SDK 装在默认位置，要么自己建一份 `fv-local.cmd`，三行就够：

```
@echo off
set "JAVA_HOME=<JDK 17 的目录>"
set "GRADLE_USER_HOME=<Gradle 缓存目录>"
set "ANDROID_HOME=<Android SDK 目录>"
```

`gradle/wrapper/gradle-wrapper.properties` 指向官方发行版。本机想复用已下好的，改那一行的地址即可（只影响本机）。

工具链：JDK 17、AGP 9.4.1（内建 Kotlin 2.2.10）、Gradle 9.8.1、Compose BOM 2026.09.00。
`compileSdk 37` + `compileSdkMinor 2`（稳定渠道没有裸 android-37），`minSdk 26`，`targetSdk 36`。

装到手机上跑 `deploy.cmd`，它只报 `tool|apk|device|install|launch` 五个状态词，不打印任何地址。荣耀机型要在开发者选项里另外打开"USB 安装"那一门，否则 `install=FAIL`。

## 素材与授权

- 标题字体：站酷快乐体，单字重，只上标题档。按 SIL OFL 1.1 授权，授权文本在 `third_party/zcool_kuaile/OFL.txt`。
- 看板娘三张图（`res/drawable-nodpi/mascot_*.png`）：本项目生成的原创角色，不含任何外部作品形象。
- 空态那几枚小图、启动图标：手写矢量，没有图标库。
