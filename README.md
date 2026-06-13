# Moe Girl

Moe Girl 是一个极简 Android 单页文件浏览与视频播放客户端，使用光鸭云盘接口获取用户资料、目录内容和视频播放地址。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- AndroidX Navigation Compose 页面过渡
- DataStore Preferences 保存登录态、根目录和播放进度
- OkHttp + kotlinx.serialization 调用光鸭接口
- Media3 ExoPlayer 播放视频
- GitHub Actions 使用 Gradle 9.2.1 构建 debug/release APK

## 本地登录探测

登录探测脚本支持断点续接，状态和凭证会保存到 `.local/`，该目录已加入 `.gitignore`。

```bash
python3 tools/guangya_login.py start --phone "+86 13800138000"
python3 tools/guangya_login.py finish --code 123456
python3 tools/guangya_login.py probe --limit 20
```

如果短信初始化接口返回人机验证链接，脚本会保存当前状态并打印验证 URL。完成验证后可带 `--captcha-token` 重新执行 `start`。

## 签名与构建

项目包含固定 PKCS12 签名文件 `app/signing/moe-girl.p12`，debug 和 release 构建使用同一签名，便于后续覆盖安装。该签名仅适合当前简单项目和公开演示场景。

本地环境无需 Gradle 或 javac；仓库配置了 GitHub Actions 构建 workflow，push 后会自动产出 APK artifact。

