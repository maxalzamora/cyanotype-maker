# cyanotypes

Upload a photograph, get back the **digital negative** you print on transparency for a
cyanotype contact print.

Cyanotype is a negative process: the paper turns Prussian blue where UV reaches it, so to
reproduce a photograph you expose through an inverted image. This service produces that
inverted image, shaped for what the process can actually print.

> **Status: stage 4.** Perceptual conversion, density mapping, process profiles, a monotone
> tone curve, print-ready export (16-bit TIFF and a page-sized PDF), calibration from a
> measured print, and a preview of the print itself. See [Calibration](#calibration) and
> [The print preview](#the-print-preview).

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
| `cal-…` | CALIBRATED | Created by measuring a real print; the assumed straight line is replaced by the measured curve. |

A profile is more than a preset: it carries the print density range (`printDmin`,
`printDmax`), output levels (`blackLevel`, `whiteLevel` — heavy ink laydown can coalesce on
transparency film), and a `ToneCurve`. Curves use Fritsch–Carlson tangents, so they cannot
overshoot; an overshoot in a tone curve shows up as a contour in the print.

Deep shadows do clip in the density profiles, at reflectance below about 4%. This is
physical: a print cannot be denser than its Dmax. `cyanotype-soft` exists to show the
workaround — give up both ends deliberately rather than letting the bottom block up.

## Calibration

The built-in profiles assume a density range, and assume that print density tracks the file
value in a straight line. Neither assumption survives contact with real materials. The
calibration loop replaces them with a measurement:

1. **Print the target.** `GET /api/calibration/target?format=pdf` returns 21 bars, raw
   values 0 → 255, edge to edge, laid out on paper by the same code that lays out a
   negative. No tone curve is applied: the target exists to find out what the curve should
   be.
2. **Expose it and photograph the print.** Crop to the bars. Nothing has to be located,
   because the target is only stripes.
3. **Measure.** `POST /api/calibration` decodes the photograph to linear light, takes the
   median of the middle 70% of each bar, converts to density (`-log10`), and normalises
   against the lightest and darkest patches. The result is 21 points on the curve *file
   value → position in this print's density range*.
4. **Fit, invert, store.** A monotone spline is fitted through the points, a 256-entry LUT
   is precomputed from its inverse combined with the existing density model, and the
   profile is written to `data/profiles/<id>.json`. It is selectable immediately, and
   survives a restart.

The measured numbers are relative, and that is enough. A camera cannot know absolute
density, but it can order tones and find both ends of the range, and a curve needs only
those. What is *not* normalised away is the shape: an S-curve in the print is exactly what
a straight-line assumption fails to reproduce.

Rejected scans say why, in terms the person holding the camera can act on:

| reason | what it means |
|---|---|
| `CALIBRATION_TOO_SMALL` | fewer than 42 px across; the bars cannot be resolved |
| `CALIBRATION_WRONG_ORIENTATION` | the bars run vertically; rotate the photograph |
| `CALIBRATION_NO_CONTRAST` | under 0.30 of density between the ends; underexposed or washed out |
| `CALIBRATION_NOT_MONOTONE` | the bars do not step in order; a bad crop, a missed bar, or a mirrored negative |

Without a darkroom the loop can still be exercised end to end: `GET
/api/calibration/simulated-scan.png` renders the target through a known forward model (an
S-curve print, slight vignetting, sensor noise), and the UI has a button that feeds it
straight back into the measurement. The same model is the test fixture — the analyser is
required to recover the curve it was generated from to within 0.02 of normalised density,
and a calibrated profile of a *linear* print has to reproduce the analytic `cyanotype` LUT
to within two levels across all 256 values.

## The print preview

`POST /api/preview` returns the negative coloured as the print it would make, so you can see
the result before cutting film. It is not a second tone mapping: the negative's value *is* the
print's density (high value, clear film, more UV, darker blue), so the preview only adds the
colour of the process. Whatever range the film reaches is the range the print reaches, which
is why `cyanotype-soft` previews as a visibly softer print.

Paper and ink are mixed in **density** — a geometric interpolation of reflectances, which is
how stacked absorbing layers combine — rather than alpha-blending blue over white. That is
why the midtones pass through denim blue instead of washing out, and why the highlights stay
slightly warm. Deterministic grain and a 4% corner falloff keep it from looking like a colour
ramp.

The preview has the print's framing: the photograph is resampled down to 1400 px on the long
side, never cropped. What it cannot show is the paper's texture, the irregularity of a
hand-coated edge, or the way a real print shifts as it oxidises over the first day or two.

## Print files

A preview is not a print file. Three files, for three jobs:

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

Open <http://localhost:3000>. The print preview appears as soon as a photograph is chosen;
the print settings are folded away because they only affect the downloadable files. The
[calibrate](http://localhost:3000/calibrate.html) page has the same shape: the steps on the
left, the measurement on the right. Both pages follow `prefers-color-scheme` and build their
selectors from `/api/profiles` and `/api/papers`, so the interface cannot drift from the API.

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
| `422` | recognised format, but the bytes do not decode; or a calibration scan that cannot be read as a wedge (`CALIBRATION_*`) |
| `500` | the upload was fine but the encoder or profile store failed (`EXPORT_FAILED`, `PROFILE_STORE_FAILED`) |

The declared `Content-Type` is ignored. Format is decided by magic bytes, and dimensions
are read from the header **before** pixels are allocated — a 40-byte PNG claiming
30000×30000 is rejected instead of consuming 2.7 GB.

`POST /api/preview` — the print, not the negative. Same `file` and `profile` fields as
`/api/render`, no `format`:

| | |
|---|---|
| `200` | an RGB PNG, at most 1400 px on the long side, with `X-Profile`, `X-Source-Format`, `X-Source-Width`, `X-Source-Height`, `X-Preview-Width`, `X-Preview-Height` |
| errors | the same as `/api/render` |

Other endpoints:

- `GET /api/profiles` — every profile: the built-ins plus anything calibrated on this
  machine. `measuredPoints` is zero for the assumptions.
- `GET /api/papers` — paper sizes in millimetres.
- `GET /api/defaults` — what the form starts with.
- `GET /api/diagnostics/step-wedge?profile=…` — renders a 21-step ramp and reports
  expected vs actual, plus monotonicity and the two extremes.
- `GET /api/diagnostics/step-wedge.png?profile=…` — the same wedge as an image.
- `GET /api/calibration/target?format=pdf` — the printable 21-bar wedge (`png` is a
  preview, `tiff` a 16-bit print file).
- `POST /api/calibration` — `file`, a photograph of the printed wedge; optional `name`.
  Returns the measured patches, the profile id, the resulting 256-entry LUT, and notes.
- `DELETE /api/calibration/{id}` — removes a calibration and its JSON file. Built-ins are
  refused.
- `GET /api/calibration/simulated-scan.png` — a synthetic photograph of a print, for demos
  and for trying the loop without a darkroom.
- `GET /health`

Smoke test against a running server:

```bash
python3 scripts/smoke.py photo.jpg
```

It also runs the calibration loop end to end: fetch the target, fetch the simulated scan,
post it, and check that a monotone profile comes back and is offered by the catalogue.

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
`CyanotypePreviewTest` pins the preview's ends — opaque film is paper, clear film is Prussian
blue — and checks that the palette never gets lighter as exposure rises.

Calibration can be checked without a darkroom:

```bash
# 1. the printable target: 21 bars, a real PDF page
curl -s -D - -o target.pdf "localhost:3000/api/calibration/target?format=pdf" | grep -i patches

# 2. a photograph of a print that was never made
curl -s -o scan.png localhost:3000/api/calibration/simulated-scan.png

# 3. measure it; the report carries the patches, the LUT and the profile id
curl -s -F file=@scan.png -F name=demo localhost:3000/api/calibration | python3 -m json.tool

# 4. it is now a profile everywhere, and a file on disk
curl -s localhost:3000/api/profiles
cat data/profiles/cal-*.json
```

For the simulated S-curve the measured range comes out at 1.00 and the calibrated LUT is
`0 → 255`, `128 → 125`, `255 → 0`: clear film for black, solid ink for white, and the
midtones moved from the assumption's 121 to the 125 that this print actually needs.

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

The print preview is checkable the same way — RGB, capped at 1400 px, and monotone from
paper to ink across the 21 bars of the calibration target:

```bash
curl -s -F file=@target.png -F profile=cyanotype -o preview.png localhost:3000/api/preview
python3 -c "from PIL import Image; im=Image.open('preview.png'); \
  print(im.mode, im.size, im.getpixel((0, im.height // 2)))"
# RGB (1400, 467) (15, 50, 84)
```

## Layout

```
imaging/     pure Java, no Spring on the classpath
  ImageFormat        magic-byte sniffing
  ImageInspector     header validation, decompression-bomb guard
  ImageDecoder       bytes → pixels, shared by render and calibration
  Srgb               transfer function + luminance
  ToneCurve          monotone cubic interpolation
  ProcessProfile     print range, output levels, curve, measured response
  NegativeLut        the whole 8-bit transform as a table
  NegativeRenderer   applies the LUT, writes a greyscale raster
  CyanotypePreview   the negative as a print: paper and ink, mixed in density
  PngEncoder         8-bit PNG output (greyscale or RGB)
  NegativePipeline   inspect → decode → render → encode
  StepWedge          known-input test fixture
  CalibrationTarget  the printable 21-bar wedge, and the grid that reads it back
  ScanAnalyser       photograph → linear light → one density per bar
  ScanMeasurement    the measurement, plus the numbers that say whether to trust it
  MeasuredResponse   monotone curve from print density to file value
  SimulatedScan      forward model of a print, for tests and the demo
  Paper, PrintSpec   stock, margin, sampling density, orientation
  PrintLayout        fit inside the margins, in points and pixels
  GrayResampler      bilinear resampling on raw samples, 8 → 16 bits
  Gray16             raw 16-bit raster, free of AWT colour models
  GrayProfile        synthesised Grey Gamma 2.2 ICC profile
  TiffEncoder        baseline TIFF, written byte by byte
  PdfExporter        one page at true paper size
  Mirror             horizontal flip for ink-down film
app/         Spring Boot
  web/               HTTP surface, catalogue, calibration, error mapping
  service/           render, export, calibration, profile registry and store
  config/            property binding, imaging beans
```

`imaging` has no framework dependency by construction, and the Gradle module boundary
enforces it. PDF output adds PDFBox to that module — a library, not a framework.

## Not built yet

In the order they will be added:

1. A step wedge in the margin of the PDF, for the same reason: every print should carry its
   own reference strip.
2. A 16-bit path end to end. The export is 16-bit, but the renderer still works from 8-bit
   source values and a 256-entry LUT.
3. Automatic alignment of the scan (four fiducials and a homography) so the crop does not
   have to be done by hand.
4. A job queue for large renders, with retries and idempotency.
