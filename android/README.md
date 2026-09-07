# android — 安卓壳（WebView 封装）

把 `theme-preview` 阅读页 + phycat 主题打包成 APK 的安卓工程。

## APK 在哪

最新产物：
- `app/build/outputs/apk/debug/app-debug.apk`
- 项目根 `downloads/phycat-md-reader-v0.1-debug.apk`（ASCII 名，供站点直链下载，已签名可直接安装）

## 装到平板

1. 把 `MD阅读器-v0.1-debug.apk` 传到平板（微信文件传输助手 / QQ / 数据线拷到 Download 都行）。
2. 平板上点这个 apk 安装。若提示"未知来源"，允许本次安装即可。
3. 打开后：内置示例可直接看；侧栏「文件」页点 **选择文件夹…**（SAF）挑一个装 md 的目录，就会把里面所有 `.md` 收进来；也可用顶栏「打开 md 文件…」单个导入。
4. 也可以用其它 App 的 **打开方式 / 分享** 把 md 发给本应用，会直接显示该文档。

> 因主题字体（思源宋体/鸿蒙字体）全部内置，APK 约 30MB 属正常。

## 从源码重新构建

```bash
# 需 JDK17+ 与 Android SDK；工程里配好了 Gradle 8.11.1（见下方）
cd "D:/桌面/安卓md/android"
export JAVA_HOME=/d/software/java/jdk21
export ANDROID_HOME="C:/Users/12474/AppData/Local/Android/Sdk"
# 本机用独立解压的 gradle；标准做法是用 wrapper：
#   gradle wrapper --gradle-version 8.11.1  然后 ./gradlew assembleDebug
"C:/Users/12474/buildtools/gradle/gradle-8.11.1/bin/gradle" assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

用 adb（需连接设备并开 USB 调试）：
```bash
"$ANDROID_HOME/platform-tools/adb.exe" install -r "app/build/outputs/apk/debug/app-debug.apk"
"$ANDROID_HOME/platform-tools/adb.exe" shell am start -n com.example.mddroid/.MainActivity
```

## 结构

```
android/
├── settings.gradle / build.gradle / gradle.properties   # AGP 8.10.1 + Gradle 8.11.1，compileSdk 36
├── local.properties                                     # sdk.dir（本机路径，换机需改）
└── app/src/main/
    ├── AndroidManifest.xml          # INTERNET 权限；接收 VIEW/SEND 的 md
    ├── java/com/example/mddroid/
    │   └── MainActivity.java        # WebView 壳 + SAF 选文件/文件夹 + JS 桥
    └── assets/app/                  # 网页资源（由 tools/sync_assets.py 从原型同步）
        ├── index.html               # css 引用已改写为同目录（去掉 ../）
        ├── phycat-*.css             # 9 套配色
        ├── phycat/                  # 基础样式 + 字体
        └── vendor/markdown-it.min.js
```

## 原生 ⇄ 网页桥

网页侧按钮检测到 `window.AndroidBridge` 时走原生，否则回退到浏览器 input：

| 网页触发 | 原生动作 | 结果 |
|---|---|---|
| `AndroidBridge.pickFile()` | SAF 选单个文件 | 读内容 → `window.mdReader.addFiles([…])` |
| `AndroidBridge.pickFolder()` | SAF 选目录，递归收集 `.md` | 同上（批量） |

网页侧暴露 `window.mdReader.addFiles([{name,path,text},…])` 负责入库并打开。

## 已知限制（原型阶段）

- 文件内容是读进内存展示，**不写回磁盘**；重启 App 后文件库回到内置示例（需要重新选文件夹）。真正的"文库持久化 + 文件留在原处"留待下一步用 SAF `takePersistableUriPermission` 做。
- 代码块高亮按主题基础色展示，未做逐语言高亮。
- 脚注/数学公式/mermaid 图暂按文本或代码处理。
