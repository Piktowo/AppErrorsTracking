# AppErrorsTracking 项目架构文档

## 1. 项目概述

AppErrorsTracking 是一个 Xposed 模块，主要功能是增强应用的崩溃对话框，修复定制 ROM 删除对话框的问题，为 Android 开发者提供更好的错误跟踪体验。

- **主要功能**：
  - 增强应用崩溃对话框，提供更多信息和功能
  - 记录应用错误信息
  - 支持错误静音管理
  - 提供详细的错误日志和统计
  - 支持快速设置磁贴访问

## 2. 技术栈概览

| 类别 | 技术/库 | 版本 | 用途 |
|------|---------|------|------|
| 开发语言 | Kotlin | 2.2.21 | 主要开发语言 |
| 构建工具 | Gradle (Kotlin DSL) | - | 项目构建和依赖管理 |
| Xposed 框架 | YukiHookAPI | 1.3.1 | 现代化 Xposed 模块开发框架 |
| Xposed 框架 | Rovo89 Xposed API | 82 | 基础 Xposed API |
| UI 库 | Material Components | 1.13.0 | 现代 Android UI 组件 |
| UI 库 | AndroidX | 多个版本 | Android 支持库 |
| 网络库 | OkHttp | 5.3.2 | 网络请求 |
| 数据解析 | Gson | 2.13.2 | JSON 解析 |
| 分析工具 | Firebase | 33.11.0 | 崩溃分析与统计 |
| 工具库 | libsu | 5.2.2 | Root 权限操作 |
| 工具库 | KavaRef | 1.0.2 | 反射工具库 |
| 国际化 | FlexiLocale | 1.0.2 | 多语言支持 |

## 3. 目录结构

### 3.1 模块结构

```
AppErrorsTracking/
├── demo-app/            # 演示应用，用于测试模块功能
├── module-app/          # 主要的 Xposed 模块应用
├── gradle/              # Gradle 包装器和依赖版本配置
├── img-src/             # 图片资源
└── .github/             # GitHub 相关配置
```

### 3.2 module-app 核心目录

```
module-app/src/main/
├── java/com/fankes/apperrorstracking/
│   ├── application/     # 应用入口
│   ├── bean/            # 数据模型类
│   ├── const/           # 常量定义
│   ├── data/            # 数据管理类
│   ├── hook/            # Xposed 钩子实现
│   ├── locale/          # 国际化相关
│   ├── service/         # 服务类
│   ├── ui/              # UI 相关
│   │   ├── activity/    # 活动界面
│   │   └── widget/      # 自定义组件
│   ├── utils/           # 工具类
│   │   ├── factory/     # 工厂类
│   │   └── tool/        # 工具方法
│   └── wrapper/         # 包装类
└── res/                 # 资源文件
    ├── anim/            # 动画资源
    ├── drawable/        # 图片资源
    ├── layout/          # 布局文件
    ├── menu/            # 菜单资源
    ├── mipmap-*/        # 图标资源
    ├── values/          # 字符串、颜色等资源
    └── xml/             # XML 配置文件
```

### 3.3 关键目录说明

| 目录 | 功能说明 | 关键文件 |
|------|---------|----------|
| application/ | 应用初始化和生命周期管理 | AppErrorsApplication.kt |
| bean/ | 数据模型类，定义应用中使用的数据结构 | AppErrorsInfoBean.kt |
| data/ | 数据管理类，负责配置和错误记录的存储 | ConfigData.kt, AppErrorsRecordData.kt |
| hook/ | Xposed 钩子实现，核心功能所在 | HookEntry.kt, FrameworkHooker.kt |
| ui/activity/ | 应用界面，按功能划分 | MainActivity.kt, AppErrorsRecordActivity.kt |
| utils/factory/ | 工厂类，提供各种工具方法的封装 | DialogBuilderFactory.kt, BaseAdapterFactory.kt |
| utils/tool/ | 工具方法，提供特定功能 | GithubReleaseTool.kt, AppAnalyticsTool.kt |

## 4. 核心架构与流程

### 4.1 应用启动流程

1. **应用初始化**：`AppErrorsApplication` 启动，初始化国际化、配置数据和分析工具
2. **Xposed 模块初始化**：`HookEntry` 加载，配置调试日志并加载系统钩子
3. **系统钩子**：`FrameworkHooker` 实现对系统错误处理的钩子，增强崩溃对话框

