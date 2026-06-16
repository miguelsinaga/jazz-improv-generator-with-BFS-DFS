/**
 * config.js — Konstanta & helper musik untuk frontend.
 *
 * Mendukung SEGALA nada dasar (12 root kromatik) dan beberapa tipe chord.
 * Chord tone dihitung secara programatik (mirror dari MusicConfig di backend)
 * agar display timeline & iringan akor bekerja untuk chord apa pun.
 */

const Config = {

  /** URL backend API */
  API_BASE: 'http://localhost:8080/api',

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
};
