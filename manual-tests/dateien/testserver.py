#!/usr/bin/env python3
"""
Testserver für die Handtests von LatherIt (nur Python-Standardbibliothek, ab Python 3.9).

    python3 testserver.py                  # HTTP 8088 und 8090, HTTPS 8443, Proxy 8089
    python3 testserver.py --proxy-auth     # Proxy verlangt Anmeldung (proxy / proxy)

Übersicht aller Adressen: http://localhost:8088/
Jeder Request wird hier im Terminal protokolliert - so sieht man, was LatherIt tatsächlich schickt.
"""
import argparse
import base64
import gzip
import hashlib
import json
import os
import re
import secrets
import select
import socket
import ssl
import struct
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlsplit

HERE = os.path.dirname(os.path.abspath(__file__))

BASIC_USER, BASIC_PASSWORD = "anna", "geheim"
BEARER_TOKEN = "test-token-123"
DIGEST_REALM = "LatherIt-Test"

ORDERS = {
    "4711": {"id": "4711", "customer": "C-1007", "status": "open", "total": 23.80,
             "items": [{"sku": "TEA-GREEN-100", "quantity": 2}, {"sku": "MUG-STONE", "quantity": 1}]},
    "4712": {"id": "4712", "customer": "C-1012", "status": "open", "total": 112.50, "items": []},
    "4713": {"id": "4713", "customer": "C-1003", "status": "shipped", "total": 9.90, "items": []},
    # Für das SoapUI-Referenzprojekt (REST-Request „Bestellung 7“)
    "7": {"id": "7", "customer": "C-7", "status": "open", "total": 7.00,
          "items": [{"sku": "A-1", "quantity": 1}, {"sku": "B-2", "quantity": 3}]},
}

SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/"
SOAP12 = "http://www.w3.org/2003/05/soap-envelope"

INDEX = """<!doctype html><meta charset="utf-8"><title>LatherIt-Testserver</title>
<style>body{font:15px system-ui;margin:2em;max-width:60em}code{background:#eee;padding:0 .3em}</style>
<h1>LatherIt-Testserver</h1>
<h2>SOAP</h2><ul>
<li><code>GET /shop?wsdl</code> WSDL (lädt <code>/xsd/shop-types.xsd</code> und <code>/xsd/common.xsd</code>)</li>
<li><code>POST /shop</code> SOAP 1.1: Ping, GetOrder (orderId 0 = Fault, Session „abgelaufen“ = Fault)</li>
<li><code>POST /shop12</code> SOAP 1.2</li></ul>
<h2>REST</h2><ul>
<li><code>GET/POST /api/orders</code>, <code>GET/PUT/PATCH/DELETE /api/orders/{id}</code> (4711, 4712, 4713, 7)</li>
<li><code>/api/echo/…</code> beliebige Methode: zeigt Methode, Pfad, Matrix-, Query-Parameter, Header, Body</li></ul>
<h2>HTTP</h2><ul>
<li><code>/http/status/{code}</code>, <code>/http/delay/{ms}</code>, <code>/http/redirect/{n}</code>, <code>/http/redirect-post</code></li>
<li><code>/http/charset/latin1</code>, <code>/http/charset/utf16bom</code>, <code>/http/charset/ohne-angabe</code></li>
<li><code>/http/big?mb=5&amp;format=xml|json</code>, <code>/http/headers</code>, <code>/http/gzip</code>, <code>/http/empty</code>,
<code>/http/html</code>, <code>/http/binary</code>, <code>/http/abbruch</code></li></ul>
<h2>Anmeldung</h2><ul>
<li><code>/auth/basic</code> anna / geheim</li><li><code>/auth/bearer</code> Token test-token-123</li>
<li><code>/auth/digest</code> anna / geheim</li><li><code>/auth/ntlm</code> beliebiger Benutzer (prüft nur den Ablauf)</li>
<li><code>/auth/alle</code> 401 mit Basic, Digest und NTLM zur Auswahl</li></ul>
<h2>GraphQL</h2><ul><li><code>POST /graphql</code>, <code>GET /graphql?query=…</code></li></ul>
<p>HTTPS (selbst signiert): Port 8443. Proxy: Port 8089.</p>
"""


def log(text):
    print(time.strftime("%H:%M:%S"), text, flush=True)


