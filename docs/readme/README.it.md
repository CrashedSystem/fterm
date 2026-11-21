# fterm

**Ti è mai capitato di innervosirti così usando Termux?**

**Italiano** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [中文(繁體)](README.zh-TW.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Français](README.fr.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## Termux, a dire il vero, è un po' scomodo

- Per cambiare un font, un colore o un solo tasto in più bisogna aprire un file di configurazione (`termux.properties`).
- Per cambiare scheda bisogna far scorrere ogni volta la barra laterale da sinistra.
- Con le schermate intere (TUI) come OpenCode, la tastiera a volte non si apre proprio.
- Il coreano e le altre lingue si disallineano, e infilare uno screenshot nel prompt è una fatica.
- Incollare molto testo scatta, e gli a capo si spezzano in Invii separati.
- Solo per un avviso a fine lavoro bisogna installare `termux-api` a parte.

---

## fterm è partito proprio da lì

La shell resta quella di Termux che già conosci; **abbiamo rifatto da zero solo schermo e input, che erano insopportabili.**

- **Schede a colpo d'occhio**: apri, cambi, rinomini e colori le schede dalla barra in alto, senza scorrimenti laterali.
- **Personalizzazione senza file di configurazione**: temi di colore, font (coreano e icone già inclusi), immagini di sfondo e barra dei tasti in basso si cambiano dalle impostazioni dell'app.
- **Profili pronti**: scegli la shell o il comando che usi di più come profilo e aprilo subito.
- **Un'interfaccia moderna e piacevole**: icone Fluent pulite, schede in stile Windows Terminal e un effetto CRT opzionale — niente di rigido.
- **Incolla sicuro (bracketed paste)**: il codice su più righe si incolla senza Invii indesiderati, e oltre i 5 KB l'app chiede una volta prima, così non incolli niente per sbaglio.
- **Copia, link e immagini del TUI in un posto solo**:
  - Copiando, gli spazi a fine riga vengono tolti da soli.
  - Un tocco su un'immagine negli appunti la salva in PNG e scrive il percorso.
  - Tieni premuto `Ctrl` e tocca un URL sullo schermo per aprirlo nel browser.
- **Notifiche senza app aggiuntive e 13 lingue**: campanello e notifiche funzionano senza `termux-api`, e l'interfaccia supporta 13 lingue.
- **Ripristino in background**: chiudi l'app e torna — lo schermo e la modalità mouse sono ancora lì.

---

## Installazione

Serve poco: **installare l'APK**, **eseguire una riga in Termux** e **concedere 3 autorizzazioni**.

1. Scarica **`fterm.apk`** dalle [Releases](https://github.com/CrashedSystem/fterm/releases/latest) e installalo.
2. Apri **Termux** e incolla la riga qui sotto per eseguirla.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Avvia **fterm** e concedi le tre autorizzazioni che chiede.
   - **Accesso a tutti i file**: serve all'app per scambiare il token con il demone di Termux.
   - **Foto/immagini**: per incollare gli screenshot dagli appunti.
   - **Notifiche**: per far suonare il campanello a fine lavoro.

Appena le concedi e torni nell'app, la connessione avviene da sola.

---

## Scorciatoie utili

- **Nuova scheda / chiudi scheda**: pulsante `+` / pressione lunga su una scheda (tastiera `Ctrl+Shift+T` / `W`)
- **Apri impostazioni**: pressione lunga su `+` (`Ctrl+,`)
- **Cambia scheda**: `Ctrl+Tab` / `Ctrl+1–9`
- **Apri un URL al volo**: `Ctrl` + tocca o clicca sullo schermo
- **Copia / incolla**: `Ctrl+Shift+C` / `V`, oppure pressione lunga · clic destro sullo schermo
- **Dimensione del testo**: `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Per saperne di più

- Non comunica con nulla fuori dal dispositivo, solo con `127.0.0.1`, e i file di rilascio sono compilati e verificati automaticamente da GitHub Actions.
- Struttura interna e note di sviluppo sono in [`docs/ROADMAP.md`](../ROADMAP.md).
- Licenza: [MIT](../../LICENSE) (motore: Termux [Apache-2.0](../../licenses/Apache-2.0.txt), font: [OFL-1.1](../../licenses/OFL-1.1.txt))
