#!/usr/bin/env python3
"""
自動化介面檢查：在模擬器上操作帳理（測試版），逐頁截圖，並把每個畫面上的文字存成 .txt。
由 .github/workflows/ui-check.yml 執行；截圖推到 ui-shots 分支供檢查。
"""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from datetime import datetime

PKG = "com.enwei.moneymanager"
OUT = os.environ.get("SHOTS", "shots")
os.makedirs(OUT, exist_ok=True)
n = 0
log = open(os.path.join(OUT, "log.txt"), "w", encoding="utf-8")


def sh(cmd, check=False):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"{cmd}\n{r.stderr}")
    return r.stdout


def adb(cmd):
    return sh(f"adb shell {cmd}")


def note(msg):
    print(msg, flush=True)
    log.write(msg + "\n")
    log.flush()


DIALOG_BUTTONS = ("Wait", "等待", "Close app", "關閉應用程式", "OK", "確定")


def nodes(retry=3):
    """目前畫面上所有有文字或描述的元件：(文字, 描述, 中心x, 中心y)。系統的「沒有回應」對話框會先關掉。"""
    out = _dump()
    for _ in range(retry):
        texts = " ".join(t for t, _, _, _ in out)
        # 測試版會偵測到正式版的更新：按「稍後」關掉
        if "有新版本" in texts:
            for t, d, x, y in out:
                if t == "稍後":
                    adb(f"input tap {x} {y}")
                    break
            time.sleep(1.5)
            out = _dump()
            continue
        if "isn't responding" not in texts and "沒有回應" not in texts:
            break
        who = next((t for t, _, _, _ in out if "responding" in t or "沒有回應" in t), "")
        note(f"[dialog] {who}（按等待）")
        for t, d, x, y in out:
            if t in DIALOG_BUTTONS[:2]:
                adb(f"input tap {x} {y}")
                break
        else:
            adb("input keyevent 4")
        time.sleep(3)
        out = _dump()
    return out


