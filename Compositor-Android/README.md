# Compositor Android

Native Android photo editor inspired by the documented workflow of the open-source Compositor project. This repository contains an independent Android implementation.

## V0.2
- Desktop-style dark workspace
- File/Edit/Image/Layer/Select/Filter/View/Help menus
- Brush, eraser, move, marquee, lasso, crop, rectangle, ellipse, text, eyedropper, gradient, blur, sharpen, dodge, burn and hand tools
- Multiple layers, duplicate/delete/new layer
- Layer opacity and blend mode selector
- Layer masks
- Undo/redo
- Grayscale and invert adjustments
- Open images from device
- PNG/JPG export
- Zoom

## Build
Open `Compositor-Android` in Android Studio. Use JDK 17 and run the app on an Android 8.0+ device/emulator.

## GitHub Actions
A workflow at `.github/workflows/android-build.yml` builds a debug APK on pushes affecting the Android project and uploads the APK as a workflow artifact.

## Next modules
PSD project files, non-destructive adjustment layers, better selection engines, transform handles, clone/healing, advanced filters, guides/snapping, and touch/gesture refinement.
