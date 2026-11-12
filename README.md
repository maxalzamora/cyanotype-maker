# cyanotypes

Upload a photograph, get back the **digital negative** you print on transparency for a
cyanotype contact print.

Cyanotype is a negative process: the paper turns Prussian blue where UV reaches it, so to
reproduce a photograph you expose through an inverted image. This service produces that
inverted image, shaped for what the process can actually print.

> **Status: stage 3.** Perceptual conversion, density mapping, process profiles, a monotone
> tone curve, and print-ready export (16-bit TIFF and a page-sized PDF). Calibration from
> measured prints is not built yet — see [Not built yet](#not-built-yet).

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

## Print files

A preview is not a print file. Three outputs, for three jobs:

| format | what it is | what it is for |
|---|---|---|
| `png` | 8-bit greyscale at the photograph's own resolution | checking the negative on screen |
| `tiff` | 16-bit greyscale, uncompressed, resolution tag, Grey Gamma 2.2 embedded | the file you send to the printer |
| `pdf` | one page of the chosen paper, image centred inside the margins | printing without having to set a size |

The TIFF is written by hand rather than through `ImageIO`, because the tags that matter
here — resolution unit and ICC profile — are exactly the ones the metadata API treats as an
afterthought. It is the baseline subset of TIFF 6.0: one IFD, strips, `BlackIsZero`,
`SampleFormat = unsigned`.

The ICC profile is **generated**, not bundled. Java's built-in `CS_GRAY` profile is
*linear*: converting 0.5 through it yields sRGB 0.735. Tagging that as "Gamma 2.2" would
shift every tone in the print, so `GrayProfile` writes a valid ICC v2 monitor profile from
the spec — `desc`, `wtpt` at D50, and a `kTRC` gamma curve. LittleCMS opens it and reports
"Gray Gamma 2.2".

16 bits is not decoration: the tone curve and the resampling both produce intermediate
values, so carrying 16 bits to the file avoids quantising them twice. PDF gets 8 bits
because 16-bit grey is inconsistently handled across viewers; the TIFF is the archival file.

`dpi` means pixels per inch **on paper** — the negative is resampled to that density for
the requested stock, not merely tagged with it. A 3:2 photograph on A4 with 10 mm margins
at 300 ppi comes out 2244 × 1496 px, drawn 190.0 × 126.7 mm. The response reports the
effective value (`X-Effective-Ppi`) because fitting to whole pixels rounds it slightly.

`mirror=true` flips the image. It is needed when the film is coated on one side and must
face the paper ink-down: you then look through the base, which mirrors the image.

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
- `format` — `png`, `tiff` or `pdf` (default `png`)
- `paper` — `a4`, `a3`, `letter` or `inch_8x10` (default `a4`; print formats only)
- `margin` — millimetres (default `10`)
- `dpi` — pixels per inch on paper, 150–2400 (default `600`)
- `mirror` — `true` or `false` (default `false`)

| | |
|---|---|
| `200` | the file, with `X-Profile`; previews also carry `X-Source-Format`, `X-Source-Width`, `X-Source-Height`; print files carry `X-Paper`, `X-Margin-Mm`, `X-Effective-Ppi`, `X-Print-Width-Mm`, `X-Print-Height-Mm`, `X-Mirrored` |
| `400` | nothing uploaded, unknown profile, unknown format, unknown paper, or an impossible margin/dpi |
| `413` | over 25 MB, over 80 MP / 20000 px per side, or an export that would exceed 200 MP |
| `415` | magic bytes are not PNG/JPEG/TIFF/GIF |
| `422` | recognised format, but the bytes do not decode |
| `500` | the upload was fine but the encoder failed (`EXPORT_FAILED`) |

The declared `Content-Type` is ignored. Format is decided by magic bytes, and dimensions
are read from the header **before** pixels are allocated — a 40-byte PNG claiming
30000×30000 is rejected instead of consuming 2.7 GB.

Other endpoints:

- `GET /api/profiles` — the profiles the UI selector is built from.
- `GET /api/papers` — paper sizes in millimetres.
- `GET /api/defaults` — what the form starts with.
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

For the print files, the checks are structural and can be repeated with any TIFF or PDF
reader:

```bash
# 16 bits, BlackIsZero, 300 dpi, embedded Grey Gamma 2.2
python3 -c "from PIL import Image; im=Image.open('negative.tiff'); \
  print(im.mode, im.size, im.tag_v2[258], im.tag_v2[282], len(im.tag_v2[34675]))"
# I;16 (2244, 1496) (16,) 300.0 312

# A4 page, one DeviceGray image, centred inside 10 mm margins
python3 -c "from pypdf import PdfReader; p=PdfReader('negative.pdf').pages[0]; \
  print(p.mediabox, p['/Resources']['/XObject'])"
```

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
  Paper, PrintSpec   stock, margin, sampling density, orientation
  PrintLayout        fit inside the margins, in points and pixels
  GrayResampler      bilinear resampling on raw samples, 8 → 16 bits
  Gray16             raw 16-bit raster, free of AWT colour models
  GrayProfile        synthesised Grey Gamma 2.2 ICC profile
  TiffEncoder        baseline TIFF, written byte by byte
  PdfExporter        one page at true paper size
  Mirror             horizontal flip for ink-down film
app/         Spring Boot
  web/               HTTP surface, catalogue, error mapping
  service/           render and export orchestration
  config/            property binding, imaging beans
```

`imaging` has no framework dependency by construction, and the Gradle module boundary
enforces it. PDF output adds PDFBox to that module — a library, not a framework.

## Not built yet

In the order they will be added:

1. Calibration: emit a step-wedge target, read measured densities back, fit a curve, store
   it as a versioned process profile. This replaces the assumed `printDmax` with a measured
   one and turns the tone curve into a real characteristic curve.
2. A step wedge in the margin of the PDF, for the same reason: every print should carry its
   own reference strip.
3. A 16-bit path end to end. The export is 16-bit, but the renderer still works from 8-bit
   source values and a 256-entry LUT.
4. A job queue for large renders, with retries and idempotency.
