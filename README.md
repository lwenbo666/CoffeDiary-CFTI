# CFTI - 咖啡日记

一杯咖啡，一份心情。**CFTI** 是你的专属咖啡人格分析应用 — 拍照记录每日咖啡，解锁你的咖啡人格（Coffee Fingerprint Type Indicator）。

> 当前版本：**v0.1.3.2**（2026-05-24）

## 功能

| 功能 | 描述 |
|------|------|
| 📸 **拍照记录** | 调用摄像头拍摄咖啡照片，自动裁剪为贴纸并添加白色边框 |
| 🗓 **日历贴纸** | 咖啡记录以拍立得贴纸形式贴在日历格子上，支持月历展开/收起及左右滑动切换月份 |
| 📋 **咖啡菜单** | 咖啡配方系统，支持添加/编辑/删除配方（烘焙度、浓缩量、糖浆、制作步骤等） |
| 🧬 **CFTI 人格** | 在 7 个不同日子记录咖啡后，解锁四维咖啡人格分析（H/C · W/B · V/A · D/P） |
| 📊 **统计看板** | 温度/糖度分布环形图、咖啡偏好 TOP5、一周饮用节律柱状图 |
| 🎨 **人格雷达图** | 可视化展示四维人格倾向 |
| 🖼 **海报生成** | 一键生成 CFTI 人格海报并保存到手机相册 |
| 🗑 **批量管理** | 支持单条删除和按咖啡名称批量删除 |

### CFTI 四维人格维度

| 维度 | 含义 | 计算方式 |
|------|------|---------|
| **H/C** | 热饮(Hot) vs 冷饮(Cold) | 根据温度和糖度选择统计 |
| **W/B** | 嗜甜(sWeet) vs 嗜苦(Bitter) | 根据糖度偏好统计 |
| **V/A** | 探险(Variety) vs 专注(Anchored) | 根据咖啡种类多样性统计 |
| **D/P** | 仪式(Dedicated) vs 随性(Playful) | 根据记录频率和分布统计 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 平台 | Android 7.0+ (API 24) |
| 原生语言 | Kotlin |
| 前端 | HTML5 + Vanilla JS（WebView 内嵌） |
| CSS | Tailwind CSS + 自定义动画 |
| 图表 | Chart.js（环形图 / 柱状图 / 雷达图） |
| 数据库 | Room (Android Jetpack) |
| 构建 | Gradle 8.7 + Kotlin 2.0 |

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 35

### 构建

```bash
# 克隆项目后
cd CoffeeDiary

# 调试构建
./gradlew assembleDebug

# 发布构建（需签名密钥）
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/*/CFTI.apk`

## 项目结构

```
CoffeeDiary/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── main.html          # 核心前端（日历/拍照/统计/CFTI）
│   │   │   ├── main.js            # 核心前端 JS 逻辑
│   │   │   ├── menu.html          # 咖啡菜单配方页
│   │   │   └── menu.js            # 菜单页 JS 逻辑
│   │   ├── java/com/example/coffeediary/
│   │   │   ├── MainActivity.kt    # WebView + JS 桥接
│   │   │   ├── AppDatabase.kt     # Room 数据库单例
│   │   │   ├── CoffeeRecord.kt    # 数据实体
│   │   │   ├── CoffeeRecordDao.kt # DAO 数据访问
│   │   │   └── SplashOverlayView.kt # 启动动画
│   │   └── res/                   # 资源文件
│   └── build.gradle.kts
├── gradle/libs.versions.toml      # 版本目录
└── settings.gradle.kts
```

## 数据模型

每条咖啡记录包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 咖啡名称（如"生椰拿铁"） |
| date | String | 日期，格式 `YYYY-MM-DD` |
| photo | Base64 PNG | 400×400 正方形贴纸 |
| temp | String | 温度：hot / ice / warm |
| sugar | String | 糖度：none / half / full |

## 使用说明

1. **记录咖啡**：点击首页右下角 `+` 按钮，拍照后输入名称并选择温度/糖度
2. **查看日历**：今日贴纸点击可放大，月历支持展开/收起及左右滑动切换月份
3. **咖啡菜单**：底部导航切换到"菜单"页，查看/添加/编辑咖啡配方
4. **查看统计**：底部导航切换到"统计"页，支持按本周/本月/本年筛选
5. **解锁 CFTI**：在 7 个不同日子记录咖啡后，自动生成咖啡人格报告
6. **生成海报**：在统计页 CFTI 区域点击"生成人格海报"保存到相册
