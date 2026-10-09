# 作息提醒 APK（xizuo-reminder）

一个**完全离线**的 Android 作息提醒应用：课表、校园跑与 Python 学习计划全部打包在应用内，
由手机系统闹钟接管提醒——不需要电脑、不需要联网、不需要服务器。

- **包名**：`com.tangxuanqi.xizuo`
- **版本**：2.0（versionCode 3）
- **最低版本**：Android 7.0（API 24）｜ 目标 API 34
- **安装包**：[`xizuo-reminder.apk`](xizuo-reminder.apk)（约 145 KB）

> 详细安装步骤、4 个系统开关的说明与提醒时刻表，见 [APK使用说明.md](APK使用说明.md)。

## 项目结构

| 路径 | 说明 |
|---|---|
| `AndroidManifest.xml` | 应用清单：权限、Activity、Receiver 声明 |
| `java/com/tangxuanqi/xizuo/` | Java 源码（闹钟调度、通知、开机自启、JSON 解析） |
| `assets/` | 前端页面与桥接脚本（`index.html`、`bridge.js`） |
| `res/` | 图标（多分辨率 mipmap）、`raw/alarm.wav` 铃声、字符串与样式 |
| `build/` | 构建工具链：SDK 获取、APK 编译打包脚本 |
| `xizuo-reminder.apk` | 已构建的安装包 |
| `APK使用说明.md` | 面向使用者的完整说明 |

## 构建方式

构建不使用 Android Studio / Gradle，而是用脚本调用 Android SDK 命令行工具直接编译打包：

```powershell
cd build
.\setup-sdk.ps1      # 首次：下载 Android SDK（约 516 MB，已 .gitignore 排除）
.\build-apk.ps1      # 编译：产出 ../xizuo-reminder.apk
```

其他脚本：`fetch-sdk.cjs`（SDK 下载逻辑）、`fetch-download.cjs`（下载工具）、
`make_icons.py`（生成多分辨率图标）、`build-apk.cjs`（Node 版构建入口）。

## 关于签名

APK 使用**自签名证书**（个人开发应用的常规做法），因此安装时会提示"未知来源"。
同一证书签名的后续版本可直接覆盖升级，无需卸载。

⚠️ **签名密钥 `build/xizuo.keystore` 已加入 `.gitignore`，不会提交。**
若遗失该文件，将无法再发布可覆盖升级的新版本——请单独备份到安全位置（不要放公开仓库）。

## 适配说明

首次安装后需在系统设置中手动开启 4 个开关（通知、精确闹钟、全屏通知、电池优化白名单）。
这不是应用权限弹窗，而是 Android 对精确闹钟与后台运行的系统级要求，任何应用都无法绕过。
应用**不会主动弹出任何权限申请框**。
