# bili-adfree-build

基于 [BiliRoamingX](https://github.com/BiliRoamingX/BiliRoamingX) 自行编译的哔哩哔哩安卓客户端补丁包，
目标版本 **8.92.1**（`tv.danmaku.bili`）。在官方去广告能力之外，补了一批 Bilibili-Evolved 风格的功能。

成品 APK 见本仓库 [Releases](../../releases)。

## 这个仓库有什么

| 文件 | 说明 |
|---|---|
| `local-changes.diff` | 对 BiliRoamingX 上游已有文件的全部改动（基于上游提交 `58aaf27`） |
| `new-files/` | 新增的补丁与集成代码，目录结构与 BiliRoamingX 仓库一致 |
| `build-and-patch.sh` | 编译 + 打补丁 + 签名的脚本（路径按自己环境改） |
| `VERIFY_REPORT.md` | 七项功能的真机验证结论与证据 |
|  | 两个失效项的定位过程与修复说明 |

**不包含**签名用的 keystore。升级安装必须用同一把签名，换签名要先卸载再装。

## 主要改动

去广告方面，把分散在各页的去广告开关收拢成独立的「去广告」分类，并新增开屏品牌广告的 HTTP 层拦截
（`Splash.kt`，8.92.1 的开屏数据走 Gson，原有的 fastjson 补丁看不到）。

功能方面：

- **直播首页停止自动播放**：除卡片列表外，新增顶部大横幅的钩子（`BaseVideoBannerHolder#realStartPlay`），
  非手动点播时不起播，手动点播不受影响
- **批量删除动态**：列表改走 App 自己的 gRPC `DynSpaceReq`，避开网页接口的 412 风控
- **记忆合集进度**：记住每个合集上次看到第几集，从合集第 1 集进入时自动跳回
- **按关注分组筛选动态**、**显示关注时间**
- **看完自动移出稍后再看**、**自动播放同 UP 主视频**、**跳过充电鸣谢**、**自动展开简介**
- **自定义字体**、**夜间模式定时切换**

## 编译

需要 JDK 21、Android SDK（含 NDK 与 cmake）、以及能读 BiliRoamingX GitHub Packages 的令牌。

```bash
git clone https://github.com/BiliRoamingX/BiliRoamingX.git
cd BiliRoamingX && git checkout 58aaf27
git apply /path/to/local-changes.diff
cp -R /path/to/new-files/. .
# 按自己的环境改 build-and-patch.sh 里的路径后执行
```

## 声明

仅供个人学习与自用。哔哩哔哩及其客户端的相关权利归其权利人所有，本仓库不分发官方 APK，
使用者需自行准备官方安装包，并自行承担使用风险。
