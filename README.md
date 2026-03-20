# server-deploy

基于 `JavaFX + SQLite + JSch` 的桌面部署工具，用于在本地工作区中浏览文件、上传文件到 Linux 服务器、按目录映射快速部署，并执行远程 Shell 命令。

## 功能概览

- 工作区选择与本地文件树展示
- 右键上传文件或目录到当前服务器远端目录
- 上传前可选是否删除远端同名文件或目录
- 支持目录映射
  - 每个服务器配置一个基础映射目录
  - 每条映射保存为“本地绝对目录 -> 服务器相对目录”
  - 右键可直接上传到映射目录
  - 右键可直接跳转到映射目录
  - 右键可复制映射目录
- 支持多服务器配置与切换
- 支持远程 Linux 命令执行
  - 优先使用 `bash -lc`
  - 不存在 `bash` 时回退到 `sh -lc`
- 上传支持进度日志
  - 展示总文件数
  - 展示已完成数量
  - 全部完成后输出明显完成日志
- 上传采用受控并发
- 文件树支持常用右键能力
  - 复制文件名
  - 复制文件路径
  - 资源管理器打开
  - 刷新当前节点
- 文本文件内嵌预览
  - 与执行日志共用 Tab 区域
  - 可同时打开多个预览 Tab
  - 预览 Tab 可关闭
  - 支持按后缀进行基础渲染

## 技术栈

- JDK 17
- Maven 3.8.1
- JavaFX 17
- SQLite
- JSch

## 项目结构

```text
server-deploy
├─ src/main/java/com/akatsugi/serverdeploy
│  ├─ AppStarter.java
│  ├─ Launcher.java
│  ├─ model
│  │  ├─ DirectoryMapping.java
│  │  ├─ ServerConfig.java
│  │  └─ WorkspaceConfig.java
│  ├─ service
│  │  ├─ DatabaseService.java
│  │  └─ RemoteOpsService.java
│  └─ ui
│     └─ FileTreeItem.java
├─ assets
│  └─ server-deploy-icon.ico
├─ app-data
├─ dist
├─ package-app.bat
├─ package-exe.bat
├─ package-installer-exe.bat
├─ run-app.bat
├─ start-app.bat
└─ pom.xml
```

## 开发环境

当前项目默认按以下环境配置运行：

- `JAVA_HOME=D:\Environment\Java`
- Maven: `D:\Environment\apache-maven-3.8.1`
- Maven 本地仓库: `D:\Environment\mavenRepo`

## 构建

```powershell
$env:JAVA_HOME='D:\Environment\Java'
$env:PATH='D:\Environment\Java\bin;D:\Environment\apache-maven-3.8.1\bin;' + $env:PATH
mvn "-Dfile.encoding=UTF-8" "-Dmaven.repo.local=D:\Environment\mavenRepo" compile
```

## 运行方式

### 方式一：Maven 直接运行

```powershell
.\run-app.bat
```

等价命令：

```powershell
$env:JAVA_HOME='D:\Environment\Java'
$env:PATH='D:\Environment\Java\bin;D:\Environment\apache-maven-3.8.1\bin;' + $env:PATH
mvn "-Dfile.encoding=UTF-8" "-Dmaven.repo.local=D:\Environment\mavenRepo" javafx:run
```

### 方式二：编译后按 classpath 启动

```powershell
.\start-app.bat
```

如果缺少编译产物，脚本会自动先调用 `package-app.bat`。

## EXE 打包

### 生成可运行的 EXE 应用目录

```powershell
.\package-exe.bat
```

产物位置：

```text
dist\server-deploy\server-deploy.exe
```

该目录下会包含：

- `server-deploy.exe`
- `runtime`
- `app`

### 生成安装版 EXE

```powershell
.\package-installer-exe.bat
```

说明：

- 该方式依赖 WiX Toolset 3.x
- 需要 `candle.exe` 和 `light.exe` 已加入 `PATH`
- 若当前机器未安装 WiX，则无法生成安装版 `setup.exe`

## 数据存储

程序运行后会在项目目录下生成 SQLite 数据库：

```text
app-data\server-deploy.db
```

数据库中主要保存：

- 工作区记录
- 服务器配置
- 目录映射配置

说明：

- 当前服务器密码为数据库持久化存储
- 如果用于生产环境，建议进一步扩展为加密存储或私钥登录

## 目录映射说明

目录映射采用以下规则：

- 每个服务器配置一个“基础映射目录”
- 每条映射配置一个“本地绝对目录”
- 每条映射配置一个“服务器相对目录”

最终远端路径为：

```text
基础映射目录 + 服务器相对目录 + 当前上传文件/目录的相对路径
```

示例：

- 基础映射目录：`/data/bigdata/data-control`
- 本地目录：`D:\work\demo\service-user\target`
- 服务器相对目录：`service-user-1.1.1-SNAPSHOT`

则上传目标类似：

```text
/data/bigdata/data-control/service-user-1.1.1-SNAPSHOT
```

## 界面说明

### 左侧区域

- 工作区文件树
- 支持右键上传、映射上传、复制文件名、复制路径、复制映射目录、跳转映射目录、资源管理器打开、刷新节点

### 右侧上方

- 远端目录
- 基础映射目录展示
- 远程命令输入与执行

### 右侧下方

- 执行日志 Tab
- 多文件预览 Tab

## 文件预览说明

- 仅做文本方式预览
- `exe`、图片、音视频文件不预览
- 其余文件尽量按文本方式展示
- 常见文本后缀会做基础渲染：
  - Markdown
  - JSON
  - XML/HTML
  - YAML
  - Properties
  - Shell
  - SQL
  - 常见代码文件
- 无对应渲染类型时按普通文本展示

## 远程执行说明

- 服务器连接能力由 JSch 实现
- 文件上传通过 SFTP 完成
- 远程命令执行通过 SSH Channel 完成
- 命令执行目录由界面中的远端目录控制

## 已验证脚本

- [run-app.bat](D:/AKATSUGI/IDEA/server-deploy/run-app.bat)
- [start-app.bat](D:/AKATSUGI/IDEA/server-deploy/start-app.bat)
- [package-app.bat](D:/AKATSUGI/IDEA/server-deploy/package-app.bat)
- [package-exe.bat](D:/AKATSUGI/IDEA/server-deploy/package-exe.bat)
- [package-installer-exe.bat](D:/AKATSUGI/IDEA/server-deploy/package-installer-exe.bat)

## 许可证

当前仓库未单独声明开源许可证。如需对外分发，建议补充许可证文件。
