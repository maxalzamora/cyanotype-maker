# cyanotypes

Upload a photograph, get back the **digital negative** you print on transparency for a
cyanotype contact print.

Cyanotype is a negative process: the paper turns Prussian blue where UV reaches it, so to
reproduce a photograph you expose through an inverted image. This service produces that
inverted image, shaped for what the process can actually print.

> **Status: stage 2.** Perceptual conversion, density mapping, process profiles and a
> monotone tone curve. Calibration from measured prints, physical sizing, 16-bit TIFF and
> PDF export are not built yet — see [Not built yet](#not-built-yet).

In the output file a **high** value means clear film: UV passes, the paper turns blue, the
print is dark. So the negative looks like a photographic negative, which is the point.

## Why it is not `255 - pixel`

Naive inversion produces bad negatives. Four things are handled here:

- **Luminance in linear light.** Averaging gamma-encoded channels is not averaging light.
  Pixels are decoded to linear, mixed with Rec. 709 weights, and re-encoded. Pure red
  becomes grey 127, not 85.
- **Alpha and colour model normalisation.** Anything (ARGB, indexed, CMYK) is flattened
  onto white before processing, so the result does not depend on the encoder that produced
  the upload.
- **`TYPE_BYTE_GRAY` is a trap.** It carries a *linear* colour model, so `setRGB` would
  apply the transfer function a second time and wash the negative out. Samples are written
  straight to the raster instead.
- **Density, not value.** Equal steps of screen grey are not equal steps of ink. The
  `cyanotype` profile converts a source value to optical density
  (`D = -log10(reflectance)`) and scales it into the density range the paper can reach.
  That is why mid-grey lands on 121 rather than 127, and why shadows separate instead of
  collapsing.

## Profiles

| id | mapping | what it is for |
|---|---|---|
| `linear` | LINEAR | Stage 1 behaviour. Exact inversion, used as a reference and for exact tests. |
| `cyanotype` | DENSITY | Print density matched to source density, Dmax 1.4. The default. |
| `cyanotype-soft` | DENSITY | Same process, rendered flatter via a tone curve so shadows do not sit on Dmax. |

A profile is more than a preset: it carries the print density range (`printDmin`,
`printDmax`), output levels (`blackLevel`, `whiteLevel` — heavy ink laydown can coalesce on
transparency film), and a `ToneCurve`. Curves use Fritsch–Carlson tangents, so they cannot
overshoot; an overshoot in a tone curve shows up as a contour in the print.

Deep shadows do clip in the density profiles, at reflectance below about 4%. This is
physical: a print cannot be denser than its Dmax. `cyanotype-soft` exists to show the
workaround — give up both ends deliberately rather than letting the bottom block up.

## Run

Needs JDK 21. No database, no external services.

```bash
./gradlew :app:bootRun                 # http://localhost:8080
PORT=3000 ./scripts/run.sh             # background on 3000, survives the shell
./scripts/run.sh stop
```

Or with Docker:

```bash
docker compose up --build              # http://localhost:3000
```

`PORT` is honoured everywhere; the container image sets it to 3000 in `compose.yaml`.

```bash
./gradlew test                         # the interesting part
```

## API

`POST /api/render` — `multipart/form-data`:

- `file` — the photograph (required)
- `profile` — `linear`, `cyanotype` or `cyanotype-soft` (default `cyanotype`)

| | |
|---|---|
| `200` | `image/png`, plus `X-Profile`, `X-Source-Format`, `X-Source-Width`, `X-Source-Height` |
| `400` | nothing uploaded, or unknown profile |
| `413` | over 25 MB, or over 80 MP / 20000 px per side |
| `415` | magic bytes are not PNG/JPEG/TIFF/GIF |
| `422` | recognised format, but the bytes do not decode |

The declared `Content-Type` is ignored. Format is decided by magic bytes, and dimensions
are read from the header **before** pixels are allocated — a 40-byte PNG claiming
30000×30000 is rejected instead of consuming 2.7 GB.

Other endpoints:

- `GET /api/profiles` — the profiles the UI selector is built from.
- `GET /api/diagnostics/step-wedge?profile=…` — renders a 21-step ramp and reports
  expected vs actual, plus monotonicity and the two extremes.
- `GET /api/diagnostics/step-wedge.png?profile=…` — the same wedge as an image.
- `GET /health`

Smoke test against a running server:

```bash
python3 scripts/smoke.py photo.jpg
```

## Verify a render

`GET /api/diagnostics/step-wedge?profile=…` asserts every row and returns
`"allCorrect": true`. Mid-grey is the number to check:

| profile | source 0 | source 128 | source 255 |
|---|---|---|---|
| `linear` | 255 | 127 | 0 |
| `cyanotype` | 255 | 121 | 0 |
| `cyanotype-soft` | 230 | 123 | 26 |

Two failure modes this catches immediately:

- **127 under `cyanotype`** — the density mapping is not running; you are back to stage 1.
- **186 under `linear` at source 128** — gamma applied twice.

In the test suite, `NegativeRendererTest` checks all 256 greyscale values under `linear`,
and `NegativeLutTest` pins the density numbers with hard-coded expectations.

## Layout

```
imaging/     pure Java, no Spring on the classpath
  ImageFormat        magic-byte sniffing
  ImageInspector     header validation, decompression-bomb guard
  Srgb               transfer function + luminance
  ToneCurve          monotone cubic interpolation
  ProcessProfile     print range, output levels, curve
  NegativeLut        the whole 8-bit transform as a table
  NegativeRenderer   applies the LUT, writes a greyscale raster
  PngEncoder         8-bit greyscale output
  NegativePipeline   inspect → decode → render → encode
  StepWedge          known-input test fixture
app/         Spring Boot
  web/               HTTP surface and error mapping
  service/           orchestration
  config/            property binding, imaging beans
```

`imaging` has no framework dependency by construction, and the Gradle module boundary
enforces it.

## Not built yet

In the order they will be added:

1. Calibration: emit a step-wedge target, read measured densities back, fit a curve, store
   it as a versioned process profile. This replaces the assumed `printDmax` with a measured
   one and turns the tone curve into a real characteristic curve.
2. Physical sizing and margins, PDF output, optional mirroring for one-sided film.
3. 16-bit TIFF with an embedded Gray Gamma 2.2 ICC profile, and proper DPI metadata.
4. A job queue for large renders, with retries and idempotency.
