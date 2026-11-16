# SyncedLyricsSDK · Android

基于 Android 原生 View 的动态歌词 SDK，包含八种歌词样式、封面背景和无歌词封面。播放器、歌曲信息、网络请求和播放控制由宿主 App 管理。

完整接入步骤、公开接口、播放器同步和已知布局限制，请参阅 [Android 接入说明](docs/Android接入说明.md)。

演示项目和单元测试：

```bash
./gradlew testDebugUnitTest :demo:assembleDebug
```

当前项目提供 Android 版本，minSdk 24；正式 AAR 需要在构建环境配置好 JDK 17、Android SDK 及 Gradle 依赖后，通过 `./gradlew assembleRelease` 生成。
