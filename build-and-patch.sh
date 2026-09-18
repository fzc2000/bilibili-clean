#!/bin/zsh
# 编译 BiliRoamingX fork → 给 8.92.1 打补丁 → （可选）装进模拟器
# 用法: build-and-patch.sh [--install]
set -euo pipefail

ROOT="${BILI_BUILD_ROOT:-$HOME/bili-build}"
SRC=$ROOT/BiliRoamingX
LOGS=$ROOT/logs
BASE_APK="${BASE_APK:-$HOME/Downloads/iBiliPlayer-8.92.1.apk}"   # 自行准备的官方 8.92.1 安装包
OUT_APK="${OUT_APK:-$HOME/Downloads/bilibili-8.92.1-biliroamingx.apk}"
CLI=$ROOT/revanced-cli.jar
KEYSTORE=$ROOT/keystore/bili-patched.keystore

export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
# BiliRoamingX 的依赖托管在 GitHub Packages，需要一个有 read:packages 的令牌
export GITHUB_ACTOR="${GITHUB_ACTOR:?请设置 GitHub 用户名}"
export GITHUB_TOKEN="${GITHUB_TOKEN:?请设置 read:packages 令牌}"

mkdir -p $LOGS
[ -f $SRC/local.properties ] || printf 'sdk.dir=%s\n' "$ANDROID_HOME" > $SRC/local.properties
if [ ! -f $CLI ]; then
  gh release download v4.6.0.2 -R zjns/revanced-cli -p revanced-cli.jar -D $ROOT
fi

echo "==> gradle dist"
(cd $SRC && ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx4g dist) > $LOGS/build.log 2>&1 || {
  grep -E '^e: |What went wrong' -A8 $LOGS/build.log | head -60
  exit 1
}
PATCHES=$(ls $SRC/build/BiliRoamingX-patches-*.jar | head -1)
INTEGRATIONS=$(ls $SRC/build/BiliRoamingX-integrations-*.apk | head -1)

echo "==> revanced-cli patch"
TMP=$(mktemp -d /private/tmp/claude-501/bili-patch.XXXX)
rm -f $OUT_APK
$JAVA_HOME/bin/java -Xmx6g -jar $CLI patch \
  -b $PATCHES -m $INTEGRATIONS \
  --signing-levels 1,2,3 --keystore $KEYSTORE \
  -t $TMP -o $OUT_APK $BASE_APK > $LOGS/patch.log 2>&1
rm -rf $TMP
echo "succeeded=$(grep -c ' succeeded' $LOGS/patch.log) failed=$(grep -c '^SEVERE: .* failed:' $LOGS/patch.log)"
grep '^SEVERE: .* failed:' -A2 $LOGS/patch.log | grep -v '^\s*at ' || true

# smali 汇编错误（如「Invalid register: v17」）只会打一行日志，出错的那条指令被悄悄丢掉，
# 补丁照样报 succeeded，产物装上去才在 ART 校验时 VerifyError。这里一律当构建失败处理。
if grep -qE '^\[[0-9]+,[0-9]+\] ' $LOGS/patch.log; then
  echo "!! smali 汇编错误，注入的指令被丢弃，产物不可用："
  grep -E -B1 '^\[[0-9]+,[0-9]+\] ' $LOGS/patch.log
  mv -f $OUT_APK "$OUT_APK.broken" 2>/dev/null || true
  exit 1
fi

if [[ "${1:-}" == "--install" ]]; then
  echo "==> adb install"
  $ANDROID_HOME/platform-tools/adb install -r $OUT_APK | tail -1
fi
