# api102 分支：从 legacy Xposed 迁移到 libxposed 102

本分支把模块从 **legacy `de.robv.android.xposed`**（`IXposedHookLoadPackage` 体系）迁移到
**libxposed 102**（`io.github.libxposed.api`）。参考样板是
[REAREye](https://github.com/killerprojecte/REAREye) —— 同是小米背屏 Xposed 模块、同作用域、
且已完成 libxposed 102 迁移，是最贴合的参照。

---

## 一、从 REAREye 借鉴了什么

| 借鉴点 | REAREye 的做法 | 本分支是否采用 |
| --- | --- | --- |
| 入口生命周期转发 | `@Keep class HookEntry : XposedModule()`，override `onModuleLoaded` / `onPackageReady` / `onSystemServerStarting`，按包名分发 | ✅ 采用（`Api102Entry`） |
| 模块声明 | `META-INF/xposed/{module.prop, scope.list, java_init.list}` 替代 `assets/xposed_init` + manifest 的 `xposedmodule`/`xposedminversion` | ✅ 采用 |
| interceptor 式 hook | `XposedInterface.Hooker { chain -> chain.proceed() }` + `HookBuilder.install(...)` | ✅ 采用（封装在 `Xp.kt` DSL） |
| 日志门面 | 经入口回调用 `XposedInterface.log`，不再用 `XposedBridge.log` | ✅ 采用（`LogHelper` + `Api102Entry.initLogger`） |
| DexKit 延迟加载 | `System.loadLibrary("dexkit")` 必须等模块在宿主进程加载后再调用 | ✅ 采用（沿用原 `hookObfuscatedByDexKit`） |
| DexKit 框架无关 | `org.luckypray:dexkit` 在 libxposed 下仍可用 | ✅ 采用（保留 1.1.8，REAREye 用 2.2.0） |
| KavaRef 反射 DSL | `com.highcapable.kavaref` 替代 EzXHelper | ❌ **未采用** —— 改用自写 `Xp.kt`（零额外依赖，且 KavaRef 对 libxposed 并非必需） |
| `SystemProperties` 背屏识别 | `persist.sys.multi_display_type == 6` | ❌ **未采用** —— 保留原方案「调用栈含 `rearscreen` + 60s 时间窗口」（`RearScreenState`），二者判断背屏的时机/维度不同，原方案在主题应用链路已验证有效 |

> 结论：REAREye 在「架构骨架」层面（生命周期转发、META-INF 声明、interceptor hook、日志、DexKit 时机）
> 是最佳样板；在「具体工具/背屏判定」层面，本工程保留自己的实现，只借其成熟经验，不盲抄。

---

## 二、本分支改动清单

- `app/build.gradle.kts`：移除 `de.robv.android.xposed:api:82` + EzXHelper 2.2.1，改为
  `io.github.libxposed:api:102.0.0` + `io.github.libxposed:service:102.0.0`；保留 DexKit 1.1.8。
- `app/src/main/AndroidManifest.xml`：删除 legacy 的 `xposedscope`/`xposeddescription`/
  `xposedminversion`/`xposedminapi` 四个 meta-data。
- `app/src/main/resources/META-INF/xposed/module.prop` / `scope.list` / `java_init.list`：新增，
  承担模块声明（`minApiVersion=101`、`targetApiVersion=102`、`staticScope=true`、`autoHotReload=false`）。
- **新增** `Api102Entry.kt`：libxposed 102 入口，按包名分发四大作用域 hook。
- **新增** `Xp.kt`：自写反射查找（`loadClass`/`findMethod`/`findField` 含父类遍历）+ libxposed 102
  hook DSL 扩展（`hookReturnConstant` / `hookBeforeAll` / `hookAfterAll` / `hookBeforeModifyArgs` / `hookWithHooker`）。
- **改写** `RearScreenMamlSkip.kt`：从 `XC_MethodHook` 子类改为提供 `hooker(): XposedInterface.Hooker` 工厂。
- **改写** `LogHelper.kt`：去掉 `XposedBridge`，改为经 `initLogger` 注入的 `XposedInterface.log` 回调。
- **改写** `LogReader.kt` / `LogActivity.kt`：`MODULE_MARK` 去掉 `XposedBridge` 后缀，改为按包名
  `com.tangjin.personalizehyper.theme` 过滤（libxposed 不再打 `XposedBridge` 标记）。
- **删除** `XposedInit.kt`（被 `Api102Entry.kt` 取代）。
- `app/proguard-rules.pro`：`-keep` 改为 `Api102Entry`。
- `app/src/main/res/values/strings.xml`：修正遗留的错误 `module_name`（原为 `EzXHelper`）。

### legacy → libxposed 102 API 对照

| 能力 | legacy | libxposed 102 |
| --- | --- | --- |
| 模块入口 | `IXposedHookLoadPackage.handleLoadPackage` | `XposedModule` + `onPackageReady` / `onSystemServerStarting` |
| 模块声明 | `assets/xposed_init` + manifest meta-data | `META-INF/xposed/{module.prop, scope.list, java_init.list}` |
| hook 方法 | `XposedBridge.hookMethod(method, XC_MethodHook)` | `xposedInterface.hook(method).intercept(Hooker)` |
| before 设 result 跳过原方法 | `beforeHookedMethod { param.result = x }` | `intercept { x }`（不调 `proceed()`） |
| after 改结果 | `afterHookedMethod` | `intercept { val r = proceed(); …; r }` |
| 改参数 | `beforeHookedMethod { param.args[1] = 0 }` | `intercept { proceed(newArgs) }` |
| 日志 | `XposedBridge.log` | `XposedInterface.log(priority, tag, msg)` |
| 偏好 | `XSharedPreferences` | `getRemotePreferences`（service，本工程暂未用） |
| 反射查找 | EzXHelper / XposedHelpers | 自写 `Xp.kt` |

---

## 三、破解语义保持不变

四个作用域的破解逻辑与 legacy 完全一致，仅「表达形式」换成 libxposed interceptor：

- **android（system server）**：`ThemeReceiver.validateTheme` 校验期间临时放行 `DrmManager.isLegal`（校验结束立刻摘钩）。
- **com.miui.personalassistant**：付费 Maml 小组件判断全部改常量；`shouldCheckMamlBoughtState` /
  `isTargetPositionMamlPayAndDownloading` 区分背屏（调用栈含 `rearscreen` 放行真实逻辑，否则返回 false）。
- **com.android.thememanager**：`OnlineResourceDetail.toResource` 强置 `bought=true`（背屏除外）；
  `DiscountPriceView` 第二个 int 参数置 0 去角标；DexKit 定位 `DrmResult` / `LargeIcon`
  （背屏窗口内 `DrmResult` 放行真实校验）。
- **com.miui.home**：`MamlutilKt` / `MaMlPendingHostView` 相关判断改常量。

---

## 四、已知限制 / 待真机验证

0. **工具链必须升级（已在本分支完成）**：libxposed 102 的传递依赖
   `io.github.libxposed:interface:102.0.0` 声明了 `minCompileSdk 37`，原 `compileSdk 34` 会直接被
   `checkDebugAarMetadata` 判失败。因此本分支已同步升级：
   - `compileSdk` 34 → **37**，`buildToolsVersion` → **36.0.0**（aapt2 36 可编译到 android-37.jar）。
   - `AGP` 8.7.3 → **8.13.2**（8.7.3 的 compileSdk 上限是 35，不支持 37）。
   - Gradle wrapper 8.9 → **8.13**（AGP 8.13.2 要求的最低 Gradle）。
   - `targetSdk` 保持 **34** 不变，**不引入任何运行时行为变化**。
   - 需本机已安装 `android-37` SDK platform（本机 `~/Library/Android/sdk/platforms/android-37.0` 已存在）。

1. **`autoHotReload=false`**：libxposed 102 在热重载时**不会**重放 `onPackageReady` /
   `onSystemServerStarting`；若开启 `true` 而 `onHotReloaded` 里不重新安装 hook，应用更新热重载后
   hook 会整体失效（旧 hook 被默认 `onHotReloaded` 摘掉、新的又没装上），直到宿主进程重启才恢复。
   本项目沿用 legacy「重启宿主即生效」语义，故关闭。未来若要开启，需在 `onHotReloaded` 里通过
   `HookHandle.replaceHook` 或重新定位宿主类把 hook 再装一遍。
2. **日志格式**：假定 libxposed 的 `log()` 经 LSPosed 落盘到 `/data/adb/lspd/log/modules_*.log`，
   `MODULE_MARK` 已改为按包名过滤；需在真机确认 LSPosed 实际写入路径与行格式（若格式不同，
   `LogReader.trimLine` 可能需要微调）。
3. **编译**：沙箱若无 Maven 网络，`assembleDebug` 无法拉取 `io.github.libxposed:api:102.0.0` 等依赖；
   需在本地执行 `./gradlew assembleDebug` 验证。
4. **README 未同步**：根目录 `README.md` 仍含 legacy 描述（`XposedInit` / `EzXHelper` /
   `[…,XposedBridge]` 日志标记），待后续同步更新。
5. **背屏识别**沿用原方案，未移植 REAREye 的 `SystemProperties` 方案。

---

## 五、本地验证步骤

```bash
# 1. 编译
./gradlew assembleDebug

# 2. 安装后在 LSPosed 启用模块，作用域勾选：
#    com.android.thememanager / com.miui.home / com.miui.personalassistant / android(系统框架)
#    并重启对应进程（系统框架需重启手机）。

# 3. 打开模块 App → 日志，确认有 FTM: 开头输出；
#    应用一个主屏付费主题验证破解生效，再试背屏主题验证放行（不被强置 bought）。
```
