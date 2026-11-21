# fterm

**用 Termux 的時候，是不是也遇過這些煩人的事？**

**中文(繁體)** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## 老實說，原本的 Termux 用起來有點難受

- 想改個字型、顏色或底部的一個附加鍵，都得去打開設定檔（`termux.properties`）。
- 想切換分頁，每次都得從左邊滑出側邊欄。
- 用 OpenCode 這類全螢幕介面（TUI）時，鍵盤偶爾會打不開。
- 中文和其他語言會錯位，想把截圖放進提示詞也很麻煩。
- 貼上長文字會卡頓，換行還會被拆成一個一個 Enter 送出去。
- 只想在任務結束後收一則通知，也得另外裝 `termux-api`。

---

## fterm 就是從這裡出發的

Shell 還是你熟悉的那套 Termux，**只把讓人難受的畫面和輸入徹底重做了一遍。**

- **一目了然的分頁列**：不用側滑，直接在上方開啟、切換分頁，還能改名字和顏色。
- **不用設定檔，隨手就能自訂**：配色主題、字型（中文和圖示已內建）、背景圖片、底部按鍵列，都能在應用程式內的設定畫面直接改。
- **預先準備好的設定檔**：把常用的 shell 或指令選成設定檔，打開就能用。
- **現代又順眼的介面**：乾淨的 Fluent 圖示、Windows Terminal 風格的分頁介面，還能按需開啟 CRT 效果，一點都不生硬。
- **安全貼上（括號貼上）**：貼上多行程式碼時不會多按出 Enter，超過 5KB 的大段文字會先問一次，避免手滑貼錯。
- **TUI 裡的複製、連結、圖片一次搞定**：
  - 複製時會自動去掉每行結尾的空白。
  - 點一下剪貼簿裡的圖片就存成 PNG，並自動填好路徑。
  - 按住 `Ctrl` 點畫面上的網址，直接打開瀏覽器。
- **不用裝額外 App 的通知 & 13 種語言**：沒有 `termux-api` 也能響鈴和跳通知，應用程式介面支援 13 種語言。
- **背景還原**：關掉應用程式再打開，之前執行中的畫面和滑鼠模式都還在。

---

## 安裝方法

要準備的東西很簡單：**安裝 APK**、**在 Termux 裡跑一行**，再**允許 3 項權限**。

1. 到 [Releases](https://github.com/CrashedSystem/fterm/releases/latest) 下載 **`fterm.apk`** 並安裝。
2. 打開 **Termux**，貼上下面這一行並執行。

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. 打開 **fterm**，把彈出的 3 項權限都允許。
   - **所有檔案存取權限**：應用程式需要它來和 Termux 守護程序交換權杖。
   - **照片/圖片權限**：用來貼上剪貼簿裡的截圖。
   - **通知權限**：用來在任務完成時響鈴提醒。

允許之後回到應用程式，就會自動連上。

---

## 常用快速鍵

- **新分頁 / 關閉分頁**：`+` 按鈕 / 長按分頁（鍵盤 `Ctrl+Shift+T` / `W`）
- **開啟設定**：長按 `+`（`Ctrl+,`）
- **切換分頁**：`Ctrl+Tab` / `Ctrl+1~9`
- **直接開啟網址**：`Ctrl` + 點擊畫面
- **複製 / 貼上**：`Ctrl+Shift+C` / `V`，或長按畫面 · 按右鍵
- **字型大小**：`Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## 了解更多

- 不連任何外部伺服器，只透過本機（`127.0.0.1`）通訊；發行檔案由 GitHub Actions 自動建置和驗證。
- 內部結構和開發記錄整理在 [`docs/ROADMAP.md`](../ROADMAP.md)。
- 授權：[MIT](../../LICENSE)（引擎：Termux [Apache-2.0](../../licenses/Apache-2.0.txt)，字型：[OFL-1.1](../../licenses/OFL-1.1.txt)）
