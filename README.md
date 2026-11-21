# fterm

**Ever hit these annoyances while using Termux?**

**English** · [한국어](docs/readme/README.ko.md) · [日本語](docs/readme/README.ja.md) · [中文(简体)](docs/readme/README.zh-CN.md) · [中文(繁體)](docs/readme/README.zh-TW.md) · [Deutsch](docs/readme/README.de.md) · [Español](docs/readme/README.es.md) · [Français](docs/readme/README.fr.md) · [Italiano](docs/readme/README.it.md) · [Português](docs/readme/README.pt.md) · [Русский](docs/readme/README.ru.md) · [Srpski](docs/readme/README.sr.md) · [Українська](docs/readme/README.uk.md)

---

## Termux, honestly, is a bit uncomfortable

- Changing a font, a color, or one extra key means opening a config file (`termux.properties`).
- Switching tabs means swiping in from the left side every single time.
- With a full-screen TUI like OpenCode, the keyboard sometimes just refuses to open.
- Korean and other non-Latin text drifts out of alignment, and dropping a screenshot into a prompt is a chore.
- Pasting a lot of text stutters, and line breaks get chopped into separate Enters.
- Even one notification when a job finishes means installing `termux-api` separately.

---

## fterm started right there

The shell is still the familiar Termux one — **only the unbearable screen and input were rebuilt from scratch.**

- **Tabs you can see at a glance**: open, switch, rename and color tabs right from the top bar, with no side swiping.
- **Customize without config files**: color themes, fonts (Korean and icons bundled), background images and the bottom key bar all change from the in-app settings screen.
- **Ready-made profiles**: pick the shell or command you use most as a profile and open it straight away.
- **A modern interface that's easy on the eyes**: clean Fluent icons, a Windows Terminal–style tab UI, and an optional CRT effect — nothing stiff about it.
- **Safe pasting (bracketed paste)**: paste multi-line code without stray Enters firing, and anything over 5KB asks once first so a fat paste can't slip through.
- **TUI copy, links and images in one place**:
  - Trailing whitespace is trimmed automatically when you copy.
  - One tap on an image in the clipboard saves it as a PNG and types the path for you.
  - Hold `Ctrl` and tap a URL on screen to open it in the browser.
- **Notifications without extra apps & 13 languages**: bells and notifications ring without `termux-api`, and the app's UI supports 13 languages.
- **Restored in the background**: close the app and come back — the screen and mouse mode you left are still alive.

---

## Installation

What you need is simple: **install the APK**, **run one line in Termux**, and **allow 3 permissions**.

1. Grab **`fterm.apk`** from [Releases](https://github.com/CrashedSystem/fterm/releases/latest) and install it.
2. Open **Termux** and paste the line below to run it.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Launch **fterm** and allow the three permissions it asks for.
   - **All files access**: needed so the app can exchange the token with the Termux daemon.
   - **Photos/images**: needed to paste clipboard screenshots.
   - **Notifications**: needed to ring the bell when a job finishes.

Once you allow them and return to the app, it connects automatically.

---

## Shortcuts worth knowing

- **New tab / close tab**: `+` button / long-press a tab (keyboard `Ctrl+Shift+T` / `W`)
- **Open settings**: long-press `+` (`Ctrl+,`)
- **Switch tabs**: `Ctrl+Tab` / `Ctrl+1–9`
- **Open a URL instantly**: `Ctrl` + tap or click on screen
- **Copy / paste**: `Ctrl+Shift+C` / `V`, or long-press · right-click on screen
- **Font size**: `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Learn more

- It talks to nothing outside the device — only `127.0.0.1` — and release files are built and verified automatically by GitHub Actions.
- Internal structure and development notes are collected in [`docs/ROADMAP.md`](docs/ROADMAP.md).
- License: [MIT](LICENSE) (engine: Termux [Apache-2.0](licenses/Apache-2.0.txt), fonts: [OFL-1.1](licenses/OFL-1.1.txt))
