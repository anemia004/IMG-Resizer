# IMG Resizer

A privacy-first, offline image resizer that runs completely in your browser.  
No data ever leaves your device — all processing happens locally on canvas.

## Features
- Resize by pixels or centimeters (with adjustable DPI)
- Keep aspect ratio to avoid distortion
- Compress to a target file size (JPEG / WebP) with automatic downscaling if needed
- Preview original and processed images side by side
- Download in PNG, JPEG, or WebP
- Mobile-friendly retro Windows 95 aesthetic

## How to Use

### Option 1: Open directly
Double-click `IMG_Resize.html`
No server required — works offline.

### Option 2: Local server (Python)
If you prefer to run it via localhost:

```bash
cd img-resizer
python -m http.server 8000

Then open http://localhost:8000 in your browser.
  
## Privacy
100% client-side — images are never uploaded on any host
Content Security Policy blocks all network connections
No external scripts, fonts, or CDNs — everything is self-contained