class Handler(BaseHTTPRequestHandler):
    # HTTP/1.1 mit Keep-Alive: NTLM braucht beide Runden auf derselben Verbindung.
    protocol_version = "HTTP/1.1"
    server_version = "LatherIt-Testserver"

    def log_message(self, format, *args):
        pass

    # ---------- Grundlagen ----------

    def handle_any(self):
        self.connection_id = getattr(self, "connection_id", secrets.token_hex(3))
        url = urlsplit(self.path)
        self.url_path, self.raw_query = url.path, url.query
        self.query = {k: v[0] if len(v) == 1 else v for k, v in parse_qs(url.query, keep_blank_values=True).items()}
        length = int(self.headers.get("Content-Length") or 0)
        self.body = self.rfile.read(length) if length else b""
        log(f"[{self.connection_id}] {self.command} {self.path}  ({len(self.body)} Bytes Body)")
        if self.headers.get("Authorization"):
            log(f"[{self.connection_id}]   Authorization: {self.headers['Authorization'][:70]}")
        try:
            self.route()
        except BrokenPipeError:
            pass

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = do_OPTIONS = handle_any

    def reply(self, status, body=b"", content_type="text/plain; charset=utf-8", headers=()):
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(status)
        if body or status not in (204, 304):
            self.send_header("Content-Type", content_type)
        for name, value in headers:
            self.send_header(name, value)
        self.send_header("Content-Length", "0" if self.command == "HEAD" else str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def json(self, status, data, headers=()):
        self.reply(status, json.dumps(data, ensure_ascii=False, indent=2), "application/json; charset=utf-8", headers)

    def text(self):
        return self.body.decode("utf-8", "replace")

    def route(self):
        p = self.url_path
        if p == "/" and self.command == "GET":
            return self.reply(200, INDEX, "text/html; charset=utf-8")
        if p in ("/shop", "/shop12"):
            if self.command == "GET" and "wsdl" in self.query:
                return self.file("wsdl/shop.wsdl", "text/xml; charset=utf-8")
            return self.soap(p == "/shop12")
        if p.startswith("/xsd/"):
            return self.file("wsdl/xsd/" + os.path.basename(p), "text/xml; charset=utf-8")
        if p.startswith("/api/"):
            return self.rest(p[len("/api"):])
        if p.startswith("/http/"):
            return self.http(p[len("/http"):])
        if p.startswith("/auth/"):
            return self.auth(p[len("/auth/"):])
        if p == "/graphql":
            return self.graphql()
        self.reply(404, f"Unbekannt: {self.command} {p}\n")

    def file(self, relative, content_type):
        path = os.path.join(HERE, relative)
        if not os.path.isfile(path):
            return self.reply(404, f"Nicht da: {relative}\n")
        with open(path, "rb") as f:
            self.reply(200, f.read(), content_type)

    # ---------- SOAP ----------

    def soap(self, v12):
        ns = SOAP12 if v12 else SOAP11
        content_type = "application/soap+xml; charset=utf-8" if v12 else "text/xml; charset=utf-8"
        body = self.text()
        action = (self.headers.get("SOAPAction") or "").strip('"')
        if v12:
            match = re.search(r'action="([^"]+)"', self.headers.get("Content-Type", ""))
            action = match.group(1) if match else action

        def envelope(content):
            return (f'<?xml version="1.0" encoding="UTF-8"?>\n<soap:Envelope xmlns:soap="{ns}">\n'
                    f"  <soap:Body>\n{content}\n  </soap:Body>\n</soap:Envelope>\n")

        def fault(code, text):
            if v12:
                content = (f"    <soap:Fault><soap:Code><soap:Value>soap:{code}</soap:Value></soap:Code>"
                           f'<soap:Reason><soap:Text xml:lang="de">{text}</soap:Text></soap:Reason></soap:Fault>')
            else:
                content = f"    <soap:Fault><faultcode>soap:{code}</faultcode><faultstring>{text}</faultstring></soap:Fault>"
            self.reply(500, envelope(content), content_type)

        if "Envelope" not in body:
            return fault("Client" if not v12 else "Sender", "Kein SOAP-Envelope im Request")
        if action.endswith("Ping") or re.search(r"<(\w+:)?Ping\b", body):
            return self.reply(200, envelope('    <PingResponse xmlns="http://example.com/shop/types"/>'), content_type)
        if action.endswith("GetOrder") or re.search(r"<(\w+:)?GetOrder\b", body):
            session = re.search(r"<(\w+:)?Session>([^<]*)<", body)
            if session and session.group(2) == "abgelaufen":
                return fault("Client" if not v12 else "Sender", "Session abgelaufen")
            order_id = re.search(r"<(\w+:)?orderId>\s*([^<\s]*)\s*<", body)
            order_id = order_id.group(2) if order_id else ""
            if order_id in ("", "0") or not order_id.isdigit():
                return fault("Client" if not v12 else "Sender", f"Bestellung „{order_id}“ nicht gefunden")
            user = "anonym" if not session else session.group(2) or "anonym"
            return self.reply(200, envelope(
                f'    <shop:GetOrderResponse xmlns:shop="http://example.com/shop/types" createdBy="{user}">\n'
                f"      <shop:id>{order_id}</shop:id>\n"
                f'      <shop:amount currency="EUR">23.80</shop:amount>\n'
                f"    </shop:GetOrderResponse>"), content_type)
        fault("Client" if not v12 else "Sender", f"Unbekannte Operation (SOAPAction „{action}“)")

    # ---------- REST ----------

    def rest(self, p):
        if p.startswith("/echo"):
            return self.echo()
        if p == "/orders":
            if self.command == "GET":
                status = self.query.get("status")
                orders = [o for o in ORDERS.values() if not status or o["status"] == status]
                return self.json(200, {"orders": orders, "count": len(orders)})
            if self.command == "POST":
                try:
                    data = json.loads(self.text() or "{}")
                except ValueError as e:
                    return self.json(400, {"error": f"Kein gültiges JSON: {e}"})
                new_id = str(4714 + len(ORDERS))
                return self.json(201, {"id": new_id, "status": "open", "received": data},
                                 [("Location", f"/api/orders/{new_id}")])
        match = re.fullmatch(r"/orders/([^/;]+)", p)
        if match:
            order = ORDERS.get(unquote(match.group(1)))
            if order is None:
                return self.json(404, {"error": f"Bestellung {match.group(1)} nicht gefunden"})
            if self.command in ("GET", "HEAD"):
                return self.json(200, order)
            if self.command in ("PUT", "PATCH"):
                return self.json(200, {**order, "updatedWith": self.text(), "method": self.command})
            if self.command == "DELETE":
                if self.body:
                    return self.json(200, {"deleted": order["id"], "bodyReceived": self.text()})
                return self.reply(204)
        self.json(405 if p.startswith("/orders") else 404, {"error": f"{self.command} {p} gibt es nicht"})

    def echo(self):
        segments = self.url_path.split("/")
        matrix = {}
        for segment in segments:
            for part in segment.split(";")[1:]:
                name, _, value = part.partition("=")
                matrix[unquote(name)] = unquote(value)
        self.json(200, {
            "method": self.command,
            "path": self.url_path,
            "rawQuery": self.raw_query,
            "query": self.query,
            "matrix": matrix,
            "headers": [[k, v] for k, v in self.headers.items()],
            "body": self.text(),
            "bodyBytes": len(self.body),
            "connection": self.connection_id,
        })

    # ---------- HTTP ----------

    def http(self, p):
        if m := re.fullmatch(r"/status/(\d{3})", p):
            code = int(m.group(1))
            return self.reply(code, b"" if code in (204, 304) else f"Status {code}\n")
        if m := re.fullmatch(r"/delay/(\d+)", p):
            time.sleep(int(m.group(1)) / 1000)
            return self.reply(200, f"Nach {m.group(1)} ms\n")
        if m := re.fullmatch(r"/redirect/(\d+)", p):
            n = int(m.group(1))
            target = f"/http/redirect/{n - 1}" if n > 1 else "/api/echo/ziel"
            return self.reply(302, b"", headers=[("Location", target)])
        if p == "/redirect-post":
            return self.reply(303, b"", headers=[("Location", "/api/echo/nach-303")])
        if p == "/charset/latin1":
            return self.reply(200, "Grüße aus Köln – äöüß\n".replace("–", "-").encode("latin-1"), "text/plain; charset=ISO-8859-1")
        if p == "/charset/utf16bom":
            return self.reply(200, "﻿UTF-16 mit BOM: äöüß €\n".encode("utf-16-be"), "text/plain")
        if p == "/charset/ohne-angabe":
            return self.reply(200, "<a>Grüße äöü</a>\n".encode("utf-8"), "text/xml")
        if p == "/big":
            return self.big()
        if p == "/headers":
            self.send_response(200)
            for cookie in ("sitzung=abc123; Path=/; HttpOnly", "sprache=de; Path=/"):
                self.send_header("Set-Cookie", cookie)
            self.send_header("X-Lang", "x" * 4000)
            self.send_header("X-Umlaut", "Gruesse")
            body = b"Siehe Header\n"
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return self.wfile.write(body)
        if p == "/gzip":
            data = gzip.compress("Dieser Text kam gzip-komprimiert an, obwohl niemand danach gefragt hat.\n".encode())
            return self.reply(200, data, "text/plain; charset=utf-8", [("Content-Encoding", "gzip")])
        if p == "/empty":
            return self.reply(200, b"")
        if p == "/html":
            return self.reply(200, "<!doctype html><html><body><h1>Keine API</h1><p>Sondern HTML</p></body></html>",
                              "text/html; charset=utf-8")
        if p == "/binary":
            return self.reply(200, bytes(range(256)) * 4, "application/octet-stream")
        if p == "/abbruch":
            self.close_connection = True
            self.connection.shutdown(socket.SHUT_RDWR)
            return
        self.reply(404, f"Unbekannt: {p}\n")

    def big(self):
        mb = float(self.query.get("mb", "5"))
        as_json = self.query.get("format") == "json"
        target = int(mb * 1024 * 1024)
        parts, size, i = [], 0, 0
        while size < target:
            i += 1
            part = (f'{{"nr": {i}, "sku": "ART-{i:06d}", "name": "Artikel {i} mit Umlauten äöü", "preis": {i % 97}.95}}'
                    if as_json else
                    f'  <artikel nr="{i}"><sku>ART-{i:06d}</sku><name>Artikel {i} mit Umlauten äöü</name>'
                    f"<preis>{i % 97}.95</preis></artikel>\n")
            parts.append(part)
            size += len(part.encode())
        if as_json:
            return self.reply(200, '{"artikel": [\n' + ",\n".join(parts) + "\n]}\n", "application/json; charset=utf-8")
        self.reply(200, '<?xml version="1.0" encoding="UTF-8"?>\n<katalog>\n' + "".join(parts) + "</katalog>\n",
                   "text/xml; charset=utf-8")

    # ---------- Anmeldung ----------

    def auth(self, kind):
        header = self.headers.get("Authorization") or ""
        if kind == "basic":
            expected = "Basic " + base64.b64encode(f"{BASIC_USER}:{BASIC_PASSWORD}".encode()).decode()
            if header == expected:
                return self.json(200, {"ok": True, "verfahren": "Basic", "benutzer": BASIC_USER})
            return self.reply(401, "Basic: anna / geheim\n", headers=[("WWW-Authenticate", f'Basic realm="{DIGEST_REALM}"')])
        if kind == "bearer":
            if header == f"Bearer {BEARER_TOKEN}":
                return self.json(200, {"ok": True, "verfahren": "Bearer"})
            return self.reply(401, f"Bearer-Token: {BEARER_TOKEN}\n",
                              headers=[("WWW-Authenticate", 'Bearer realm="api", error="invalid_token"')])
        if kind == "digest":
            return self.digest(header)
        if kind == "ntlm":
            return self.ntlm(header)
        if kind == "alle":
            if header:
                return self.json(200, {"ok": True, "authorization": header.split(" ")[0]})
            self.send_response(401)
            for value in (f'Basic realm="{DIGEST_REALM}"', f'Digest realm="{DIGEST_REALM}", nonce="{secrets.token_hex(8)}", qop="auth"', "NTLM"):
                self.send_header("WWW-Authenticate", value)
            self.send_header("Content-Length", "0")
            return self.end_headers()
        self.reply(404, f"Unbekannt: {kind}\n")

    def digest(self, header):
        if header.startswith("Digest "):
            params = {k: a or b for k, a, b in re.findall(r'(\w+)=(?:"([^"]*)"|([^,\s]*))', header[7:])}
            ha1 = hashlib.md5(f"{BASIC_USER}:{DIGEST_REALM}:{BASIC_PASSWORD}".encode()).hexdigest()
            ha2 = hashlib.md5(f"{self.command}:{params.get('uri', '')}".encode()).hexdigest()
            expected = hashlib.md5(
                f"{ha1}:{params.get('nonce')}:{params.get('nc')}:{params.get('cnonce')}:{params.get('qop')}:{ha2}".encode()
            ).hexdigest()
            if params.get("username") == BASIC_USER and params.get("response") == expected:
                return self.json(200, {"ok": True, "verfahren": "Digest", "benutzer": BASIC_USER})
            log(f"[{self.connection_id}]   Digest falsch (Benutzer {params.get('username')!r})")
        nonce = secrets.token_hex(16)
        self.reply(401, "Digest: anna / geheim\n", headers=[
            ("WWW-Authenticate", f'Digest realm="{DIGEST_REALM}", nonce="{nonce}", qop="auth", algorithm=MD5, opaque="abc"')])

    def ntlm(self, header):
        """Spielt den NTLM-Ablauf nach (ohne das Passwort zu prüfen): Typ 1 → Challenge → Typ 3 auf derselben Verbindung."""
        token = header[5:] if header.startswith("NTLM ") else ""
        message = base64.b64decode(token) if token else b""
        kind = struct.unpack("<I", message[8:12])[0] if len(message) >= 12 and message[:8] == b"NTLMSSP\0" else 0
        if kind == 1:
            self.ntlm_challenge = True
            domain = "TESTDOMAENE".encode("utf-16-le")
            info = struct.pack("<HH", 2, len(domain)) + domain
            info += struct.pack("<HH", 7, 8) + struct.pack("<Q", (int(time.time()) + 11644473600) * 10_000_000)
            info += struct.pack("<HH", 0, 0)
            offset = 48 + len(domain)
            challenge = (b"NTLMSSP\0" + struct.pack("<I", 2) + struct.pack("<HHI", len(domain), len(domain), 48)
                         + struct.pack("<I", 0x628A8215) + secrets.token_bytes(8) + b"\0" * 8
                         + struct.pack("<HHI", len(info), len(info), offset) + domain + info)
            return self.reply(401, b"", headers=[("WWW-Authenticate", "NTLM " + base64.b64encode(challenge).decode())])
        if kind == 3:
            if not getattr(self, "ntlm_challenge", False):
                return self.reply(401, "NTLM: Typ 3 kam über eine andere Verbindung als die Challenge\n",
                                  headers=[("WWW-Authenticate", "NTLM")])

            def field(position):
                length, _, start = struct.unpack("<HHI", message[position:position + 8])
                return message[start:start + length].decode("utf-16-le", "replace")
            return self.json(200, {"ok": True, "verfahren": "NTLM", "domaene": field(28), "benutzer": field(36),
                                   "rechner": field(44), "hinweis": "Passwort wird hier nicht geprüft"})
        self.reply(401, "NTLM\n", headers=[("WWW-Authenticate", "NTLM")])

    # ---------- GraphQL ----------

    def graphql(self):
        if self.command == "GET":
            query, variables = self.query.get("query", ""), json.loads(self.query.get("variables", "{}") or "{}")
        else:
            try:
                data = json.loads(self.text() or "{}")
            except ValueError as e:
                return self.json(400, {"errors": [{"message": f"Kein gültiges JSON: {e}"}]})
            query, variables = data.get("query") or "", data.get("variables") or {}
        if not query:
            return self.json(400, {"errors": [{"message": "Feld „query“ fehlt"}]})
        if query.count("{") != query.count("}"):
            return self.json(200, {"errors": [{"message": "Syntax Error: Klammern passen nicht", "locations": [{"line": 1, "column": 1}]}]})
        if "mutation" in query:
            # Wert des Arguments customer: direkt im Query oder über eine Variable ($c, $customer, …)
            arg = re.search(r'customer\s*:\s*(?:\$(\w+)|"([^"]*)")', query)
            customer = (variables.get(arg.group(1)) if arg.group(1) else arg.group(2)) if arg else variables.get("customer")
            return self.json(200, {"data": {"createOrder": {"id": "4720", "customer": customer, "status": "open"}}})
        if re.search(r"\border\s*\(", query):
            order = ORDERS.get(str(variables.get("id", "")))
            if order is None:
                return self.json(200, {"data": {"order": None},
                                       "errors": [{"message": f"Bestellung {variables.get('id')} nicht gefunden", "path": ["order"]}]})
            return self.json(200, {"data": {"order": {k: order[k] for k in ("id", "customer", "status", "total")}}})
        if re.search(r"\borders\b", query):
            return self.json(200, {"data": {"orders": [{"id": o["id"], "status": o["status"]} for o in ORDERS.values()]}})
        self.json(200, {"errors": [{"message": "Unbekanntes Feld (bekannt: order(id), orders, mutation createOrder)"}]})


# ---------- Proxy ----------

class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    credentials = None

    def log_message(self, format, *args):
        pass

    def authorized(self):
        if not self.credentials:
            return True
        if self.headers.get("Proxy-Authorization") == "Basic " + base64.b64encode(self.credentials.encode()).decode():
            return True
        log(f"[Proxy] 407 für {self.command} {self.path}")
        self.send_response(407)
        self.send_header("Proxy-Authenticate", 'Basic realm="LatherIt-Testproxy"')
        self.send_header("Content-Length", "0")
        self.end_headers()
        return False

    def do_CONNECT(self):
        if not self.authorized():
            return
        host, _, port = self.path.partition(":")
        log(f"[Proxy] CONNECT {self.path}")
        try:
            upstream = socket.create_connection((host, int(port or 443)), timeout=10)
        except OSError as e:
            self.send_error(502, str(e))
            return
        self.send_response(200, "Connection established")
        self.end_headers()
        sockets = [self.connection, upstream]
        while True:
            readable, _, _ = select.select(sockets, [], [], 30)
            if not readable:
                break
            for s in readable:
                data = s.recv(65536)
                if not data:
                    upstream.close()
                    return
                (upstream if s is self.connection else self.connection).sendall(data)

    def forward(self):
        if not self.authorized():
            return
        log(f"[Proxy] {self.command} {self.path}")
        url = urlsplit(self.path)
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        upstream = socket.create_connection((url.hostname, url.port or 80), timeout=30)
        target = url.path + ("?" + url.query if url.query else "")
        lines = [f"{self.command} {target or '/'} HTTP/1.1"]
        for name, value in self.headers.items():
            if name.lower() not in ("proxy-authorization", "proxy-connection", "connection"):
                lines.append(f"{name}: {value}")
        lines.append("X-Via-Proxy: LatherIt-Testproxy")
        lines.append("Connection: close")
        upstream.sendall(("\r\n".join(lines) + "\r\n\r\n").encode("latin-1") + body)
        chunks = []
        while chunk := upstream.recv(65536):
            chunks.append(chunk)
        upstream.close()
        self.connection.sendall(b"".join(chunks))
        self.close_connection = True

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = forward


def serve(server, label):
    log(f"{label} läuft")
    server.serve_forever()


def main():
    parser = argparse.ArgumentParser(description="Testserver für die LatherIt-Handtests")
    parser.add_argument("--host", default="127.0.0.1", help="0.0.0.0 = auch aus dem Netzwerk (z. B. für die Test-VMs)")
    parser.add_argument("--proxy-auth", action="store_true", help="Proxy verlangt Basic-Anmeldung proxy / proxy")
    args = parser.parse_args()

    threads = []
    for port in (8088, 8090):
        server = ThreadingHTTPServer((args.host, port), Handler)
        threads.append(threading.Thread(target=serve, args=(server, f"HTTP  http://{args.host}:{port}/"), daemon=True))

    cert = os.path.join(HERE, "zertifikat", "localhost.pem")
    key = os.path.join(HERE, "zertifikat", "localhost-key.pem")
    if os.path.exists(cert):
        https = ThreadingHTTPServer((args.host, 8443), Handler)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert, key)
        https.socket = context.wrap_socket(https.socket, server_side=True)
        threads.append(threading.Thread(target=serve, args=(https, f"HTTPS https://localhost:8443/ (selbst signiert)"), daemon=True))

    if args.proxy_auth:
        ProxyHandler.credentials = "proxy:proxy"
    proxy = ThreadingHTTPServer((args.host, 8089), ProxyHandler)
    label = "Proxy " + f"{args.host}:8089" + (" (Anmeldung proxy / proxy)" if args.proxy_auth else "")
    threads.append(threading.Thread(target=serve, args=(proxy, label), daemon=True))

    for thread in threads:
        thread.start()
    log("Beenden mit Strg+C")
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        sys.exit(0)


if __name__ == "__main__":
    main()
