# 空叶计算器

[下载APK](https://github.com/Mayleaves/KongyeCalculator/releases/latest) · [发行版列表](https://github.com/Mayleaves/KongyeCalculator/releases) · [更新日志](CHANGELOG.md)

极简科学计算 · 留白成算。

空叶计算器（KongyeCalculator）是一款离线优先的 Android 科学计算器应用，集所见即所得的 LaTeX 风格数学公式编辑、多函数同坐标系绘图、符号方程求解与多款日常实用计算工具于一体。

- 支持 Android 8.0 及以上系统。
- 应用包名：`com.kongye.calculator`，当前版本：**1.0.5**（build 39）。
- 所有计算功能完全离线运行，历史记录、变量、自定义函数与设置仅保存在设备本地；仅在用户主动打开「检查更新」或「更新历史」时访问 GitHub Releases。

## 核心功能

- **所见即所得公式编辑**：采用 Latin Modern Math 数学排版风格，支持分数、高次根式、上下标、任意底对数、微积分与求和/求积模板，以及可点击定位编辑的虚线参数框。
- **四区连续滚动键盘与导轨切换**：内置基础运算、科学函数、常用变量与经典公式模板四大分区，左侧几何等线导轨一键直达并随滚动高亮，支持自定义键位排列与按键列数。
- **紧凑编辑工具栏与会话暂存**：键盘上方提供清空、分组换行、光标左右移与退格操作；结果暂存区在无数据时五等分、有数据时六等分，长数字不挤动按钮。
- **多行方程组与函数绘图**：经典函数模板支持连续追加为多行大括号方程组，同一坐标系可同时绘制多条曲线，每条函数拥有独立配色与显隐开关，支持自由拖动、双指缩放与一键重置。
- **后台符号求解方程**：支持切换未知量、代入已知坐标值，提供精确分式/符号解与小数近似值；符号计算在后台 Worker 中异步执行，不阻塞界面交互。
- **数学排版历史记录**：历史记录完整保留分数与多行大括号结构，支持一键回填重新编辑与文本导出；每次重新启动默认以空白算式开始，历史与配置持久保留。
- **实用工具集**：侧边栏内置分数计算、单位换算、日期计算、世界时间、亲戚称呼计算、自定义函数管理与键位管理。
- **浅色／暗色主题与动态图标**：支持「水墨极简」浅色与「暗曜墨白」深色主题切换，桌面图标与原生启动页自动联动适配。
- **应用内检查更新与更新历史**：支持弹窗检测 GitHub Releases 新版本、显示更新内容与下载进度并拉起系统安装，更新历史支持关键词搜索与翻页浏览。

## 下载与安装

在 [最新发行版](https://github.com/Mayleaves/KongyeCalculator/releases/latest) 的附件中下载 APK 安装。

> 覆盖安装请使用 Releases 中同签名的正式安装包。

## 从源码构建

构建需要 JDK 17 或更新版本、Android SDK 37，并在本地 `local.properties` 中配置 SDK 路径（例如 `sdk.dir=D:/Program/Android/Sdk`）。

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

构建产物位于 `app/build/outputs/apk/`。详细的 Windows 构建环境配置、浏览器回归测试与 Android 真机测试说明请参阅 [docs/BUILD.md](docs/BUILD.md)。

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `app/src/main/assets/calculator.html` | 离线计算界面、公式编辑器、绘图引擎与工具页主入口 |
| `app/src/main/java/com/kongye/calculator/` | Android WebView 容器、原生开屏动画、动态桌面图标、原生计算与更新下载模块 |
| `app/src/main/res/` | 明暗桌面图标、主题与原生资源 |
| `tests/` | 计算逻辑单元测试与 Chrome 浏览器自动化回归脚本 |
| `app/src/androidTest/` | Android 真机 / 模拟器 WebView 交互、绘图、启动与更新回归测试 |
| `docs/BUILD.md` | 构建、签名与自动化验证详细文档 |

## 第三方组件与致谢

- [Nerdamer](https://github.com/jiggzson/nerdamer) 2.1.0（Apache-2.0）：符号代数与方程求解引擎，许可随离线脚本保留。
- [relationship.js](https://github.com/mumuy/relationship) 1.2.9（MIT）：中文亲戚称呼计算库，许可随离线脚本保留。
- Latin Modern Math（GUST Font License）：数学公式字体，许可文件见 [app/src/main/assets/font-licenses/](app/src/main/assets/font-licenses/)。
