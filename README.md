# 我爱听书

Android 文本阅读、语音朗读和离线翻译应用。包名 `com.voicetxt.reader`，版本 1.0.1（3），arm64-v8a，最低 Android 8.0。

## 构建

安装 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。Android Studio 打开本目录并设置 SDK；命令行可在本地 local.properties 配置 sdk.dir。首次构建需联网获取 Gradle/Maven 依赖。

Windows：`gradlew.bat :app:assembleDebug`

Linux/macOS：`sh gradlew :app:assembleDebug`

测试 APK：`gradlew.bat :app:assembleDebugAndroidTest`。部分设备测试需要用户模型及对应设备环境。调试版使用标准调试签名。

## 正式发布

必须恢复现有正式签名，不得新建替代签名。私有文件不在仓库中。恢复配置到 `.tools/release-signing/keystore.properties`，或通过 `-PreleaseSigningPropertiesFile=本地配置路径` 指定。配置字段：storeFile、storeType、keyAlias、storePassword、keyPassword；storeFile 相对于项目根目录。

运行 `gradlew.bat :app:assembleRelease` 或 `gradlew.bat :app:bundleRelease`。缺少正式签名时发布构建拒绝执行。

## 源码与运行库

Java、资源、清单、原生桥接 cpp 和 androidTest 源码均保留。实际打包资源位于 system-assets。没有附带语音或翻译模型，使用时通过应用下载或导入。

app/libs/sherpa-classes.jar 和 jniLibs 下的 .so 是当前构建必需的运行库。Gradle 使用预编译库，不会自动重编 cpp。原生来源、哈希、版本和补丁见 third-party，重编需另行准备其源码及 NDK。

第三方许可证位于 third-party 和 app/src/main/system-assets/licenses。未擅自添加应用源码的开源许可证，由权利人决定公开许可。

整理副本排除 VITS 项目、缓存、SDK/JDK、构建产物、输出材料、签名文件和未打包的旧 assets 模型。原始文件未删除。原本依赖本机 .tools 的构建脚本替换为 Gradle Wrapper。
