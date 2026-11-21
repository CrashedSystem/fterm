# fterm

**Tu as déjà connu ces frustrations avec Termux ?**

**Français** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [中文(繁體)](README.zh-TW.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Italiano](README.it.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## Termux, franchement, c'est un peu pénible

- Changer une police, une couleur ou une seule touche en bas oblige à ouvrir un fichier de config (`termux.properties`).
- Changer d'onglet, c'est un balayage depuis le bord gauche à chaque fois.
- Avec une interface plein écran (TUI) comme OpenCode, le clavier refuse parfois de s'ouvrir.
- Le coréen et les autres langues se décalent, et glisser une capture d'écran dans un prompt est une galère.
- Coller beaucoup de texte saccade, et les retours à la ligne partent en Entrée séparées.
- Pour une seule notification en fin de tâche, il faut installer `termux-api` à part.

---

## fterm est parti de là

Le shell reste celui de Termux que tu connais ; **seuls l'écran et la saisie, insupportables, ont été refaits de zéro.**

- **Des onglets visibles d'un coup d'œil** : ouvrir, changer, renommer et colorer depuis la barre du haut, sans balayage.
- **Personnaliser sans fichier de config** : thèmes de couleurs, polices (coréen et icônes inclus), images de fond et barre de touches du bas se règlent dans les paramètres de l'app.
- **Des profils prêts à l'emploi** : choisis le shell ou la commande que tu utilises le plus et ouvre-le aussitôt.
- **Une interface moderne et agréable** : icônes Fluent nettes, onglets façon Windows Terminal, effet CRT optionnel — rien de rigide.
- **Collage sûr (bracketed paste)** : du code multiligne se colle sans Entrée parasite, et au-delà de 5 Ko l'app demande une fois pour éviter un collage accidentel.
- **Copie, liens et images du TUI au même endroit** :
  - Les espaces en fin de ligne sont retirés automatiquement à la copie.
  - Une pression sur une image du presse-papiers l'enregistre en PNG et écrit le chemin.
  - Maintiens `Ctrl` et touche une URL à l'écran pour l'ouvrir dans le navigateur.
- **Notifications sans appli en plus et 13 langues** : sonnerie et notifications fonctionnent sans `termux-api`, et l'interface prend en charge 13 langues.
- **Restauration en arrière-plan** : ferme l'app et reviens — l'écran et le mode souris sont toujours là.

---

## Installation

Il faut trois choses simples : **installer l'APK**, **lancer une ligne dans Termux** et **accorder 3 autorisations**.

1. Télécharge **`fterm.apk`** depuis les [Releases](https://github.com/CrashedSystem/fterm/releases/latest) et installe-le.
2. Ouvre **Termux** et colle la ligne ci-dessous pour l'exécuter.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Lance **fterm** et accorde les trois autorisations demandées.
   - **Accès à tous les fichiers** : pour que l'app échange le jeton avec le démon Termux.
   - **Photos/images** : pour coller les captures du presse-papiers.
   - **Notifications** : pour faire sonner la cloche à la fin d'une tâche.

Dès que tu les accordes et que tu reviens dans l'app, la connexion se fait toute seule.

---

## Raccourcis utiles

- **Nouvel onglet / fermer l'onglet** : bouton `+` / appui long sur un onglet (clavier `Ctrl+Shift+T` / `W`)
- **Ouvrir les paramètres** : appui long sur `+` (`Ctrl+,`)
- **Changer d'onglet** : `Ctrl+Tab` / `Ctrl+1–9`
- **Ouvrir une URL aussitôt** : `Ctrl` + toucher ou cliquer sur l'écran
- **Copier / coller** : `Ctrl+Shift+C` / `V`, ou appui long · clic droit sur l'écran
- **Taille du texte** : `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Pour aller plus loin

- L'app ne parle à rien hors de l'appareil, seulement à `127.0.0.1`, et les fichiers de version sont compilés et vérifiés automatiquement par GitHub Actions.
- La structure interne et les notes de développement sont dans [`docs/ROADMAP.md`](../ROADMAP.md).
- Licence : [MIT](../../LICENSE) (moteur : Termux [Apache-2.0](../../licenses/Apache-2.0.txt), polices : [OFL-1.1](../../licenses/OFL-1.1.txt))
