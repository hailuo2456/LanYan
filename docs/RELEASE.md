# 发布流程

每次发版按下面顺序执行。

## 1. 修改版本号

编辑 `app/build.gradle.kts`：

```kotlin
versionCode = 15          // 每次 +1
versionName = "1.4.1"     // 展示版本
```

同时更新：

- `README.md` 的 version 徽章与下载文案
- `outputs/澜言-使用说明.txt` 的「适用版本」与 APK 文件名
- `docs/` 内涉及版本号的说明（如有）

## 2. 本地验证与构建

```bat
cd /d <项目目录>
set "JAVA_HOME=<JDK 17 路径>"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

要求：单元测试、Lint、打包全部通过。

## 3. 生成交付物

```bat
copy /y app\build\outputs\apk\debug\app-debug.apk outputs\LanYan-<版本>-debug.apk
tar -a -c -f outputs\LanYan-<版本>-source.zip ^
  --exclude="app/build" --exclude=".gradle" --exclude=".kotlin" ^
  --exclude=".workbuddy" --exclude="local.properties" --exclude=".idea" ^
  .gitignore README.md LICENSE app build.gradle.kts docs gradle gradle.properties gradlew gradlew.bat settings.gradle.kts
```

## 4. 提交并打 tag

```bat
git add -A
git commit -m "release: v<版本>"
git tag -a v<版本> -m "LanYan v<版本>"
git push origin main
git push origin v<版本>
```

> 本机若遇到 `SSL certificate ... unable to get local issuer certificate`，先执行
> `git config http.sslBackend schannel` 再推送。

## 5. 创建 GitHub Release

1. 打开 https://github.com/hailuo2456/LanYan/releases/new
2. 选择刚推送的 tag
3. 标题：`澜言 v<版本>`
4. 说明：按「新增 / 修复 / 安装」三段写变更
5. 上传附件：
   - `LanYan-<版本>-debug.apk`（重命名为 `LanYan-<版本>.apk`）
   - `LanYan-<版本>-source.zip`
6. 发布后确认 README 的下载按钮（指向 `/releases/latest`）可正常跳转

## 6. 发版后自检

- [ ] Release 页面两个附件都能下载
- [ ] README 徽章版本号正确
- [ ] 使用说明书版本号与 APK 名称一致
- [ ] 从干净环境 clone 后能 `./gradlew assembleDebug`
