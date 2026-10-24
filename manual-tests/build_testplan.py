#!/usr/bin/env python3
"""
Erzeugt den Handtest-Plan als HTML und PDF (über Chrome headless).

    python3 build_testplan.py           # testplan.html und LatherIt-Handtests.pdf

Die Links im PDF zeigen auf die Dateien in diesem Ordner (absoluter Pfad des Rechners, auf dem gebaut
wird) - daher wird das PDF nicht eingecheckt. Auf einer Test-VM nach dem Kopieren des Ordners neu bauen.
"""
import html
import os
import re
import shutil
import subprocess
import sys
from urllib.parse import quote

from testplan_inhalt import KAPITEL, SITZUNGEN, TITEL, UNTERTITEL, VORWORT

HERE = os.path.dirname(os.path.abspath(__file__))
FILES = os.path.join(HERE, "dateien")


# Alle Test-IDs: Erwähnungen wie „V-04“ im Text werden zu Sprungmarken (in Vorschau mit ⌘[ zurück).
CASE_IDS = {
    case["id"]
    for chapter in KAPITEL
    for container in [chapter, *chapter.get("unter", [])]
    for case in container.get("faelle", [])
}


def file_url(relative):
    path = os.path.join(FILES, relative)
    if not os.path.exists(path):
        sys.exit(f"Verlinkte Datei fehlt: dateien/{relative}")
    return "file://" + quote(path.replace(os.sep, "/") if os.sep != "/" else path)


def inline(text):
    """Kleine Auszeichnung: `code`, **fett**, [[datei:pfad]], [[url:adresse]], [[url:adresse|Text]]."""
    codes = []

    def keep_code(m):
        codes.append(f"<code>{m.group(1)}</code>")
        return f"\0{len(codes) - 1}\0"

    out = re.sub(r"`([^`]+)`", keep_code, html.escape(text, quote=False))
    out = "".join(
        piece if piece.startswith("[[") else re.sub(
            r"\b([A-Z]-\d{2})\b",
            lambda m: f'<a class="ref" href="#{m.group(1)}">{m.group(1)}</a>' if m.group(1) in CASE_IDS else m.group(0),
            piece,
        )
        for piece in re.split(r"(\[\[.+?\]\])", out)
    )
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"(?<![\w*])\*([^*\s][^*]*?)\*(?![\w*])", r"<i>\1</i>", out)
    out = re.sub("\0(\\d+)\0", lambda m: codes[int(m.group(1))], out)

    def link(m):
        kind, target = m.group(1), html.unescape(m.group(2))
        label = None
        if "|" in target:
            target, label = target.split("|", 1)
        if kind == "datei":
            return f'<a class="file" href="{html.escape(file_url(target))}">{html.escape(label or "dateien/" + target)}</a>'
        return f'<a href="{html.escape(target)}">{html.escape(label or target)}</a>'

    return re.sub(r"\[\[(datei|url):(.+?)\]\]", link, out)


code_blocks = []


def code_block(code):
    """Code-Block. Damit Kopieren direkt aus dem PDF (Vorschau) den Text in der richtigen Reihenfolge liefert:
    Schrift Menlo in ganzen Pixeln (bei 8,2 pt ordnete Vorschau Zeilen und Kommas um), lange Zeilen
    kleiner gesetzt statt abgeschnitten oder umbrochen."""
    code_blocks.append(code)
    anchor = f"c{len(code_blocks)}"
    text = code.strip(chr(10))
    longest = max(len(line) for line in text.split("\n"))
    style = f' style="font-size: {max(7, min(11, int(680 / (0.6 * longest))))}px"' if longest > 100 else ""
    return f'<pre class="code" id="{anchor}"{style}>{html.escape(text)}</pre>'


