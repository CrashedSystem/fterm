# fterm

**Kennst du diese Nervigkeiten bei Termux?**

**Deutsch** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [中文(繁體)](README.zh-TW.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## Termux ist ehrlich gesagt etwas unbequem

- Für eine andere Schrift, eine Farbe oder eine einzige Zusatztaste unten muss man erst eine Konfigurationsdatei (`termux.properties`) öffnen.
- Für den Tab-Wechsel muss man jedes Mal von links die Seitenleiste hereinswipen.
- Bei Vollbild-TUIs wie OpenCode öffnet sich die Tastatur manchmal einfach nicht.
- Koreanisch und andere Sprachen verrutschen, und ein Screenshot im Prompt ist auch umständlich.
- Beim Einfügen von langem Text ruckelt es, und Zeilenumbrüche werden in einzelne Enters zerlegt.
- Für eine einzige Benachrichtigung am Ende eines Jobs muss man `termux-api` extra installieren.

---

## Genau da hat fterm angefangen

Die Shell bleibt die vertraute von Termux — **nur Bildschirm und Eingabe, die genervt haben, wurden komplett neu gebaut.**

- **Tabs auf einen Blick**: Tabs oben öffnen, wechseln, umbenennen und einfärben — ganz ohne Wischen.
- **Anpassen ohne Konfigurationsdateien**: Farbthemen, Schriften (Koreanisch und Icons eingebaut), Hintergrundbilder und die untere Tastenleiste direkt im Einstellungsbildschirm der App.
- **Fertige Profile**: die Shell oder das Kommando, das du am meisten nutzt, als Profil wählen und sofort öffnen.
- **Moderne Oberfläche, die sich gut ansehen lässt**: klare Fluent-Icons, Tabs im Stil von Windows Terminal und auf Wunsch ein CRT-Effekt — nichts Steifes.
- **Sicheres Einfügen (Bracketed Paste)**: mehrzeiliger Code wird ohne fremde Enters eingefügt, und ab 5 KB wird einmal nachgefragt, damit nichts versehentlich landet.
- **Kopieren, Links und Bilder im TUI an einer Stelle**:
  - Beim Kopieren werden Leerzeichen am Zeilenende automatisch entfernt.
  - Ein Tipp auf ein Bild in der Zwischenablage speichert es als PNG und trägt den Pfad ein.
  - `Ctrl` halten und eine URL auf dem Bildschirm antippen öffnet sie im Browser.
- **Benachrichtigungen ohne Zusatz-Apps & 13 Sprachen**: Glocke und Benachrichtigungen funktionieren ohne `termux-api`, die Oberfläche unterstützt 13 Sprachen.
- **Im Hintergrund wiederhergestellt**: App schließen und zurückkommen — Bildschirm und Mausmodus sind noch da.

---

## Installation

Was du brauchst, ist einfach: **APK installieren**, **eine Zeile in Termux ausführen** und **3 Berechtigungen erlauben**.

1. Lade **`fterm.apk`** aus den [Releases](https://github.com/CrashedSystem/fterm/releases/latest) und installiere sie.
2. Öffne **Termux** und füge die Zeile unten ein, um sie auszuführen.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Starte **fterm** und erlaube die drei Berechtigungen, nach denen es fragt.
   - **Zugriff auf alle Dateien**: damit die App den Token mit dem Termux-Daemon austauschen kann.
   - **Fotos/Bilder**: um Screenshots aus der Zwischenablage einzufügen.
   - **Benachrichtigungen**: damit die Glocke läutet, wenn ein Job fertig ist.

Sobald du sie erlaubst und zur App zurückkehrst, verbindet sie sich automatisch.

---

## Nützliche Kürzel

- **Neuer Tab / Tab schließen**: `+`-Button / Tab lange drücken (Tastatur `Ctrl+Shift+T` / `W`)
- **Einstellungen öffnen**: `+` lange drücken (`Ctrl+,`)
- **Tab wechseln**: `Ctrl+Tab` / `Ctrl+1–9`
- **URL sofort öffnen**: `Ctrl` + auf den Bildschirm tippen oder klicken
- **Kopieren / Einfügen**: `Ctrl+Shift+C` / `V`, oder lange drücken · Rechtsklick
- **Schriftgröße**: `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Mehr erfahren

- Die App spricht mit nichts außerhalb des Geräts — nur über `127.0.0.1` — und die Release-Dateien werden von GitHub Actions automatisch gebaut und geprüft.
- Interna und Entwicklungsnotizen stehen in [`docs/ROADMAP.md`](../ROADMAP.md).
- Lizenz: [MIT](../../LICENSE) (Engine: Termux [Apache-2.0](../../licenses/Apache-2.0.txt), Schriften: [OFL-1.1](../../licenses/OFL-1.1.txt))
