# fterm

**¿Te has topado con estas molestias usando Termux?**

**Español** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [中文(繁體)](README.zh-TW.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Português](README.pt.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## Termux, la verdad, es algo incómodo

- Para cambiar una fuente, un color o una sola tecla extra hay que abrir un archivo de configuración (`termux.properties`).
- Para cambiar de pestaña hay que deslizar la barra lateral desde la izquierda cada vez.
- Con interfaces a pantalla completa (TUI) como OpenCode, el teclado a veces no se abre.
- Los textos en coreano y otros idiomas se desalinean, y meter una captura en el prompt es un lío.
- Pegar mucho texto da tirones, y los saltos de línea se parten en Enters sueltos.
- Solo para recibir un aviso al terminar un trabajo hay que instalar `termux-api` aparte.

---

## fterm empezó justo ahí

El shell sigue siendo el mismo Termux de siempre; **solo rehicimos la pantalla y la entrada, que eran lo insufrible.**

- **Pestañas a la vista**: abre, cambia, renombra y colorea pestañas desde la barra superior, sin deslizar.
- **Personalización sin archivos de configuración**: temas de color, fuentes (coreano e iconos incluidos), imágenes de fondo y la barra de teclas inferior se cambian desde los ajustes de la app.
- **Perfiles listos**: elige el shell o el comando que más uses como perfil y ábrelo al instante.
- **Una interfaz moderna y agradable**: iconos Fluent limpios, pestañas al estilo de Windows Terminal y un efecto CRT opcional: nada rígido.
- **Pegado seguro (bracketed paste)**: pega código de varias líneas sin Enters sueltos, y si pasa de 5 KB te pregunta una vez para que no se cuele nada por accidente.
- **Copiar, enlaces e imágenes del TUI en un solo sitio**:
  - Al copiar, se quitan solos los espacios al final de cada línea.
  - Un toque en una imagen del portapapeles la guarda como PNG y escribe la ruta.
  - Mantén `Ctrl` y toca una URL en pantalla para abrirla en el navegador.
- **Avisos sin apps extra y 13 idiomas**: el timbre y las notificaciones funcionan sin `termux-api`, y la interfaz admite 13 idiomas.
- **Se restaura en segundo plano**: cierra la app y vuelve: la pantalla y el modo ratón siguen vivos.

---

## Instalación

Solo hace falta: **instalar el APK**, **ejecutar una línea en Termux** y **conceder 3 permisos**.

1. Descarga **`fterm.apk`** desde [Releases](https://github.com/CrashedSystem/fterm/releases/latest) e instálalo.
2. Abre **Termux** y pega la línea de abajo para ejecutarla.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Abre **fterm** y concede los tres permisos que pide.
   - **Acceso a todos los archivos**: para que la app intercambie el token con el demonio de Termux.
   - **Fotos/imágenes**: para pegar capturas del portapapeles.
   - **Notificaciones**: para que suene el timbre al terminar un trabajo.

Cuando los concedas y vuelvas a la app, se conecta sola.

---

## Atajos que conviene saber

- **Nueva pestaña / cerrar pestaña**: botón `+` / mantener pulsada una pestaña (teclado `Ctrl+Shift+T` / `W`)
- **Abrir ajustes**: mantener pulsado `+` (`Ctrl+,`)
- **Cambiar de pestaña**: `Ctrl+Tab` / `Ctrl+1–9`
- **Abrir una URL al instante**: `Ctrl` + tocar o hacer clic en la pantalla
- **Copiar / pegar**: `Ctrl+Shift+C` / `V`, o mantener pulsada la pantalla · clic derecho
- **Tamaño de letra**: `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Más información

- No se comunica con nada fuera del dispositivo, solo con `127.0.0.1`, y los archivos de la versión los compila y verifica GitHub Actions automáticamente.
- La estructura interna y las notas de desarrollo están en [`docs/ROADMAP.md`](../ROADMAP.md).
- Licencia: [MIT](../../LICENSE) (motor: Termux [Apache-2.0](../../licenses/Apache-2.0.txt), fuentes: [OFL-1.1](../../licenses/OFL-1.1.txt))
