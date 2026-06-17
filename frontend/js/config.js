/**
 * config.js — Konstanta & helper musik untuk frontend.
 *
 * Mendukung SEGALA nada dasar (12 root kromatik) dan beberapa tipe chord.
 * Chord tone dihitung secara programatik (mirror dari MusicConfig di backend)
 * agar display timeline & iringan akor bekerja untuk chord apa pun.
 */

const Config = {

  /** Tempo default (BPM) */
  TEMPO_BPM: 120,

  /** 12 nada kromatik (ejaan sharp) */
  CHROMATIC: ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'],

  /** Display name untuk nada (menggunakan flat dimana konvensional) */
  NOTE_DISPLAY: {
    'C':'C', 'C#':'C♯', 'D':'D', 'D#':'E♭', 'E':'E', 'F':'F',
    'F#':'F♯', 'G':'G', 'G#':'A♭', 'A':'A', 'A#':'B♭', 'B':'B'
  },

  /** Frekuensi nada dalam Hz (oktaf 4) untuk Web Audio API */
  NOTE_FREQ: {
    'C':261.63, 'C#':277.18, 'D':293.66, 'D#':311.13, 'E':329.63, 'F':349.23,
    'F#':369.99, 'G':392.00, 'G#':415.30, 'A':440.00, 'A#':466.16, 'B':493.88
  },

  /** Semua root yang tersedia (12 kromatik) */
  ROOTS: ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'],

  /** Tipe chord dan label display */
  CHORD_TYPES: {
    'maj7': 'Major 7',
    '7':    'Dominant 7',
    'm7':   'Minor 7',
    'm7b5': 'Half-Dim (m7♭5)',
    'dim7': 'Diminished 7',
    '6':    'Major 6',
    'm6':   'Minor 6',
  },

  /** Interval (semitone dari root) untuk chord tone tiap tipe chord */
  CHORD_TONE_INTERVALS: {
    'maj7': [0, 4, 7, 11],
    '7':    [0, 4, 7, 10],
    'm7':   [0, 3, 7, 10],
    'm7b5': [0, 3, 6, 10],
    'dim7': [0, 3, 6, 9],
    '6':    [0, 4, 7, 9],
    'm6':   [0, 3, 7, 9],
  },

  /** Normalisasi ejaan flat → sharp. */
  FLAT_TO_SHARP: { 'Db':'C#','Eb':'D#','Gb':'F#','Ab':'G#','Bb':'A#' },

  /**
   * Hitung chord tone (nama nada) dari root + tipe chord.
   * @param {string} root - misal "C", "F#"
   * @param {string} type - misal "maj7", "m7"
   * @returns {string[]} daftar nama nada chord tone
   */
  computeChordTones(root, type) {
    const rootIdx = this.CHROMATIC.indexOf(root);
    const intervals = this.CHORD_TONE_INTERVALS[type] || [];
    if (rootIdx < 0) return [];
    return intervals.map(iv => this.CHROMATIC[(rootIdx + iv) % 12]);
  },

  /**
   * Pecah nama chord ("Dm7", "C#maj7", "Bbm7b5") menjadi {root, type}.
   */
  parseChord(chordName) {
    if (!chordName) return null;
    let root, type;
    if (chordName.length >= 2 && (chordName[1] === '#' || chordName[1] === 'b')) {
      root = chordName.slice(0, 2);
      type = chordName.slice(2);
    } else {
      root = chordName.slice(0, 1);
      type = chordName.slice(1);
    }
    root = this.FLAT_TO_SHARP[root] || root;
    if (this.CHROMATIC.indexOf(root) < 0) return null;
    return { root, type };
  },

  /** Chord tone dari nama chord lengkap ("Cmaj7" → ["C","E","G","B"]). */
  chordTones(chordName) {
    const p = this.parseChord(chordName);
    return p ? this.computeChordTones(p.root, p.type) : [];
  },
};
