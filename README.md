# 記帳本 Money Manager（Android）

為台灣日常生活設計的個人記帳 App。資料只存在手機本機（Room / SQLite），可匯出 JSON 備份。

## 功能

| 功能 | 說明 |
| --- | --- |
| 通知自動記帳 | 監聽你勾選的銀行、信用卡、LINE Pay／街口／全支付、簡訊等 App 推播，自動辨識金額、幣別、商家、卡號末四碼。可設定直接入帳或先放「待確認」。多個 App 同時通知同一筆消費會自動去重。 |
| 券商成交回報 | 通知中的「成交回報：買進 2330 台積電 1張 成交價 1,050」會自動建立股票交易並估算手續費／交易稅。 |
| 信用卡自動扣繳 | 信用卡帳戶設定結帳日、繳款日、扣款帳戶後，每到繳款日自動從銀行轉帳「結帳日欠款 − 已繳金額」。首頁顯示即將到期的帳單。 |
| 週期交易 | 訂閱、房租、保費、薪水、定期定額等，支援每天／每週／每月（月底自動調整）／每年與間隔；離線多天也會補帳。 |
| 電子發票載具 | 匯入財政部平台匯出的載具消費明細 CSV（支援 UTF-8 / Big5、民國年）。以發票號碼去重，並與通知已記錄的同金額消費合併，補上店名與品項。 |
| 股票投資 | 台股（證交所即時報價，上市／上櫃）、美股（Yahoo Finance）。平均成本法計算未實現／已實現損益、股利、今日漲跌；買賣金額可連動交割帳戶餘額。 |
| 即時匯率 | 自動更新匯率（open.er-api.com），多幣別帳戶與交易都換算成基準幣計算淨資產與報表。 |
| 報表 | 分類圓餅圖、近 6 個月收支趨勢、月均收支。 |
| 自動分類 | 內建台灣常見商家關鍵字；你確認過的商家分類會被記住，下次自動套用。 |
| 自動更新 | App 內「設定與更新」→ 檢查更新，直接下載並安裝 GitHub Release 上的新版 APK。 |

## 安裝與更新

1. 程式推到 `main` 分支時，GitHub Actions 會自動編譯並發布 Release（`v1.0.<build>`）。
   其他分支的建置結果在 Actions 頁面的 Artifacts 下載。
2. 手機下載 APK 安裝（需允許「安裝不明應用程式」）。
3. 之後到 App 的 **更多 → 設定與更新** 填入 GitHub Token（因為儲存庫是私人的），按「檢查更新」即可一鍵更新。
   Token 建立方式：GitHub → Settings → Developer settings → Fine-grained tokens → 只選此儲存庫，權限 Contents: Read-only。

### 固定簽章金鑰（建議設定一次）

Android 只允許「相同簽章」的 APK 覆蓋安裝。CI 會優先使用 Secrets 中的金鑰；未設定時改用快取的 debug 金鑰（快取 7 天未使用會被清除，之後的版本就無法直接覆蓋安裝）。

在電腦上產生金鑰並加到 GitHub → Settings → Secrets and variables → Actions：

```bash
keytool -genkeypair -keystore release.jks -alias moneymanager -keyalg RSA -keysize 2048 -validity 36500
base64 -w0 release.jks   # macOS 用 base64 -i release.jks
```

| Secret | 內容 |
| --- | --- |
| `KEYSTORE_BASE64` | 上面 base64 的輸出 |
| `KEYSTORE_PASSWORD` | 金鑰庫密碼 |
| `KEY_PASSWORD` | 金鑰密碼（與上面相同可省略） |

> 從 debug 金鑰換成正式金鑰時，需要先手動解除安裝舊版一次（請先匯出備份）。

## 第一次使用建議

1. **更多 → 帳戶與信用卡**：建立銀行、信用卡（填末四碼、結帳日、繳款日、扣款帳戶）、電子支付、證券交割帳戶。
2. **更多 → 通知自動記帳**：開啟通知存取權，勾選要監聽的 App。可在「測試辨識」貼上一則通知確認是否辨識正確。
3. **更多 → 週期交易**：加入訂閱與固定收支。
4. **投資**：輸入目前持股（買進紀錄）。

## 專案結構

```
core/   純 Kotlin 商業邏輯（通知解析、發票 CSV、週期／信用卡帳單週期、股票損益），含單元測試
app/    Android App（Jetpack Compose、Room、WorkManager、NotificationListenerService）
```

本機測試核心邏輯：`./gradlew :core:test`；編譯 App 需要 Android SDK（`./gradlew :app:assembleRelease`）。

## 已知限制

- 電子發票目前透過 CSV 匯入；財政部 API 需另行申請 AppID，尚未串接。
- 券商帳戶無公開 API 可直接連線，持股以「成交回報通知」或手動輸入建立。
- 通知格式因銀行而異，辨識不到的格式可在「通知自動記帳 → 最近處理紀錄」查看原文，回報後加入規則。
