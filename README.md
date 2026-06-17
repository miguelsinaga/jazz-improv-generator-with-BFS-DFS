# Syncopate — Jazz Improvisation Generator

> Pembangkitan Melodi Jazz Berbasis Chord Progression Menggunakan Algoritma BFS dan DFS pada Graf Nada Berbobot

**Tugas Makalah IF2211 Strategi Algoritma — Sem. II 2025/2026**
Institut Teknologi Bandung
**Miguel Rangga — 13524069**

---

## Deskripsi

Syncopate adalah web application yang menghasilkan melodi improvisasi jazz secara otomatis menggunakan algoritma **BFS (Breadth-First Search)** dan **DFS (Depth-First Search)** pada **weighted directed graph** transisi nada.

Untuk setiap chord, program membangun graf di mana **node = nada** dan **edge = transisi melodik**. Bobot tiap edge dirancang berdasarkan **teori musik jazz** (gerak stepwise, chord tone, bebop scale, dan chromatic enclosure), sehingga melodi yang dihasilkan terdengar natural dan sesuai konteks chord.

Aplikasi berjalan **sepenuhnya di browser** (tidak butuh server) dan bisa di-deploy sebagai situs statis.

### Arsitektur

Logika generator memiliki **dua implementasi yang identik**:

1. **Engine JavaScript (client-side)** — `frontend/js/engine.js`
   Dipakai aplikasi web; berjalan langsung di browser sehingga app bisa di-deploy statis.
2. **Backend Java (Spring Boot)** — `backend/`
   Implementasi referensi algoritma (graf via JGraphT, REST API). **Opsional** — tidak diperlukan untuk menjalankan web app, tetapi berguna sebagai acuan/uji algoritma.

```
┌──────────────────────────────────────────────┐
│  Browser (situs statis — deploy: Vercel)       │
│                                                │
│   index.html ─ ui.js ─ app.js                  │
│        │                                       │
│        ▼                                       │
│   engine.js   ← buildGraph + BFS/DFS + rhythm  │
│        │                                       │
│        ▼                                       │
│   audio.js    ← Web Audio API (melodi + akor)  │
└──────────────────────────────────────────────┘

(opsional) backend/  Java Spring Boot + JGraphT — REST API referensi
```

### Konsep Algoritma

**Weighted Directed Graph (per chord).** Node terdiri dari tiga kelas nada:

1. **Chord tone** — nada pembentuk chord (mis. `Cmaj7` → C, E, G, B). Titik "mendarat" yang stabil.
2. **Scale tone** — nada lain pada **bebop scale** (chord-scale + 1 nada kromatik passing). Bahan *scalar run* khas bebop.
3. **Approach note** — nada kromatik pengepung (½ langkah di atas/bawah chord tone) untuk teknik **enclosure**.

**Bobot edge** (semakin tinggi = semakin disukai):

| Transisi | Bobot |
|---|---|
| Approach/Scale → chord tone (½ langkah, *resolve*) | `2.6` |
| Scale → chord tone (langkah penuh) | `1.8` |
| Antar approach pengepung target sama (*enclosure*) | `1.8` |
| Scale → scale (*scalar run*) | `1.2` |
| Chord tone → scale (memulai run) | `0.9` |
| Chord tone → approach kromatik | `0.4` |
| Antar chord tone (arpeggiation) | bobot interval `{1:0.9, 2:1.0, 3:0.7, 4:0.55, 5:0.45, 6:0.3}` |

Nada non-chord hanya boleh bergerak **stepwise** (≤ 2 semitone) dan selalu cenderung *resolve* ke chord tone.

**Bebop scale per tipe chord:** `7`→Mixolydian+nat7, `maj7`/`6`→Ionian+#5, `m7`→Dorian+nat3, `m6`→melodic minor+#5, `m7b5`→Locrian+nat7, `dim7`→whole-half diminished.

**BFS (Breadth-First Search)** — memakai **Queue** (FIFO); mengeksplorasi beberapa nada pada frontier yang sama → melodi lebih "merata"/eksploratif.

**DFS (Depth-First Search)** — memakai **Stack** (LIFO); mengeksplorasi sedalam mungkin, backtrack saat buntu → melodi lebih "linear"/mengalir.

**Pemilihan greedy + konteks.** Nada berikutnya dipilih dengan strategi **greedy** terhadap bobot yang sudah disesuaikan konteks 1–2 nada terakhir (mendorong resolusi, penyelesaian enclosure, dan scalar run, serta menghindari osilasi A-B-A-B). Parameter **randomness** mengatur peluang eksplorasi *weighted-random* untuk variasi (`0` = greedy penuh, `1` = eksploratif).

### Fitur

- **Segala nada dasar & chord** — 12 root kromatik × 7 tipe chord (`maj7`, `7`, `m7`, `m7b5`, `dim7`, `6`, `m6`).
- **Chromatic enclosure & bebop scale** untuk rasa jazz yang lebih natural.
- **Slider tempo** (40–240 BPM), berpengaruh langsung saat diputar.
- **Iringan akor** — chord yang dipilih ikut dibunyikan sebagai pad di bawah melodi.
- **Dua rhythm mode** — Swing (triplet feel) atau Weight (durasi dari bobot graf).

---

## Struktur Project

