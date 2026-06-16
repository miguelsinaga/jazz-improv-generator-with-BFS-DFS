# Syncopate — Jazz Improvisation Generator

> Pembangkitan Melodi Jazz Berbasis Chord Progression Menggunakan Algoritma BFS dan DFS pada Graf Nada

**Tugas Makalah IF2211 Strategi Algoritma — Sem. II 2025/2026**  
Institut Teknologi Bandung

---

## Deskripsi

Syncopate adalah web application yang menghasilkan melodi improvisasi jazz secara otomatis menggunakan algoritma **BFS (Breadth-First Search)** dan **DFS (Depth-First Search)** pada **weighted directed graph** transisi nada.

Program memodelkan nada-nada dalam skala jazz sebagai node dalam graf, dimana edge merepresentasikan transisi valid antar nada dengan bobot berdasarkan interval musikal dan kedekatan terhadap chord tone.

### Arsitektur

```
┌─────────────────────┐               ┌──────────────────────────┐
│  Frontend           │   HTTP POST   │  Backend (Java)          │
│  HTML/CSS/JS        │ ────────────> │  Spring Boot + JGraphT   │
│  - Chord selection  │               │  - Build weighted graph  │
│  - Algorithm toggle │  JSON resp.   │  - BFS / DFS traversal   │
│  - Web Audio API    │ <──────────── │  - Rhythm assignment     │
│    (play melodi)    │               │  - Compute statistics    │
└─────────────────────┘               └──────────────────────────┘
     localhost:3000                         localhost:8080
```

### Konsep Algoritma

**Weighted Directed Graph:**
- **Node** = nada dalam skala jazz (7 nada per chord)
- **Edge** = transisi valid (interval ≤ 7 semitone)
- **Bobot** = `INTERVAL_WEIGHT[interval] × CHORD_TONE_BONUS`
  - Interval kecil (stepwise) mendapat bobot lebih tinggi
  - Chord tone mendapat bonus 1.5x

**BFS (Breadth-First Search):**
- Menggunakan **Queue** (FIFO)
- Mengeksplorasi semua nada yang bisa dicapai dalam 1 langkah sebelum lanjut
- Hasil: melodi yang lebih "merata" dan exploratory

**DFS (Depth-First Search):**
- Menggunakan **Stack** (LIFO)
- Mengeksplorasi sedalam mungkin sebelum backtrack
- Hasil: melodi yang lebih "linear" dan mengalir

---

## Struktur Project

```
jazz-improv/
├── README.md
├── .gitignore
│
├── backend/                          # Java Spring Boot API
│   ├── pom.xml                       # Maven dependencies
│   └── src/main/
│       ├── java/com/jazzimprov/
│       │   ├── JazzImprovApplication.java    # Entry point
│       │   ├── config/
│       │   │   ├── MusicConfig.java          # Konstanta musik (skala, chord, bobot)
│       │   │   └── WebConfig.java            # CORS configuration
│       │   ├── model/
│       │   │   ├── ImprovRequest.java        # DTO request
│       │   │   └── ImprovResponse.java       # DTO response
│       │   ├── service/
│       │   │   ├── GraphService.java         # Build graf berbobot (JGraphT)
│       │   │   ├── TraversalService.java     # BFS dan DFS
│       │   │   └── RhythmService.java        # Assign durasi nada
│       │   └── controller/
│       │       └── ImprovController.java     # REST API endpoints
│       └── resources/
│           └── application.properties
│
└── frontend/                         # Static web client
    ├── index.html                    # Halaman utama
    ├── css/
    │   └── style.css                 # Custom styles
    └── js/
        ├── config.js                 # Konstanta musik (frontend)
        ├── audio.js                  # Web Audio API playback
        ├── ui.js                     # DOM rendering
        └── app.js                    # Main orchestrator + API calls
```

---

## Cara Menjalankan

### Prerequisites
- **Java 17+** (JDK)
- **Maven 3.8+**
- Browser modern (Chrome / Firefox / Edge)

### 1. Jalankan Backend

```bash
cd backend
mvn spring-boot:run
```

Server berjalan di `http://localhost:8080`.

Verifikasi dengan:
```bash
curl http://localhost:8080/api/health
```

### 2. Jalankan Frontend

Buka file `frontend/index.html` langsung di browser, atau gunakan live server:

```bash
cd frontend

# Opsi 1: Python
python3 -m http.server 3000

# Opsi 2: Node.js
npx serve . -l 3000

# Opsi 3: VS Code Live Server extension
```

Buka `http://localhost:3000` di browser.

### 3. Gunakan Aplikasi

1. **Pilih chord progression** — klik dropdown untuk mengubah root/tipe chord
2. **Pilih algoritma** — toggle antara BFS dan DFS
3. **Pilih rhythm** — Weight (berdasarkan bobot graf) atau Swing (triplet feel)
4. **Atur randomness** — slider dari deterministic (0) ke chaotic (1)
5. **Klik Generate Improvisation** — backend memproses, hasilnya muncul di timeline
6. **Klik ▶ Play** — melodi dimainkan via Web Audio API di browser

---

## API Endpoints

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| `POST` | `/api/generate` | Generate improvisasi jazz |
| `GET` | `/api/chords` | Daftar chord yang tersedia |
| `GET` | `/api/health` | Health check |

### POST /api/generate

**Request Body:**
```json
{
  "chordProgression": ["Dm7", "G7", "Cmaj7"],
  "algorithm": "DFS",
  "randomness": 0.3,
  "rhythmMode": "swing",
  "maxNotes": 32
}
```

**Response:**
```json
{
  "notes": ["D", "F", "A", "G", "B", "D", ...],
  "durations": [0.375, 0.125, 0.375, 0.125, ...],
  "tempoBpm": 120,
  "algorithm": "DFS",
  "rhythmMode": "swing",
  "stats": {
    "uniqueNotes": 7,
    "avgInterval": 2.1,
    "complexity": "Med-High",
    "totalNotes": 32
  },
  "graphInfo": {
    "nodeCount": 7,
    "edgeCount": 24,
    "nodes": ["D", "E", "F", "G", "A", "A#", "C"]
  }
}
```

---

## Teknologi

| Komponen | Teknologi |
|----------|-----------|
| Backend | Java 17, Spring Boot 3.3, JGraphT 1.5 |
| Frontend | HTML5, CSS3 (Tailwind), Vanilla JavaScript |
| Audio | Web Audio API (OscillatorNode, triangle wave) |
| Build | Maven |
| Fonts | Hanken Grotesk, Inter, JetBrains Mono |

---

## Referensi

1. Cormen, T.H. et al. — *Introduction to Algorithms* (BFS & DFS)
2. JGraphT Documentation — https://jgrapht.org
3. Web Audio API — https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API
4. Jazz Theory — Dorian, Mixolydian, Ionian modes
5. Munir, Rinaldi. — *Strategi Algoritma*, Informatika Bandung
