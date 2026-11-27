## 空叶 Android 项目在 Windows 上的构建与测试

- 在 E:\Vibe Coding\Projects 下打开对应工程并运行该工程的 gradlew.bat。构建产物保存在该工程的 build 目录中。
- 命令行构建从 E:\Vibe Coding\Android\Open Android Build Shell.cmd 启动，然后进入工程目录。使用其中配置的 Gradle 缓存和临时目录。
- 用 E:\Vibe Coding\Android\Start Android Studio.cmd 启动 Android Studio。AVD、Gradle 缓存和临时目录在 E:\Vibe Coding\Android 下，Android SDK 位于 D:\Program\Android\Sdk。
- 使用 adb devices 确认模拟器序列号。模拟器安装和测试命令使用 adb -s <emulator-序列号> 指定目标。
- 只有确实需要模拟器做设备端测试或界面验证时才启动；改代码、打包 APK 和运行本地单元测试时不启动。先检查能否复用现有测试设备。需要新开模拟器时，运行 `E:\Vibe Coding\Android\Start Android Emulator.cmd`，默认以 `-no-window` 无窗口模式运行；需要用户查看或手动操作时，才加 `--visible`。
