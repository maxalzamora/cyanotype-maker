#!/usr/bin/env python3
"""End-to-end smoke test for a running server.

    ./gradlew :app:bootRun          # in one shell
    python3 scripts/smoke.py photo.jpg

Posts an image to /api/render, checks the response is a real PNG, writes it next to
the input, and prints the step-wedge self-check. Uses only the standard library so it
runs anywhere Python does.
"""

import json
import os
import sys
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("CYANOTYPES_URL", "http://localhost:8080")


def post_file(path):
    boundary = uuid.uuid4().hex
    with open(path, "rb") as handle:
        payload = handle.read()
    name = os.path.basename(path)
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{name}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode() + payload + f"\r\n--{boundary}--\r\n".encode()

    request = urllib.request.Request(
        BASE + "/api/render",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    return urllib.request.urlopen(request, timeout=120)


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    source = argv[1]

    with post_file(source) as response:
        data = response.read()
        if data[:4] != b"\x89PNG":
            print("FAIL: response is not a PNG")
            return 1
        stem = os.path.splitext(os.path.basename(source))[0]
        target = f"{stem}-negative.png"
        with open(target, "wb") as handle:
            handle.write(data)
        print(f"ok  {response.status}  {len(data)} bytes")
        for header in ("X-Source-Format", "X-Source-Width", "X-Source-Height"):
            print(f"    {header}: {response.headers.get(header)}")
        print(f"    wrote {target}")

    with urllib.request.urlopen(BASE + "/api/diagnostics/step-wedge", timeout=30) as response:
        report = json.load(response)
    print(f"step wedge: {report['steps']} steps, all correct = {report['allCorrect']}")
    if not report["allCorrect"]:
        for row in report["rows"]:
            if not row["ok"]:
                print("  mismatch:", row)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
