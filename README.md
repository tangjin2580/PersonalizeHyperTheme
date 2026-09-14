# PersonalizeHyperTheme

适用于中国版 MIUI / HyperOS 的 Xposed 主题个性化模块。

[![CI](https://github.com/tangjin2580/PersonalizeHyperTheme/actions/workflows/main.yml/badge.svg)](https://github.com/tangjin2580/PersonalizeHyperTheme/actions/workflows/main.yml)
[![Stars](https://img.shields.io/github/stars/tangjin2580/PersonalizeHyperTheme?label=stars)](https://github.com/tangjin2580/PersonalizeHyperTheme)
[![Version](https://img.shields.io/badge/version-1.9.0-blue)](https://github.com/tangjin2580/PersonalizeHyperTheme)
[![License](https://img.shields.io/badge/license-GPL--3.0-green)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2010%2B%20%2F%20HyperOS-brightgreen)](https://github.com/tangjin2580/PersonalizeHyperTheme)

> 本项目由 [FuckMiuiThemeManager](https://github.com/tangjin2580/FuckMiuiThemeManager) 独立而来，
> 包名已改为 `com.tangjin.personalizehyper.theme`。**从旧版升级需先卸载旧版再安装**，
> 并在 LSPosed 中重新勾选作用域后重启。

---

## 功能

| 功能 | 说明 |
| --- | --- |
| 第三方主题 | 允许无条件应用未购买的第三方主题 |
| 付费字体 | 允许免费使用全部上架字体 |
| 去广告 | 去除主题壁纸应用内部广告（**不含开屏广告**，开屏广告来自另一个 MIUI 应用） |
| 主题壁纸 | 支持最近所有版本的主题壁纸 |
| 背屏主题 | 支持后置屏（背屏）主题的 apply |
| 内置日志 | 自带日志界面，可直接查看模块 hook 日志，无需连电脑 |

## 环境要求

- Android 10 及以上（`minSdk 29`），已在 **HyperOS / Android 14** 上验证
- 已 Root，并安装 **LSPosed**（或兼容的 Xposed 框架）
- 中国版 MIUI / HyperOS 的主题壁纸应用

## 安装

1. 下载 Release 里的 APK，或直接 `./gradlew assembleRelease` 自行编译
2. 安装 APK（**包名与旧版不同，签名也与官方版本不同，安装前需先卸载旧版**）
3. 在 LSPosed 中启用模块，并勾选作用域
4. **重启手机**

### 作用域（必须全部勾选）

| 宿主 | 包名 | 说明 |
| --- | --- | --- |
| 主题壁纸 | `com.android.thememanager` | 主题 / 字体 / 壁纸的购买校验 |
| 智能助理 | `com.miui.personalassistant` | 付费内容判断 |
| 系统框架 | `android` | DRM 合法性校验 |
| 桌面 | `com.miui.home` | 图标相关 |

> **系统框架勾选后必须重启手机才生效。**
> 在系统框架生效前，主题可能会不定时恢复默认——这是正常现象，重启后消失。

## 日志界面

模块带一个桌面入口（**HyperOS主题破解**），打开即可查看本模块的 hook 日志：

| 控件 | 作用 |
| --- | --- |
| 刷新日志 | 一次性拉取最近 300 行本模块日志 |
| 开始 / 停止 | 实时跟随日志输出 |
| 清空 | 清空当前显示 |
| 重启主题 / 重启桌面 | 快速重启对应宿主，便于验证 hook |
| V / D / I / W / E | 等级过滤（V 默认关闭） |

**日志来源**：Android 11 之后应用读不到其它进程的 logcat，所以界面读取的是 LSPosed 的落盘日志
`/data/adb/lspd/log/modules_*.log`，并只保留本模块（`[com.tangjin.personalizehyper.theme,XposedBridge`）的输出。
因此**首次打开需要授予 root 权限**，若 Magisk 弹出授权请点「允许」并记住。

需要连电脑抓日志时，模块也会**同时**写入 logcat（tag `FuckThemeManager`）：

```bash
adb logcat -s FuckThemeManager:V
```

## 实现原理

| 作用域 | hook 点 | 作用 |
| --- | --- | --- |
| `android` | `miui.drm.DrmManager.isLegal` | 在 `validateTheme` 期间临时放行 DRM 校验 |
| `com.android.thememanager` | `toResource` | 置为「已购买」；同时据此识别背屏主题 |
| `com.android.thememanager` | `DiscountPriceView` | 去角标 |
| `com.android.thememanager` | DexKit 反查 | 按字符串特征反查被混淆的 `DrmResult`、`LargeIcon` 方法 |
| `com.miui.personalassistant` | 11 处付费判断 | 返回值改为常量 |
| `com.miui.home` | 图标相关 | 大图标处理 |

**背屏（后置屏）主题**是其中最麻烦的一块：主题管理器的 `DrmResult` 被 hook 的方法其实是**无参方法**，
`param.args` 恒为空，无法据此判断当前是不是背屏主题。最终方案是：

1. 在 `toResource` 处识别背屏主题，记录时间戳
2. 开启一个 **60 秒窗口**
3. 窗口内的 `DrmResult` 调用，放行真实 DRM 校验

## 构建

```bash
./gradlew assembleRelease
```

环境要求：**JDK 17**、Android SDK（`compileSdk 34`）。
产物在 `app/build/outputs/apk/release/`，约 396 KB。

### 依赖

| 依赖 | 用途 |
| --- | --- |
| `de.robv.android.xposed:api:82` | Xposed API（compileOnly，运行时由框架提供） |
| `app/libs/miui-framework.jar` | MIUI 内部类（`miui.drm.*`）桩（compileOnly，运行时由宿主提供） |
| `com.github.kyuubiran:EzXHelper:2.2.1` | 方法 / 字段查找 |
| `org.luckypray:DexKit:1.1.8` | 按字符串特征反查被混淆的方法（自带 `libdexkit.so`） |

> **注意**：EzXHelper 2.2.1 是用 Kotlin metadata 2.1.0 编译的，**Kotlin 插件必须 ≥ 2.1**，
> 否则会报 `incompatible version of Kotlin`。
>
> 另外 EzXHelper 2.2.1 的 hook 扩展（`createHook`）已标为 internal，Kotlin 侧调不到，
> 因此本项目 **hook 一律使用 Xposed 原生 API**，EzXHelper 只用于方法 / 字段查找。

### 项目结构

```
app/src/main/java/com/tangjin/personalizehyper/theme/
├── XposedInit.kt          # 模块入口，四个作用域的全部 hook
├── LogActivity.kt         # 日志界面（开始/停止/刷新/清空/重启宿主/等级过滤）
├── LogReader.kt           # 读取并过滤模块日志
├── LogHelper.kt           # 日志双写（logcat + XposedBridge）
├── RearScreenState.kt     # 背屏状态与 60 秒窗口
└── RearScreenMamlSkip.kt  # 背屏 Maml 跳过
```

## 关于源码

该模块的历史版本曾长期停留在 2022 年的 1.2，1.3 ~ 1.9.0 的源码已丢失，只留有设备上安装的 APK。

当前代码是**从设备上 1.9.0（versionCode 19）的 APK 反编译还原**的：先用 jadx 出底稿，
再与 apktool 反编译出的 smali **逐处交叉校验**——jadx 还原 `switch-over-string` 时会丢分支
（顶部的 `Unreachable blocks removed` 提示），直接照抄会漏掉整段逻辑。

还原后做了两轮回环验证：把新编译出的 release **再反编译**，与原始 APK 做字符串集合 diff，
确认「原装有、新版缺」为空，即没有漏掉任何 hook 目标。

相对原 APK，代码只改了**一处行为**：日志来源从 `logcat` 换成 LSPosed 落盘日志
（原命令 `logcat -s FuckThemeManager:*` 里 `*` 不是合法优先级，在 Android 11+ 上恒返回空，
界面因此永远空白）。其余逻辑与设备上的 1.9.0 完全一致。

## 免责声明

本项目仅供学习研究 Android / Xposed 逆向技术使用，请在下载后 24 小时内删除。
请勿用于商业用途，使用本模块产生的任何后果由使用者自行承担。
请支持正版主题与字体。

## License

[GPL-3.0](LICENSE)
