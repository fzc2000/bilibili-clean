<div align="center">

# bili-adfree-build

_一个没有广告的 B 站，顺便塞了点 Evolved 的功能_

> 看视频就看视频，别给我推东西.

</div>

---

## 这是什么

- 拿 [BiliRoamingX](https://github.com/BiliRoamingX/BiliRoamingX) 自己编的 B 站安卓客户端补丁包
- 只对 **8.92.1** 这一个版本，别的版本别试，指纹对不上
- 官方去广告之外，把 [Bilibili-Evolved](https://github.com/the1812/Bilibili-Evolved) 上我常用的几个功能搬到了手机上

## 干了啥

- **去广告归拢**
  - 散落在各页的去广告开关收成一个「去广告」分类，一眼能找到
  - 开屏品牌广告在 HTTP 层拦掉，8.92.1 这块走 Gson，老补丁看不见
- **直播首页不自动播**
  - 卡片列表和顶部大横幅都拦了，自己点还是能播的
- **批量删除动态**
  - 网页接口会 412，改走 App 自己的 gRPC
- **记忆合集进度**
  - 看到第几集记着，下次从第 1 集点进来直接跳过去
- **杂七杂八**
  - 按关注分组筛动态、显示关注时间、看完自动移出稍后再看、自动播同 UP 的视频、跳过充电鸣谢、自动展开简介、自定义字体、夜间模式定时

七项功能在真机上过了一遍，结论在 [VERIFY_REPORT.md](./VERIFY_REPORT.md)。

## 怎么用

去 [Release](../../releases) 下 APK 装上就行。

> 签名和官方不一样，装之前先卸了官方版。以后升级只认这里发的包，签名一致才能覆盖装。

## 仓库里有啥

| 文件 | 干嘛的 |
|---|---|
| `local-changes.diff` | 对 BiliRoamingX 上游（`58aaf27`）已有文件的改动 |
| `new-files/` | 新加的文件，目录结构和上游仓库一样，整个盖上去就行 |
| `build-and-patch.sh` | 编译 → 打补丁 → 签名，路径自己改 |
| `VERIFY_REPORT.md` | 真机验证结论 |

keystore 不在这，别找了。

## 自己编

要 JDK 21、Android SDK（带 NDK 和 cmake），还有一个能读 BiliRoamingX GitHub Packages 的令牌。

```bash
git clone https://github.com/BiliRoamingX/BiliRoamingX.git
cd BiliRoamingX && git checkout 58aaf27
git apply ../local-changes.diff
cp -R ../new-files/. .
GITHUB_ACTOR=你的用户名 GITHUB_TOKEN=你的令牌 ../build-and-patch.sh
```

官方 8.92.1 的 APK 自己找，这里不放。

## 已知问题

- 记忆合集进度只认「整个合集的第 1 集」，分季合集里某一季的第 1 集不算
- 批量删除动态的「删」这一步没实测过，我号上没动态可删
- 两个测试项因为账号没关注只验到入口，见报告

## Thanks

- [BiliRoamingX](https://github.com/BiliRoamingX/BiliRoamingX) 底子全是它的
- [ReVanced](https://github.com/ReVanced) 打补丁的工具链
- [Bilibili-Evolved](https://github.com/the1812/Bilibili-Evolved) 功能都是照着它抄的

---

## 声明

自己用的，图个清净。B 站客户端的一切权利归 B 站，这里不分发官方 APK，也不对你用了之后发生的任何事负责。
拿去干别的、封号、丢数据，都是你自己的事。
