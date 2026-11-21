# fterm

**Da li ti se ovo dešavalo u Termuxu?**

**Srpski** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [中文(繁體)](README.zh-TW.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Українська](README.uk.md)

---

## Termux je, iskreno, pomalo nezgodan

- Da promeniš font, boju ili jednu dodatnu tipku na dnu, moraš da otvoriš konfiguracioni fajl (`termux.properties`).
- Da promeniš tab, svaki put moraš da prevučeš bočnu traku sa leve strane.
- U celoekranskim interfejsima (TUI) kao što je OpenCode, tastatura ponekad jednostavno neće da se otvori.
- Korejski i drugi jezici se razmiču, a ubacivanje snimka ekrana u prompt je prava muka.
- Lepljenje dugog teksta secka, a prelomi redova se raspadaju na zasebne Entere.
- Čak i jedno obaveštenje kad se posao završi traži posebno instaliran `termux-api`.

---

## fterm je počeo upravo tu

Ljuskа je i dalje ona poznata iz Termuxa — **samo su ekran i unos, koji su nervirali, napravljeni iznova.**

- **Tabovi na prvi pogled**: otvaraj, menjaj, preimenuj i boji tabove direktno na gornjoj traci, bez prevlačenja.
- **Prilagođavanje bez konfiguracionih fajlova**: teme boja, fontovi (korejski i ikone već ugrađeni), pozadinske slike i donja traka sa tipkama menjaju se u podešavanjima same aplikacije.
- **Gotovi profili**: izaberi ljusku ili komandu koju najviše koristiš kao profil i otvori je odmah.
- **Moderan interfejs koji prija oku**: čiste Fluent ikone, tabovi u stilu Windows Terminala i CRT efekat po želji — ništa kruto.
- **Bezbedno lepljenje (bracketed paste)**: višeredni kod se lepi bez zalutalih Entera, a na tekstu većem od 5 KB aplikacija jednom pita da nešto ne bi slučajno ušlo.
- **Kopiranje, linkovi i slike u TUI-ju na jednom mestu**:
  - Prilikom kopiranja prazna mesta na kraju reda se sama uklanjaju.
  - Jedan dodir na sliku u klipbordu čuva je kao PNG i upisuje putanju.
  - Drži `Ctrl` i dodirni URL na ekranu da ga otvoriš u pregledaču.
- **Obaveštenja bez dodatnih aplikacija i 13 jezika**: zvono i obaveštenja rade bez `termux-api`, a interfejs podržava 13 jezika.
- **Vraćanje u pozadini**: zatvori aplikaciju i vrati se — ekran i režim miša su još tu.

---

## Instalacija

Potrebno je samo: **instaliraj APK**, **pokreni jednu liniju u Termuxu** i **dozvoli 3 dozvole**.

1. Preuzmi **`fterm.apk`** sa [Releases](https://github.com/CrashedSystem/fterm/releases/latest) i instaliraj ga.
2. Otvori **Termux** i nalepi liniju ispod da je pokreneš.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Pokreni **fterm** i dozvoli tri dozvole koje traži.
   - **Pristup svim fajlovima**: da bi aplikacija razmenila token sa Termux demonom.
   - **Fotografije/slike**: da bi lepila snimke ekrana iz klipborda.
   - **Obaveštenja**: da bi zazvonilo kad se posao završi.

Kad ih dozvoliš i vratiš se u aplikaciju, veza se uspostavlja sama.

---

## Korisne prečice

- **Novi tab / zatvori tab**: dugme `+` / dugi pritisak na tab (tastatura `Ctrl+Shift+T` / `W`)
- **Otvori podešavanja**: dugi pritisak na `+` (`Ctrl+,`)
- **Menjanje tabova**: `Ctrl+Tab` / `Ctrl+1–9`
- **Otvori URL odmah**: `Ctrl` + dodir ili klik na ekran
- **Kopiraj / nalepi**: `Ctrl+Shift+C` / `V`, ili dugi pritisak · desni klik na ekran
- **Veličina teksta**: `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Saznaj više

- Ne komunicira ni sa čim van uređaja — samo preko `127.0.0.1` — a fajlove izdanja automatski grade i proveravaju GitHub Actions.
- Unutrašnja struktura i beleške o razvoju su u [`docs/ROADMAP.md`](../ROADMAP.md).
- Licenca: [MIT](../../LICENSE) (motor: Termux [Apache-2.0](../../licenses/Apache-2.0.txt), fontovi: [OFL-1.1](../../licenses/OFL-1.1.txt))