def _dump():
    adb("uiautomator dump /sdcard/ui.xml >/dev/null")
    raw = sh("adb exec-out cat /sdcard/ui.xml")
    out = []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return out
    for e in root.iter("node"):
        t, d, b = e.get("text", ""), e.get("content-desc", ""), e.get("bounds", "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if not m or not (t or d):
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        out.append((t, d, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def shot(name):
    global n
    n += 1
    base = os.path.join(OUT, f"{n:02d}_{name}")
    time.sleep(1.2)
    sh(f"adb exec-out screencap -p > {base}.png")
    texts = [t or d for t, d, _, _ in nodes()]
    with open(base + ".txt", "w", encoding="utf-8") as f:
        f.write("\n".join(texts))
    note(f"[shot] {n:02d}_{name}")


def tap(label, exact=False, nth=0, wait=1.5):
    for t, d, x, y in nodes():
        for v in (t, d):
            if v and (v == label if exact else label in v):
                if nth == 0:
                    adb(f"input tap {x} {y}")
                    time.sleep(wait)
                    return True
                nth -= 1
    note(f"[miss] 找不到「{label}」")
    return False


def back(wait=1.2):
    adb("input keyevent 4")
    time.sleep(wait)


def scroll(times=1):
    for _ in range(times):
        adb("input swipe 540 1700 540 600 400")
        time.sleep(0.8)


def geo(lat, lon):
    sh(f"adb emu geo fix {lon} {lat}")
    time.sleep(1)


def launch():
    adb(f"am force-stop {PKG}")
    adb(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1 >/dev/null")
    time.sleep(6)
    nodes()


def notify(pkg, title, text):
    adb(f"am broadcast -n {PKG}/.DebugHooks -a {PKG}.NOTIFY --es pkg {pkg} --es title '\"{title}\"' --es text '\"{text}\"' >/dev/null")
    time.sleep(3)


def settle():
    """等開機完成、系統安靜下來（模擬器剛開機時桌面常常沒有回應）"""
    for _ in range(60):
        if sh("adb shell getprop sys.boot_completed").strip() == "1":
            break
        time.sleep(2)
    adb("input keyevent 82")  # 解鎖
    time.sleep(20)
    nodes()


def main():
    settle()
    # 權限與假資料
    for p in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS"]:
        adb(f"pm grant {PKG} android.permission.{p}")
    geo(25.0133, 121.5406)
    launch()
    adb(f"am broadcast -n {PKG}/.DebugHooks -a {PKG}.DEMO >/dev/null")
    time.sleep(6)
    launch()

    # ① 各分頁
    shot("txns")
    tap("日曆", exact=True); shot("txns_calendar")
    tap("分析", exact=True); shot("txns_analysis")
    tap("每日", exact=True)
    tap("統計", exact=True); shot("stats")
    tap("資產", exact=True); shot("assets")
    tap("股票", exact=True); time.sleep(4); shot("stocks")
    tap("觀察", nth=0); shot("stocks_watch")
    tap("助理", exact=True); time.sleep(2); shot("assistant")
    tap("紀律", exact=True); shot("assistant_discipline")
    tap("工具", exact=True); shot("tools")
    tap("設定", exact=True); shot("settings"); scroll(); shot("settings_2"); back()

    # ② 記帳：人在台科大第二餐廳，打開新增
    geo(25.01290, 121.54150)
    tap("交易", exact=True)
    tap("記一筆"); time.sleep(9); shot("editor_at_cafeteria2")
    for k in ["8", "5"]:
        tap(k, exact=True, wait=0.4)
    shot("editor_amount_85")
    back(); back()
    tap("捨棄", wait=1); tap("不儲存", wait=1)
    launch()

    # ③ 模擬通知：LINE Pay 先到，再來信用卡刷卡（末四碼 5908）同一筆
    now = datetime.now()
    md, hm = f"{now.month}/{now.day}", now.strftime("%H:%M")
    notify("jp.naver.line.android", "LINE錢包", "LINE Pay 付款 NT$ 60 付款完成。")
    notify("com.test.card", "測試銀行", f"貴賓您好，末四碼5908感謝{md} {hm}刷卡台幣60元，商店名稱:LINE Pay")
    # 信用卡刷「京都」85 元（你的習慣：京都日式料理寫在說明，店家是台科大第二餐廳）
    notify("com.test.card", "測試銀行", f"貴賓您好，末四碼5908感謝{md} {hm}刷卡台幣85元，商店名稱:京都")
    launch()
    shot("txns_after_notifications")
    tap("LINE Pay"); time.sleep(2); shot("txn_linepay_60"); back()
    tap("85"); time.sleep(2); shot("txn_kyoto_85"); back()
    tap("工具", exact=True); tap("通知記帳"); time.sleep(2); shot("notif_log"); back()

    # ④ 個股頁
    tap("資產", exact=True); tap("股票", exact=True); time.sleep(3)
    tap("2330"); time.sleep(12); shot("stock_2330")
    scroll(2); shot("stock_2330_mid")
    scroll(3); shot("stock_2330_projection")
    back()

    # ⑤ 深色模式與大字體
    adb("cmd uimode night yes"); time.sleep(2)
    launch(); shot("dark_txns")
    tap("記一筆"); time.sleep(5); shot("dark_editor"); back(); tap("捨棄", wait=1)
    adb("cmd uimode night no")
    adb("settings put system font_scale 1.3"); time.sleep(2)
    launch(); shot("bigfont_txns")
    tap("資產", exact=True); shot("bigfont_assets")
    tap("記一筆", wait=1); tap("交易", exact=True); tap("記一筆"); time.sleep(5); shot("bigfont_editor")
    adb("settings put system font_scale 1.0")

    # 沒有回應（ANR）的原因：記下是哪個程式、卡在哪裡
    anr = sh("adb logcat -d -b main,system,crash | grep -A12 'ANR in' | head -120")
    if anr.strip():
        note("[anr]\n" + anr)
    sh(f"adb shell dumpsys activity processes > /dev/null")
    traces = sh("adb shell 'ls /data/anr/ 2>/dev/null'")
    if traces.strip():
        note("[anr traces] " + traces.replace("\n", " "))
    # 閃退紀錄
    crash = sh(f"adb shell run-as {PKG} cat files/last_crash.txt 2>/dev/null")
    if crash.strip():
        note("[crash]\n" + crash)
    note("done")


if __name__ == "__main__":
    main()
