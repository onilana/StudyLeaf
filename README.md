# StudyLeaf

**StudyLeaf** is an Android document studio, optical scanner, format converter, and AI-assisted study companion built with Kotlin, Jetpack Compose, and Material Design 3. Designed for both phones and tablets, StudyLeaf keeps everyday PDF reading, scanning, annotation, conversion, and compression fast and on-device, with optional cloud-backed study intelligence.

- **Author:** Oni Lana
- **Publisher:** Oni Lana
- **Application ID:** `com.studyleaf.org`
- **Platform:** Android (Min SDK 24 / Android 7.0+, Target SDK 36)

---

## Key Features

### 1. Adaptive Mobile & Tablet Workspace
- **Material Design 3 Interface:** Clean, tactile surfaces with full **Light and Dark theme** support and adaptive layouts across Compact (phones), Medium (foldables), and Expanded (tablets) window sizes.
- **Unified Home Dashboard:** Compact 3×3 tools grid, instant search bar, format filter chips (`All`, `PDF`, `Word`, `Excel`, `Image`, `Scan`, `Favorites`), quick document import, and a streamlined Recent Documents list.
- **Tablet Supporting Preview Pane:** On wide screens, selecting a document displays an interactive side-by-side preview pane alongside a `NavigationRail`.
- **Local Vault & Activity History:** Browse all saved documents in List or Grid mode, star favorites, rename, share, or delete files, and review a timestamped log of scans, merges, and format conversions.

### 2. Fullscreen PDF & Document Reader
- **Distraction-Free Fullscreen Viewing:** Opens documents edge-to-edge with pinch-to-zoom, pan gestures, and a slim top bar.
- **Top-Right Horizontal Tools Menu:** Access all reader tools from a compact horizontal menu positioned next to the Share icon, keeping the reading canvas uncluttered.
- **Study Annotations:** Highlight, underline, strikethrough, freehand pen drawing, and sticky notes per page with instant undo and persistent Room storage.
- **Page Thumbnail Rail & Page Reordering:** Jump quickly between pages or split, extract, and rearrange PDF pages.
- **System "Open With" & Share Integration:** Registered via Android `ACTION_VIEW` and `ACTION_SEND` intent filters so tapping **PDF**, **Excel (`.xlsx`, `.xls`, `.csv`)**, **Word (`.docx`, `.doc`)**, or **Plain Text (`.txt`)** files from any file manager or messaging app lets you open them directly in StudyLeaf.

### 3. Optical Document Scanner
- **4-Corner Perspective Alignment:** Capture pages with the camera or import photos from the system Photo Picker, then adjust four corner handles to deskew and crop cleanly.
- **8 Built-In PDF Scan Filters:** `Original`, `Crisp Doc`, `Magic Color`, `Whiteboard`, `Grayscale`, `B&W Print`, `Warm Study`, and `Ink Boost`.
- **Fine-Grained Image Enhancement:** On-device **Clarity** (3×3 sharpening kernel), **Brightness**, and **Contrast** sliders with live preview.
- **Selectable Scan Page Resolution:** Choose between **Small** (`720 × 1018`), **Medium** (`1080 × 1527`), and **Large** (`1600 × 2262`) output resolutions for multi-page batch PDFs.

### 4. On-Device Conversion & Compression Suite
All core document conversions and optimizations run locally on the device:
- **Excel ⇄ Word / PDF:** Convert between Excel spreadsheets (`.xlsx`), Word documents (`.docx`), and PDF files in both directions.
- **Word & Text → PDF:** Typeset `.docx` documents and text notes into formatted PDF pages.
- **Image ⇄ PDF:** Combine photos into multi-page PDFs or export PDF pages as crisp PNG images.
- **Merge PDFs:** Combine multiple PDF documents into a single file.
- **Split & Reorder Pages:** Reorder pages or extract a subset into a new PDF.
- **Optimize PDF:** Re-encode and shrink PDF file sizes locally.
- **Image Compressor:** Compress photos with selectable quality/size targets before sharing or archiving.

### 5. AI Study Assistant
- **Document & Page Summaries:** Generate concise study summaries for the active document or specific page.
- **Interactive Flashcards:** Create key-concept flashcards directly from study notes and PDFs.
- **Multimodal Q&A:** Ask contextual questions about scanned pages, charts, tables, and text via Firebase AI Logic protected by App Check.

---

## Architecture & Tech Stack

- **Language & UI:** Kotlin, Jetpack Compose, Material Design 3, Edge-to-Edge (`enableEdgeToEdge`)
- **Architecture:** MVVM with `ViewModel`, Kotlin Coroutines, and `StateFlow` (`collectAsStateWithLifecycle`)
- **Local Persistence:** Room Database (documents, annotations, conversion activity logs) and Jetpack DataStore Preferences (theme and app preferences)
- **Document & Imaging Engine:** Native Android `PdfDocument`, `PdfRenderer`, OpenXML `.docx`/`.xlsx` zip/XML processing, bitmap perspective warp, 3×3 clarity convolution kernel, and Coil image loading
- **AI Integration:** Firebase AI Logic SDK (`firebase-ai`) with Firebase App Check
- **Privacy & Storage:** Zero-permission Android Photo Picker (`PickVisualMedia`), Storage Access Framework (`OpenDocument`), and scoped `FileProvider` sharing (`com.studyleaf.org.fileprovider`)

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Edge-to-edge host, App Check init, Open With / Share intents
├── ai/
│   ├── AppCheckInitializer.kt       # Firebase App Check attestation initializer
│   └── StudyLeafAiEngine.kt         # Firebase AI Logic assistant & study helper
├── data/
│   ├── engine/                      # PDF, Word, Excel, image compression & scan enhancement engines
│   ├── local/                       # Room database, DAOs, entities & DataStore preferences
│   ├── model/StudyLeafModels.kt     # Domain models, scan filters, resolutions & tool definitions
│   └── repository/                  # Document repository & sample workspace seeding
├── ui/
│   ├── StudyLeafApp.kt              # Adaptive scaffold, navigation state, dialogs & tool routing
│   ├── StudyLeafViewModel.kt        # Centralized UI state & background operations
│   ├── components/                  # Reusable M3 cards, search bar, chips, editor & preview canvas
│   ├── screens/                     # Home, Files, History, Reader, Scanner, Converter, Reorder & AI
│   └── theme/                       # Material 3 color schemes, typography & shapes
└── update/
    └── InAppUpdateController.kt     # Google Play In-App Updates coordinator
```

---

## Building the Project

### Prerequisites
- Android Studio (Ladybug or newer) or JDK 17+ with Gradle 9+
- Android SDK 36 installed

### Build Release APK & Bundle (.aab)
```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

The generated artifacts are output to:
- **Signed Release APK:** `app/build/outputs/apk/release/app-release.apk`
- **Release App Bundle (AAB):** `app/build/outputs/bundle/release/app-release.aab`

### Build Debug APK
```bash
./gradlew :app:assembleDebug
```

The generated APK is output to:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Credits

Created and published by **Oni Lana**.
