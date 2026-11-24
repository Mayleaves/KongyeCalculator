# 空叶计算器 KongyeCalculator

离线 Android 科学计算器，支持数学公式编辑、函数绘图、方程求解，以及常用计算工具。Android 8.0 及以上可用。

计算功能保持离线。“关于”右侧显示当前版本，点击检查更新在同一弹窗中显示检查结果、版本提示和下载进度。更新历史在用户打开时访问 GitHub，按列表显示更新内容，支持搜索和翻页。APK 下载完成后经系统确认安装。

[下载安装包](https://github.com/Mayleaves/KongyeCalculator/releases/latest)

## 当前版本

版本名称 **1.0.3**，版本代码 **37**。应用包名 `com.kongye.calculator`。

- LaTeX 默认风格的数学字体，支持分数、根式、上下标、对数和可编辑虚线框。
- 四个键盘分区，包含常用运算、科学函数、字母和经典函数模板。
- 多行公式可追加到同一大括号组，选择后可直接使用默认参数绘图、求解。
- 同一坐标系绘制多条函数，每个函数有独立的显示／隐藏按钮；支持拖动、双指缩放和重置。
- 方程求解支持切换未知量、代入另一坐标值、精确结果和小数近似。符号计算在后台 Worker 中完成。
- 历史记录保留数学排版与多行结构，支持导出文字、恢复编辑。
- 暂存结果工具栏在无数据时五等分、有数据时六等分，数字长短不会挤动按钮。
- 浅色／暗色主题，桌面图标与开屏图案随应用主题切换。
- 分数计算、单位转换、日期计算、世界时间、亲戚称呼和键位管理等工具。

## 构建

需要 JDK 17 或更新版本、Android SDK 37，以及 Gradle Wrapper 使用的依赖。SDK 路径在本地 `local.properties` 中配置，例如 `sdk.dir=D:/Program/Android/Sdk`。

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

APK 输出在 `app/build/outputs/apk/`。仓库不包含签名私钥；没有本地签名文件时 Release 构建输出未签名 APK。覆盖安装请使用 Releases 中同签名的安装包。

在本项目维护者的 Windows 环境中，先启动 `E:\Vibe Coding\Android\Open Android Build Shell.cmd`，再进入工程目录运行上述命令，以使用配置好的 Gradle 缓存和临时目录。

## 源码结构

- `app/src/main/assets/calculator.html`：离线界面、公式编辑器、绘图和工具页面的主入口。
- `app/src/main/java/com/kongye/calculator/`：Android WebView 容器、原生开屏、主题图标切换和计算代码。
- `app/src/main/res/`：明暗图标、主题和原生资源。
- `tests/`：计算逻辑与浏览器回归。
- `app/src/androidTest/`：在实际 WebView 中验证拖动、双指缩放、显隐、后台求解、工具栏与导航定位。

当前 HTML 源文件可直接在浏览器中预览，运行时不依赖网络。历史记录、草稿和设置保存在设备本地，暂存结果仅在当前会话保留。

## 验证

浏览器测试需要 Node.js 20 或以上版本及 Chrome。Windows 默认使用系统安装的 Chrome，也可通过 `CHROME_PATH` 指定可执行文件。

```powershell
./scripts/test-browser.ps1
```

真机测试使用相同签名的 Release 与测试 APK。先用 `adb devices` 确认设备，再明确指定序列号。

```bat
gradlew.bat :app:assembleRelease :app:assembleReleaseAndroidTest
adb -s SERIAL install -r app/build/outputs/apk/release/app-release.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb -s SERIAL shell am instrument -w com.kongye.calculator.test/com.kongye.calculator.GraphDeviceTest
```

仅验证侧栏箭头定位和变形动画时，在 instrumentation 命令中加入 `-e drawerOnly true`。

更新功能的独立真机测试：

```bat
gradlew.bat :app:assembleReleaseAndroidTest -PinstrumentationRunner=com.kongye.calculator.UpdateDeviceTest
adb -s SERIAL install -r app/build/outputs/apk/androidTest/release/app-release-androidTest.apk
adb -s SERIAL shell am instrument -w com.kongye.calculator.test/com.kongye.calculator.UpdateDeviceTest
```

该测试会请求真实的 GitHub 版本及更新历史，并用本地测试记录验证搜索、翻页与空结果。若设备安装的是带更新功能的旧版本，GitHub 已发布更高版本，可加 `-e download true` 验证真实下载、进度、文件校验及安装 URI。此模式不会自动确认安装。

已在索尼 A202SO 真机验证多函数绘图、独立显隐、拖动和双指缩放、代入值切换、五／六等分工具栏、明暗图标、历史大括号以及侧栏箭头位置。具体改动见 [更新日志](../CHANGELOG.md)。

## 第三方组件

- Nerdamer 2.1.0：Apache-2.0，许可随离线脚本保留。
- relationship.js 1.2.9：许可随离线脚本保留。
- Latin Modern Math 数学字体：GUST Font License，见 `app/src/main/assets/font-licenses/`。

品牌图形采用项目的「四象留白」方案。仓库尚未为项目原创代码指定额外的开源许可。
