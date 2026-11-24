## 空叶 Android 项目在 Windows 上的构建与测试

- 在 E:\Vibe Coding\Projects 下打开对应工程并运行该工程的 gradlew.bat。构建产物保存在该工程的 build 目录中。
- 命令行构建从 E:\Vibe Coding\Android\Open Android Build Shell.cmd 启动，然后进入工程目录。使用其中配置的 Gradle 缓存和临时目录。
- 用 E:\Vibe Coding\Android\Start Android Studio.cmd 启动 Android Studio，用 E:\Vibe Coding\Android\Start Android Emulator.cmd 启动 Medium_Phone_API_37.0。AVD、Gradle 缓存和临时目录在 E:\Vibe Coding\Android 下，Android SDK 位于 D:\Program\Android\Sdk。
- 使用 adb devices 确认模拟器序列号。模拟器安装和测试命令使用 adb -s <emulator-序列号> 指定目标。
