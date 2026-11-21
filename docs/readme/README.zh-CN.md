# fterm

**用 Termux 的时候，是不是也遇到过这些糟心事？**

**中文(简体)** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(繁體)](README.zh-TW.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## 老实说，原来的 Termux 用起来有点难受

- 想改个字体、颜色或者底部的一个附加键，都得去打开配置文件（`termux.properties`）。
- 想切换标签页，每次都得从左边滑出侧边栏。
- 用 OpenCode 这类全屏界面（TUI）时，键盘偶尔会打不开。
- 中文和其他语言会错位，想把截图放进提示词里也很麻烦。
- 粘贴长文本会卡顿，换行还会被拆成一个一个回车发出去。
- 只想在任务结束后收一条通知，也得另外装 `termux-api`。

---

## fterm 就是从这里出发的

Shell 还是你熟悉的那套 Termux，**只把让人难受的画面和输入彻底重做了一遍。**

- **一目了然的标签栏**：不用侧滑，直接在顶部打开、切换标签，还能改名字和颜色。
- **不用配置文件，随手就能定制**：配色主题、字体（中文和图标已内置）、背景图片、底部按键栏，都能在应用内的设置界面里直接改。
- **预先准备好的配置档**：把常用的 shell 或命令选成配置档，打开就能用。
- **现代又顺眼的界面**：干净的 Fluent 图标、Windows Terminal 风格的标签界面，还能按需开启 CRT 效果，一点都不生硬。
- **安全粘贴（括号粘贴）**：粘贴多行代码时不会多按出回车，超过 5KB 的大段文本会先问一次，避免手滑贴错。
- **TUI 里的复制、链接、图片一次搞定**：
  - 复制时会自动去掉每行末尾的空格。
  - 点一下剪贴板里的图片就存成 PNG，并自动填好路径。
  - 按住 `Ctrl` 点屏幕上的网址，直接打开浏览器。
- **不用装附加应用的通知 & 13 种语言**：没有 `termux-api` 也能响铃和弹通知，应用界面支持 13 种语言。
- **后台恢复**：关掉应用再打开，之前运行中的画面和鼠标模式都还在。

---

## 安装方法

需要准备的东西很简单：**安装 APK**、**在 Termux 里跑一行**，再**允许 3 项权限**。

1. 到 [Releases](https://github.com/CrashedSystem/fterm/releases/latest) 下载 **`fterm.apk`** 并安装。
2. 打开 **Termux**，粘贴下面这一行并执行。

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. 打开 **fterm**，把弹出的 3 项权限都允许。
   - **所有文件访问权限**：应用需要它来和 Termux 守护进程交换令牌。
   - **照片/图片权限**：用来粘贴剪贴板里的截图。
   - **通知权限**：用来在任务完成时响铃提醒。

允许之后回到应用，就会自动连接上。

---

## 常用快捷键

- **新建标签 / 关闭标签**：`+` 按钮 / 长按标签（键盘 `Ctrl+Shift+T` / `W`）
- **打开设置**：长按 `+`（`Ctrl+,`）
- **切换标签**：`Ctrl+Tab` / `Ctrl+1~9`
- **直接打开网址**：`Ctrl` + 点击屏幕
- **复制 / 粘贴**：`Ctrl+Shift+C` / `V`，或者长按屏幕 · 右键
- **字号**：`Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## 了解更多

- 不连接任何外部服务器，只通过本机（`127.0.0.1`）通信；发布文件由 GitHub Actions 自动构建和验证。
- 内部结构和开发记录整理在 [`docs/ROADMAP.md`](../ROADMAP.md)。
- 许可证：[MIT](../../LICENSE)（引擎：Termux [Apache-2.0](../../licenses/Apache-2.0.txt)，字体：[OFL-1.1](../../licenses/OFL-1.1.txt)）
