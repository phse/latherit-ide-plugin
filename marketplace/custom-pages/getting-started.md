The LatherIt UI is available in English and German. It follows the language of the IDE and can be changed in LatherIt's settings.

## Project files

A LatherIt project is a single YAML file named `*.latherit.yaml` (or `.latherit.yml`) that lives in your repository. Opening it shows two editor tabs: **LatherIt** (the client) and **Text** (the YAML).

| Action | How |
| --- | --- |
| New project | *File \| New \| LatherIt Project* |
| Import from SoapUI | *File \| New \| LatherIt Project from SoapUI…* (`*-soapui-project.xml`) |
| Save | automatic, through the IDE document: changes show up in the Text tab, Git and Local History |
| External changes | edits in the Text tab, `git pull` or another program reload the UI |

## Sidebar module

Optional, per project and only for you (stored in the workspace, not shared with the team): the LatherIt tree as a tool window next to *Project*, each request, test or mock in its own editor tab. Without it everything stays as described above.

| | |
| --- | --- |
| Turn on | *Settings \| Tools \| LatherIt*, *Tools \| LatherIt as Sidebar Module*, or the button next to the tree in the LatherIt editor |
| Turn off | *Switch Back to Editor View* in the tool window title, or the same switches |
| Project file | the first `*.latherit.yaml` in the project; with several, choose in the tool window title. Opening a project file shows it in the tool window |
| Open a node | double-click or Enter (single click with *Open Files with Single Click*) |
| Behavior (⋮ in the tool window) | *Enable Preview Tab*, *Open Files with Single Click*, *Open Directories with Single Click*, *Always Select Opened File*. Initially taken from the Project tool window, then independent; *Enable Preview Tab* is the IDE-wide setting and stays shared |
| Tabs | renamed and closed together with their node; tabs with the same name show their parent |
| Mock services | run in the tool window, also when started from a tab |

## Try it in two minutes

1. *File \| New \| LatherIt Project*, enter a name.
2. In the LatherIt tab press **⇧⌘N** (Ctrl+Shift+N), choose `GET` and enter any URL, e.g. `https://httpbin.org/get?hello=world`.
3. Press **⌘↵** (Ctrl+Enter) to send. The response shows status, time and size, formatted or raw; the *Sent* tab shows the request as actually sent.
4. For SOAP: **⌘I** (Ctrl+I) imports a WSDL from a URL or a file and generates sample requests.

## Keyboard shortcuts

In the LatherIt tab these go to LatherIt, even where the IDE uses them itself. On Windows and Linux use Ctrl instead of ⌘.

| Shortcut (macOS) | Action |
| --- | --- |
| ⌘↵ | Send request, run test or load test, start or stop mock |
| ⇧⌘N | New REST request |
| ⌘I | Import WSDL |
| ⌘B | Put request on the Board or remove it |
| ⌃1 … ⌃9 (Windows/Linux: Alt+1 … 9) | Open Board entry |
| ⌘P | Go to request, test or mock in all open LatherIt projects |
| ⌥⌘F | Filter the tree (or just start typing in it) |
| ⇧⌥F | Format request (also the IDE's *Reformat Code* shortcut, e.g. ⌥⌘L) |
| ⇧⌘F | The IDE's *Find in Files* with the selected text |
| ⌘1 / ⌘2 | Focus tree / editor |
| ⌘F, ⌘G | Find in editor, next match |
| ⌥⌘← / ⌥⌘→ | Back / forward |
| F2 (sidebar module: also the IDE's Rename, ⇧F6) | Rename (or right-click in the tree) |
| ⌘⌫ | Delete request, step or interface in the tree |

## Good to know

- **Mock services** run inside the IDE and belong to the editor tab that started them. They stop when the tab is closed (sidebar module: when the tool window's project closes).
- **HTTP** uses the proxy from the IDE settings (*Appearance & Behavior | System Settings | HTTP Proxy*).
- **HTTPS** trusts the root certificates of macOS or Windows, like your browser, and then those of the IDE.
- **Property expansion** works like in SoapUI: `${#Project#name}`, `${#System#…}`, `${#Env#NAME}`, inline scripts.
- **Board**: requests you use often go on the Board above the tree (⌘B) and open with ⌃1 … ⌃9.
- **Light/dark** follows the IDE theme.