```
jazz-improv-generator-with-BFS-DFS/
├── README.md
├── LAPORAN_REFERENSI.md             # Penjelasan + seluruh source code (untuk makalah)
├── vercel.json                      # Konfigurasi deploy statis (folder frontend)
│
├── frontend/                        # Aplikasi web (statis, jalan di browser)
│   ├── index.html                   # Halaman utama
│   ├── css/
│   │   └── style.css                # Custom styles
│   └── js/
│       ├── config.js                # Konstanta & helper musik (nada, frekuensi, chord tone)
│       ├── engine.js                # Mesin generator: buildGraph + BFS/DFS + rhythm
│       ├── audio.js                 # Web Audio API: melodi + iringan akor
│       ├── ui.js                    # DOM rendering & interaksi UI
│       └── app.js                   # State, event handler, loop playback
│
└── backend/                         # (Opsional) Java Spring Boot API — referensi algoritma
    ├── pom.xml
    ├── Dockerfile
    └── src/main/
        ├── java/com/jazzimprov/
        │   ├── JazzImprovApplication.java
        │   ├── config/
        │   │   ├── MusicConfig.java          # Skala, bebop scale, chord tone, bobot
        │   │   └── WebConfig.java            # CORS
        │   ├── model/
        │   │   ├── ImprovRequest.java
        │   │   └── ImprovResponse.java
        │   ├── service/
        │   │   ├── GraphService.java         # Build graf berbobot (JGraphT)
        │   │   ├── TraversalService.java     # BFS, DFS, greedy + bobot kontekstual
        │   │   └── RhythmService.java         # Durasi nada & statistik
        │   └── controller/
        │       └── ImprovController.java     # REST API endpoints
        └── resources/
            └── application.properties
```

---

## Cara Menjalankan

### Aplikasi web (utama)

Tidak butuh server — cukup buka `frontend/index.html` di browser, atau pakai live server:

```bash
cd frontend

# Opsi 1: Python
python3 -m http.server 3000

# Opsi 2: Node.js
npx serve . -l 3000

# Opsi 3: VS Code Live Server extension
```

Buka `http://localhost:3000` di browser.

**Penggunaan:**
1. Susun **chord progression** — ubah root/tipe chord, tambah/hapus chord.
2. Pilih **algoritma** — BFS atau DFS.
3. Pilih **rhythm** — Swing atau Weight.
4. Atur **randomness** (deterministic ↔ chaotic) dan **tempo** (40–240 BPM).
5. Klik **Generate Improvisation** — hasil muncul di timeline.
6. Klik **▶ Play** — melodi + iringan akor dimainkan via Web Audio API.

### (Opsional) Backend Java — referensi algoritma

```bash
cd backend
mvn spring-boot:run        # http://localhost:8080
curl http://localhost:8080/api/health
```

> Catatan: aplikasi web memakai `engine.js` (client-side), jadi backend tidak wajib menyala.
> Backend disediakan sebagai implementasi referensi dan untuk pengujian API.

---

## Deploy (Vercel)

Aplikasi adalah situs statis murni. File `vercel.json` sudah mengarahkan output ke folder `frontend/`.

1. Import repo ke [Vercel](https://vercel.com) (login dengan GitHub).
2. Framework Preset: **Other**, Root Directory: root repo (otomatis baca `vercel.json`).
3. **Deploy.** Setiap `git push` ke `main` akan auto-redeploy.

---

## (Opsional) Backend API

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| `POST` | `/api/generate` | Generate improvisasi jazz |
| `GET`  | `/api/chords` | Daftar root & tipe chord yang tersedia |
| `GET`  | `/api/health` | Health check |

### POST /api/generate

**Request Body:**
```json
{
  "chordProgression": ["Dm7", "G7", "Cmaj7"],
  "algorithm": "DFS",
  "randomness": 0.3,
  "rhythmMode": "swing",
  "tempo": 120,
  "maxNotes": 32
}
```

**Response:**
```json
{
  "notes": ["D", "F", "E", "F", "F#", "G", "A", "B", "C", "..."],
  "durations": [0.375, 0.125, 0.375, 0.125, "..."],
  "chordSequence": ["Dm7", "Dm7", "G7", "G7", "Cmaj7", "..."],
  "tempoBpm": 120,
  "algorithm": "DFS",
  "rhythmMode": "swing",
  "stats": {
    "uniqueNotes": 8,
    "avgInterval": 1.7,
    "complexity": "Med-High",
    "totalNotes": 32
  }
}
```

---

## Teknologi

| Komponen | Teknologi |
|----------|-----------|
| Web app | HTML5, CSS3 (Tailwind via CDN), Vanilla JavaScript |
| Generator | `engine.js` (port logika algoritma ke JS) |
| Audio | Web Audio API (OscillatorNode — triangle melodi + pad akor) |
| Backend (opsional) | Java 17, Spring Boot 3.3, JGraphT 1.5, Maven |
| Deploy | Vercel (statis) |
| Fonts | Hanken Grotesk, Inter, JetBrains Mono |

---

## Referensi

1. Munir, Rinaldi. — *Strategi Algoritma*, Informatika Bandung (BFS & DFS)
2. Cormen, T.H. et al. — *Introduction to Algorithms* (graph traversal)
3. JGraphT Documentation — https://jgrapht.org
4. Web Audio API — https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API
5. Jazz Theory — chord-scale (Dorian/Mixolydian/Ionian), bebop scale, chromatic enclosure
