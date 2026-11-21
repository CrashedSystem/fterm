# fterm

**Já passou por essas chatices usando o Termux?**

**Português** · [English](../../README.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [中文(简体)](README.zh-CN.md) · [中文(繁體)](README.zh-TW.md) · [Deutsch](README.de.md) · [Español](README.es.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Русский](README.ru.md) · [Srpski](README.sr.md) · [Українська](README.uk.md)

---

## O Termux, sinceramente, é meio incômodo

- Para mudar uma fonte, uma cor ou uma tecla extra embaixo é preciso abrir um arquivo de configuração (`termux.properties`).
- Para trocar de aba é preciso arrastar a barra lateral a partir da esquerda toda vez.
- Com telas cheias (TUI) como o OpenCode, o teclado às vezes simplesmente não abre.
- Coreano e outros idiomas ficam desalinhados, e colocar um print no prompt dá trabalho.
- Colar muito texto engasga, e as quebras de linha viram Enters separados.
- Só para receber um aviso quando a tarefa termina é preciso instalar o `termux-api` à parte.

---

## O fterm começou daí

O shell continua sendo o do Termux que você já conhece; **só a tela e a entrada, que eram insuportáveis, foram refeitas do zero.**

- **Abas à vista**: abra, troque, renomeie e colore abas direto na barra de cima, sem arrastar.
- **Personalização sem arquivo de configuração**: temas de cor, fontes (coreano e ícones já inclusos), imagens de fundo e a barra de teclas de baixo mudam nas configurações do próprio app.
- **Perfis prontos**: escolha o shell ou o comando que você mais usa como perfil e abra na hora.
- **Interface moderna e agradável de olhar**: ícones Fluent limpos, abas no estilo do Windows Terminal e um efeito CRT opcional — nada engessado.
- **Colar seguro (bracketed paste)**: código de várias linhas entra sem Enters soltos, e acima de 5 KB o app pergunta uma vez para nada entrar por acidente.
- **Cópia, links e imagens do TUI num lugar só**:
  - Ao copiar, os espaços no fim da linha são removidos sozinhos.
  - Um toque numa imagem da área de transferência salva como PNG e já escreve o caminho.
  - Segure `Ctrl` e toque numa URL na tela para abrir no navegador.
- **Notificações sem apps extras e 13 idiomas**: o sino e as notificações funcionam sem o `termux-api`, e a interface do app tem 13 idiomas.
- **Restauração em segundo plano**: feche o app e volte — a tela e o modo mouse continuam lá.

---

## Instalação

É simples: **instalar o APK**, **rodar uma linha no Termux** e **liberar 3 permissões**.

1. Baixe o **`fterm.apk`** em [Releases](https://github.com/CrashedSystem/fterm/releases/latest) e instale.
2. Abra o **Termux** e cole a linha abaixo para executar.

```bash
curl -fsSL https://raw.githubusercontent.com/CrashedSystem/fterm/master/termux/setup.sh | bash
```

3. Abra o **fterm** e libere as três permissões pedidas.
   - **Acesso a todos os arquivos**: para o app trocar o token com o daemon do Termux.
   - **Fotos/imagens**: para colar prints da área de transferência.
   - **Notificações**: para o sino tocar quando a tarefa terminar.

Depois de liberar e voltar ao app, a conexão acontece sozinha.

---

## Atalhos que valem a pena

- **Nova aba / fechar aba**: botão `+` / pressione e segure uma aba (teclado `Ctrl+Shift+T` / `W`)
- **Abrir configurações**: pressione e segure `+` (`Ctrl+,`)
- **Trocar de aba**: `Ctrl+Tab` / `Ctrl+1–9`
- **Abrir uma URL na hora**: `Ctrl` + toque ou clique na tela
- **Copiar / colar**: `Ctrl+Shift+C` / `V`, ou pressione e segure · clique com o botão direito
- **Tamanho da letra**: `Ctrl+=` / `Ctrl+-` / `Ctrl+0`

---

## Saiba mais

- Não conversa com nada fora do aparelho, só com `127.0.0.1`, e os arquivos de release são compilados e verificados automaticamente pelo GitHub Actions.
- A estrutura interna e as notas de desenvolvimento estão em [`docs/ROADMAP.md`](../ROADMAP.md).
- Licença: [MIT](../../LICENSE) (motor: Termux [Apache-2.0](../../licenses/Apache-2.0.txt), fontes: [OFL-1.1](../../licenses/OFL-1.1.txt))
