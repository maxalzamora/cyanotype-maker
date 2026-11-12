#!/usr/bin/env python3
"""End-to-end smoke test for a running server.

    ./gradlew :app:bootRun          # in one shell
    python3 scripts/smoke.py photo.jpg

Posts an image to /api/render as PNG, TIFF and PDF, checks that the bytes really are
those formats, and prints the step-wedge self-check. Uses only the standard library so it
runs anywhere Python does.
"""

import json
import os
import sys
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("CYANOTYPES_URL", "http://localhost:8080")


def post_file(path, **fields):
    boundary = uuid.uuid4().hex
    with open(path, "rb") as handle:
        payload = handle.read()
    name = os.path.basename(path)

    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{name}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode() + payload

    for key, value in fields.items():
        body += (
            f"\r\n--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{key}"\r\n\r\n'
            f"{value}"
        ).encode()
    body += f"\r\n--{boundary}--\r\n".encode()

    request = urllib.request.Request(
        BASE + "/api/render",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    return urllib.request.urlopen(request, timeout=120)


def check(source, fmt, signature, headers, **fields):
    stem = os.path.splitext(os.path.basename(source))[0]
    target = f"{stem}-negative.{fmt}"

    with post_file(source, format=fmt, **fields) as response:
        data = response.read()
        if not data.startswith(signature):
            print(f"FAIL: {fmt} response does not start with {signature!r}")
            return False
        with open(target, "wb") as handle:
            handle.write(data)
        print(f"ok  {fmt:4}  {response.status}  {len(data)} bytes  -> {target}")
        for header in headers:
            value = response.headers.get(header)
            if value is None:
                print(f"FAIL: {fmt} response is missing {header}")
                return False
            print(f"          {header}: {value}")
    return True


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    source = argv[1]

    ok = check(source, "png", b"\x89PNG",
               ("X-Source-Format", "X-Source-Width", "X-Source-Height", "X-Profile"))
    ok &= check(source, "tiff", b"II*\x00",
                ("X-Paper", "X-Effective-Ppi", "X-Print-Width-Mm", "X-Print-Height-Mm"),
                dpi="150")
    ok &= check(source, "pdf", b"%PDF",
                ("X-Paper", "X-Effective-Ppi", "X-Mirrored"), dpi="150")

    with urllib.request.urlopen(BASE + "/api/diagnostics/step-wedge", timeout=30) as response:
        report = json.load(response)
    print(f"step wedge: {report['steps']} steps, all correct = {report['allCorrect']}")
    if not report["allCorrect"]:
        for row in report["rows"]:
            if not row["ok"]:
                print("  mismatch:", row)
        ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
