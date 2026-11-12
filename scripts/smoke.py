#!/usr/bin/env python3
"""End-to-end smoke test for a running server.

    ./gradlew :app:bootRun          # in one shell
    python3 scripts/smoke.py photo.jpg

Posts an image to /api/render as PNG, TIFF and PDF, checks that the bytes really are
those formats, checks the cyanotype preview, prints the step-wedge self-check, and runs
the calibration loop end to end with the simulated scan. Uses only the standard library
so it runs anywhere Python does.
"""

import json
import os
import sys
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("CYANOTYPES_URL", "http://localhost:8080")


def post_file(path, endpoint="/api/render", **fields):
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
        BASE + endpoint,
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


def check_preview(source):
    """The negative coloured as the print: PNG, 8-bit RGB, capped for the screen."""
    with post_file(source, endpoint="/api/preview", profile="cyanotype") as response:
        data = response.read()
        width = int(response.headers.get("X-Preview-Width", "0"))
        height = int(response.headers.get("X-Preview-Height", "0"))
        profile = response.headers.get("X-Profile")
    if not data.startswith(b"\x89PNG"):
        print("FAIL: preview is not a PNG")
        return False
    if not (0 < width <= 1400 and 0 < height <= 1400):
        print(f"FAIL: preview size {width}x{height} is outside the 1400 px cap")
        return False
    # IHDR: bit depth at byte 24, colour type at byte 25; 2 is truecolour.
    if data[24] != 8 or data[25] != 2:
        print(f"FAIL: preview is not 8-bit RGB (depth={data[24]}, colour={data[25]})")
        return False
    print(f"ok  preview {width}x{height} RGB, {len(data)} bytes, profile {profile}")
    return True


def check_calibration():
    """Print target → synthetic scan → measured profile → usable as a profile."""
    with urllib.request.urlopen(
            BASE + "/api/calibration/target?format=pdf", timeout=60) as response:
        target = response.read()
        patches = response.headers.get("X-Calibration-Patches")
    if not target.startswith(b"%PDF") or patches != "21":
        print(f"FAIL: calibration target is not a 21-bar PDF (patches={patches})")
        return False
    print(f"ok  target  21 bars, {len(target)} bytes")

    scan_path = "simulated-scan.png"
    with urllib.request.urlopen(
            BASE + "/api/calibration/simulated-scan.png", timeout=60) as response:
        with open(scan_path, "wb") as handle:
            handle.write(response.read())
    print(f"ok  scan    {scan_path}")

    with post_file(scan_path, endpoint="/api/calibration", name="smoke test") as response:
        report = json.load(response)
    lut = report["lut"]
    if len(report["patches"]) != 21 or len(lut) != 256:
        print("FAIL: calibration returned the wrong shape")
        return False
    if any(lut[i] > lut[i - 1] for i in range(1, len(lut))):
        print("FAIL: calibrated LUT is not monotone")
        return False
    print(f"ok  profile {report['profileId']}  range "
          f"{report['measuredDensityRange']:.2f}  lut 0->{lut[0]} 128->{lut[128]} 255->{lut[-1]}")

    with urllib.request.urlopen(BASE + "/api/profiles", timeout=30) as response:
        ids = [profile["id"] for profile in json.load(response)]
    if report["profileId"] not in ids:
        print("FAIL: the new profile is not offered by /api/profiles")
        return False
    print(f"ok  catalog {len(ids)} profiles, including the measurement")
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
    ok &= check_preview(source)

    with urllib.request.urlopen(BASE + "/api/diagnostics/step-wedge", timeout=30) as response:
        report = json.load(response)
    print(f"step wedge: {report['steps']} steps, all correct = {report['allCorrect']}")
    if not report["allCorrect"]:
        for row in report["rows"]:
            if not row["ok"]:
                print("  mismatch:", row)
        ok = False

    ok &= check_calibration()
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
