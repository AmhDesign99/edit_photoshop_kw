# Web Photo Editor

A browser-based Photoshop-style image editor designed for free deployment on GitHub Pages.

## Current MVP

- Dark desktop-style workspace
- File/Edit/Image/Layer/Select/View/Help menus
- Canvas, zoom, pan and fit-to-screen
- Layers with visibility, opacity and blend modes
- History with undo/redo
- New/Open image
- Save/load project (`.wpe` JSON package)
- Export PNG/JPG/WebP
- Move, rectangular marquee, lasso-style selection, crop, eyedropper
- Brush, pencil, eraser
- Clone-style stamp
- Gradient and bucket fill
- Blur/sharpen/smudge/tone tools
- Text tool and vector-style rectangle/ellipse/line tools
- Rulers/grid toggles and keyboard shortcuts

## Run locally

No build step is required. Open `index.html` in a modern browser. For best local testing, serve the folder with any static web server.

## GitHub Pages

1. Create a GitHub repository.
2. Upload the contents of this folder to the repository root.
3. Go to **Settings → Pages**.
4. Select **Deploy from a branch**.
5. Select the `main` branch and `/ (root)`.
6. Save. GitHub Pages will publish the editor.

## Notes

This project intentionally uses original UI styling and symbols rather than copying proprietary Photoshop assets. Advanced features such as full PSD fidelity, non-destructive masks, advanced healing, smart objects, CMYK workflows, and GPU-accelerated effects can be added in later phases.