def render_case(case):
    parts = [f'<section class="case" id="{case["id"]}">']
    tag = f'<span class="tag">{html.escape(case["tag"])}</span>' if case.get("tag") else ""
    parts.append(f'<h4><span class="id">{case["id"]}</span> {inline(case["titel"])}{tag}</h4>')
    if case.get("ziel"):
        parts.append(f'<p class="ziel">{inline(case["ziel"])}</p>')
    if case.get("vorher"):
        parts.append(f'<p class="label">Vorbereitung</p><p>{inline(case["vorher"])}</p>')
    parts.append('<p class="label">Schritte</p><ol>')
    for step in case["schritte"]:
        if isinstance(step, tuple):
            text, code = step
            parts.append(f"<li>{inline(text)}{code_block(code)}</li>")
        else:
            parts.append(f"<li>{inline(step)}</li>")
    parts.append("</ol>")
    parts.append('<p class="label">Erwartet</p><ul class="expected">')
    for item in case["erwartet"]:
        parts.append(f"<li>{inline(item)}</li>")
    parts.append("</ul>")
    if case.get("hinweis"):
        parts.append(f'<p class="hint">{inline(case["hinweis"])}</p>')
    parts.append("</section>")
    return "\n".join(parts)


def render_table(rows, header):
    out = ['<table><thead><tr>' + "".join(f"<th>{inline(h)}</th>" for h in header) + "</tr></thead><tbody>"]
    for row in rows:
        out.append("<tr>" + "".join(f"<td>{inline(str(c))}</td>" for c in row) + "</tr>")
    out.append("</tbody></table>")
    return "\n".join(out)


def render_block(block):
    kind = block[0]
    if kind == "p":
        return f"<p>{inline(block[1])}</p>"
    if kind == "code":
        return code_block(block[1])
    if kind == "liste":
        return "<ul>" + "".join(f"<li>{inline(i)}</li>" for i in block[1]) + "</ul>"
    if kind == "tabelle":
        return f'<div class="table">{render_table(block[2], block[1])}</div>'
    raise ValueError(kind)


def build_html():
    toc, body = [], []
    for number, chapter in enumerate(KAPITEL, 1):
        anchor = f"k{number}"
        toc.append(f'<li><a href="#{anchor}">{html.escape(chapter["titel"])}</a>'
                   f'<span class="toc-sub">{inline(chapter.get("kurz", ""))}</span></li>')
        body.append(f'<h2 id="{anchor}" class="chapter">{number}. {html.escape(chapter["titel"])}</h2>')
        for block in chapter.get("text", []):
            body.append(render_block(block))
        for sub_number, sub in enumerate(chapter.get("unter", []), 1):
            body.append(f'<h3>{number}.{sub_number} {html.escape(sub["titel"])}</h3>')
            for block in sub.get("text", []):
                body.append(render_block(block))
            body.extend(render_case(case) for case in sub.get("faelle", []))
        body.extend(render_case(case) for case in chapter.get("faelle", []))

    sessions = render_table(SITZUNGEN, ["Sitzung", "Inhalt", "Dauer", "Voraussetzung"])
    intro = "\n".join(render_block(b) for b in VORWORT)
    with open(os.path.join(HERE, "testplan.css"), encoding="utf-8") as f:
        css = f.read()
    return f"""<!doctype html>
<html lang="de"><head><meta charset="utf-8"><title>{html.escape(TITEL)}</title><style>{css}</style></head>
<body>
<header class="cover">
  <h1>{html.escape(TITEL)}</h1>
  <p class="subtitle">{html.escape(UNTERTITEL)}</p>
</header>
{intro}
<h2 class="nobreak">Sitzungsplan</h2>
<p>Eine Sitzung ist ein Stück, das sich am Stück erledigen lässt. Die Reihenfolge ist ein Vorschlag;
Voraussetzung ist immer die Vorbereitung (Kapitel 1).</p>
<div class="table">{sessions}</div>
<h2 class="nobreak">Inhalt</h2>
<ol class="toc">{"".join(toc)}</ol>
{"".join(body)}
</body></html>"""


def find_chrome():
    for candidate in (
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        shutil.which("google-chrome") or "",
        shutil.which("chromium") or "",
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    ):
        if candidate and os.path.exists(candidate):
            return candidate
    sys.exit("Chrome nicht gefunden - testplan.html im Browser öffnen und als PDF drucken.")


def main():
    html_path = os.path.join(HERE, "testplan.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(build_html())
    pdf_path = os.path.join(HERE, "LatherIt-Handtests.pdf")
    subprocess.run([
        find_chrome(), "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}", "file://" + quote(html_path),
    ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print(pdf_path)


if __name__ == "__main__":
    main()