### 4.2 核心数据流

1. **配置数据**：通过 `ConfigData` 管理，使用 YukiHookAPI 的 `prefs` 功能持久化
2. **错误记录**：通过 `AppErrorsRecordData` 管理，存储应用错误信息
3. **网络请求**：通过 `GithubReleaseTool` 发起网络请求，检查应用更新

### 4.3 界面架构

- **主界面**：`MainActivity`，作为应用入口和 Xposed 模块设置界面
- **错误相关界面**：
  - `AppErrorsRecordActivity`：错误记录列表
  - `AppErrorsMutedActivity`：已静音错误列表
  - `AppErrorsDisplayActivity`：错误显示界面（半透明）
  - `AppErrorsDetailActivity`：错误详情界面
- **配置界面**：`ConfigureActivity`，管理应用配置
- **调试界面**：`LoggerActivity`，查看日志信息

## 5. 开发规范

### 5.1 添加新页面

1. **创建 Activity 类**：在 `ui/activity/` 目录下创建新的 Activity 类，继承自 `BaseActivity`
2. **创建布局文件**：在 `res/layout/` 目录下创建对应的布局文件
3. **注册 Activity**：在 `AndroidManifest.xml` 中注册新的 Activity
4. **添加导航**：在适当的地方添加导航到新页面的代码

### 5.2 调用接口

1. **网络请求**：使用 `OkHttpClient` 发起网络请求，参考 `GithubReleaseTool` 的实现
2. **错误处理**：使用 `runCatching` 捕获异常，确保应用稳定性
3. **UI 更新**：在主线程更新 UI，使用 `runOnUiThread` 方法

### 5.3 添加新功能

1. **功能分析**：确定功能的具体需求和实现方式
2. **代码实现**：
   - 对于 UI 功能，创建新的 Activity 或 Fragment
   - 对于核心功能，在 `hook/` 目录下实现对应的钩子
   - 对于数据管理，在 `data/` 目录下添加相应的管理类
3. **配置管理**：如果需要用户配置，在 `ConfigData` 中添加新的配置项
4. **测试验证**：在 `demo-app` 中测试新功能

### 5.4 代码风格

- **命名规范**：
  - 类名使用 PascalCase
  - 方法和属性使用 camelCase
  - 常量使用 SNAKE_CASE
- **代码组织**：
  - 按功能模块划分目录
  - 使用工厂类和工具类封装通用功能
  - 采用 Kotlin 的扩展函数和属性简化代码
- **错误处理**：
  - 使用 `runCatching` 捕获异常
  - 避免使用空值，使用 `let`、`also` 等函数处理可能的空值

## 6. 配置与部署

### 6.1 构建配置

- **构建类型**：支持 debug 和 release 两种构建类型
- **签名配置**：使用 `universal` 签名配置，位于 `.secret` 目录
- **版本管理**：版本号和版本名称在 `build.gradle.kts` 中配置

### 6.2 部署流程

1. **构建 APK**：运行 `./gradlew :module-app:assembleRelease` 构建发布版本
2. **安装模块**：将生成的 APK 安装到设备上
3. **激活模块**：在 Xposed 框架中激活模块并重启设备

## 7. 常见问题与解决方案

### 7.1 网络请求失败

**问题**：`GithubReleaseTool` 中的网络请求失败
**解决方案**：检查网络连接，确保设备可以访问 GitHub API

### 7.2 模块未生效

**问题**：Xposed 模块未生效
**解决方案**：
- 确保 Xposed 框架已正确安装
- 确保模块已在 Xposed 框架中激活
- 重启设备以应用模块

### 7.3 配置未保存

**问题**：应用配置未保存
**解决方案**：检查 `ConfigData` 中的存储逻辑，确保使用了正确的存储方法

### 7.4 错误对话框未显示

**问题**：应用崩溃时错误对话框未显示
**解决方案**：
- 检查 `ConfigData` 中的相关配置项
- 确保模块已正确激活
- 检查 `FrameworkHooker` 中的钩子实现

## 8. 总结

AppErrorsTracking 是一个功能完善的 Xposed 模块，为 Android 开发者提供了更好的错误跟踪体验。项目采用现代化的 Kotlin 开发风格，结合 YukiHookAPI 框架简化 Xposed 模块开发，代码结构清晰，功能模块划分合理。

通过本文档，开发者可以快速了解项目的架构和开发规范，从而更加高效地进行开发和维护工作。