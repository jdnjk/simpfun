# Simpfun APP

简幻欢平台的第三方 Android 客户端。使用简幻欢账号登录后，可直接在手机上管理名下服务器实例。

## 为什么要用呢

- **双栏文件管理**: 本地与远程同屏互传，传输更方便
- **SFTP连接**: 无视官网1GB限制，照样上传
- **本地 MCP 服务端**: 只要支持 **MCP** 协议的AI软件都可以用
- **购买更便捷**: 仅需一个页面就可以完成购买，免去二维码付款   

## 下载

加入 QQ 群 **1057873005** 获取最新 APK。

## 快速跑路

依赖：Android SDK、JDK 17。

```bash
git clone https://github.com/jdnjk/simpfun.git
cd simpfun
./gradlew assembleRelease
```

构建 release 需在 `sign.properties` 配置签名。

## 协议

本软件在 MIT 许可证下开源  
使用该应用应遵守[《软件许可及服务协议》](eula/README.md)。第三方 SDK 清单见[此处](eula/3rdparty.md)。  
