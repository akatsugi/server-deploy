# 服务器部署 IDEA 插件

一个基于 Gradle 的 IntelliJ IDEA 插件，用于在 IDEA 中维护服务器配置、目录映射，并在项目树右键完成文件上传和远程命令执行。

## 功能

- 服务器配置
  - 名称、主机、端口、用户名、密码、默认远程目录
  - 支持测试连接
- 目录映射配置
  - 维护本地目录到远程目录的映射关系
  - 支持在设置页新增/编辑
  - 支持在 IDEA 项目树右键“配置路径映射”
- 文件上传
  - 在项目树右键文件或目录时，如果命中映射，则显示“上传到服务器”
  - 支持选择目标服务器
  - 支持上传前删除远程已有目标
  - 上传进度显示当前文件已上传大小、文件总大小、文件序号
- 远程 Shell 命令
  - 在项目树右键文件或目录时，如果命中映射，则显示“执行远程 Shell 命令”
  - 执行时会先进入当前匹配到的远程目录，再执行命令
  - 支持在命令输入框中换行执行多条命令
  - 支持命令预览和执行结果查看
- JSON 导入导出
  - 可导出服务器配置、目录映射、默认 Shell 命令
  - 可从 JSON 导入到插件设置页

## 设置入口

IDEA 中打开：

`Settings / Preferences -> Tools -> 服务器部署`

设置页包含：

- 默认远程 Shell 命令
- 服务器列表
- 目录映射
- JSON 导入 / 导出

## 右键菜单

在项目树中右键文件或目录，会出现：

- `服务器部署 -> 配置路径映射`
- `服务器部署 -> 上传到服务器`
- `服务器部署 -> 执行远程 Shell 命令`

说明：

- `配置路径映射`：选中单个本地文件或目录时可用
- `上传到服务器`：需要命中目录映射后可用
- `执行远程 Shell 命令`：需要命中目录映射后可用

## 远程目录规则

- 服务器配置中的“默认远程目录”用于定义服务器基准目录
- 映射中的“远程目录”支持两种形式：
  - 相对路径：例如 `service-a`、`.`
  - 绝对路径：以 `/` 开头，例如 `/data/custom/service-a`

解析规则：

- 相对路径：`默认远程目录 + 映射远程目录`
- 绝对路径：直接使用映射远程目录

## 默认 Shell 命令占位符

设置页中的“默认远程 Shell 命令”支持以下占位符：

- `${remotePath}`：当前选中文件或目录映射后的远程目标路径
- `${remoteDirectory}`：当前执行命令时进入的远程目录
- `${serverName}`：服务器名称
- `${host}`：服务器主机
- `${username}`：用户名
- `${defaultDirectory}`：服务器默认远程目录

执行命令时，插件会自动包装成：

```sh
cd <当前匹配到的远程目录>
你的命令
```

因此可以直接在对话框中写多行命令，例如：

```sh
pwd
ls -l
java -version
```

## 开发环境

- JDK: `D:\Environment\Java`
- Gradle: `D:\Environment\gradle-8.14.3`
- IntelliJ Platform: `IC 2024.1.7`

关键配置见：

- [gradle.properties](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\gradle.properties)
- [build.gradle.kts](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\build.gradle.kts)

## 常用脚本

- [build-plugin.bat](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\build-plugin.bat)
  - 打包插件 ZIP
- [test-plugin.bat](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\test-plugin.bat)
  - 运行测试
- [run-ide.bat](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\run-ide.bat)
  - 启动沙箱 IDEA 调试插件

也可以直接执行 Gradle：

```bat
gradle test
gradle buildPlugin
gradle runIde
```

## 打包产物

插件打包后输出到：

`build/distributions/server-deploy-idea-plugin-1.0.0.zip`

当前路径：

[server-deploy-idea-plugin-1.0.0.zip](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\build\distributions\server-deploy-idea-plugin-1.0.0.zip)

## 安装方式

在 IntelliJ IDEA 中打开：

`Settings / Preferences -> Plugins -> 齿轮图标 -> Install Plugin from Disk...`

然后选择：

`build/distributions/server-deploy-idea-plugin-1.0.0.zip`

## 核心代码位置

- 动作
  - [ConfigurePathMappingAction.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\action\ConfigurePathMappingAction.java)
  - [UploadToServerAction.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\action\UploadToServerAction.java)
  - [ExecuteRemoteShellAction.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\action\ExecuteRemoteShellAction.java)
- 服务
  - [MappingResolver.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\service\MappingResolver.java)
  - [RemoteUploadService.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\service\RemoteUploadService.java)
  - [RemoteCommandService.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\service\RemoteCommandService.java)
  - [SettingsJsonService.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\service\SettingsJsonService.java)
- 设置页
  - [ServerDeployConfigurable.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\settings\ServerDeployConfigurable.java)
  - [ServerDeploySettingsPanel.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\settings\ServerDeploySettingsPanel.java)
  - [ServerDeploySettingsService.java](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\java\com\akatsugi\serverdeploy\idea\settings\ServerDeploySettingsService.java)
- 插件声明
  - [plugin.xml](D:\AKATSUGI\IDEA\server-deploy\idea-plugin\src\main\resources\META-INF\plugin.xml)
