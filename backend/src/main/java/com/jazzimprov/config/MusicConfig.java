package com.jazzimprov.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Konfigurasi musik untuk Jazz Improv Generator.
 *
 * Berbeda dengan versi awal yang meng-hardcode skala & chord tone untuk
 * sejumlah chord tertentu, versi ini MENGHITUNG skala dan chord tone secara
 * programatik dari (root + tipe chord). Dengan begitu generator bisa bermain
 * dari SEGALA nada dasar (12 root kromatik) dan SEGALA chord progression
 * untuk tipe chord yang didukung.
 *
 * Teori musik yang dipakai (chord–scale theory):
 * - maj7  → Ionian      (major scale)
 * - 7     → Mixolydian
 * - m7    → Dorian
 * - m7b5  → Locrian
 * - dim7  → Whole-half diminished
 * - 6     → Ionian
 * - m6    → Melodic minor
 */
public final class MusicConfig {

    private MusicConfig() {}

    /** 12 nada kromatik (ejaan sharp). */
    public static final String[] CHROMATIC_NOTES = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    /** Normalisasi ejaan flat → sharp agar root bisa ditulis "Bb", "Eb", dst. */
    private static final Map<String, String> FLAT_TO_SHARP = Map.of(
        "Db", "C#", "Eb", "D#", "Gb", "F#", "Ab", "G#", "Bb", "A#"
    );

    /**
     * Interval (semitone dari root) untuk SKALA tiap tipe chord.
     * Menentukan node mana saja yang tersedia dalam graf.
     */
    private static final Map<String, int[]> SCALE_INTERVALS = Map.of(
        "maj7", new int[]{0, 2, 4, 5, 7, 9, 11},   // Ionian
        "7",    new int[]{0, 2, 4, 5, 7, 9, 10},   // Mixolydian
        "m7",   new int[]{0, 2, 3, 5, 7, 9, 10},   // Dorian
        "m7b5", new int[]{0, 1, 3, 5, 6, 8, 10},   // Locrian
        "dim7", new int[]{0, 2, 3, 5, 6, 8, 9, 11},// Whole-half diminished
        "6",    new int[]{0, 2, 4, 5, 7, 9, 11},   // Ionian
        "m6",   new int[]{0, 2, 3, 5, 7, 9, 11}    // Melodic minor
    );

    /** Interval (semitone dari root) untuk CHORD TONE tiap tipe chord. */
    private static final Map<String, int[]> CHORD_TONE_INTERVALS = Map.of(
        "maj7", new int[]{0, 4, 7, 11},
        "7",    new int[]{0, 4, 7, 10},
        "m7",   new int[]{0, 3, 7, 10},
        "m7b5", new int[]{0, 3, 6, 10},
        "dim7", new int[]{0, 3, 6, 9},
        "6",    new int[]{0, 4, 7, 9},
        "m6",   new int[]{0, 3, 7, 9}
    );

    /**
     * Bobot edge berdasarkan interval melodik (dalam semitone, sudah dilipat
     * ke 1..6 oleh {@link #intervalSemitones}).
     *
     * Teori: gerak melodik jazz paling natural saat STEPWISE (langkah 1–2
     * semitone). Lompatan makin besar makin "berat". Tritone (6) sengaja
     * diberi bobot kecil tapi tetap ada agar dominant chord punya warna.
     *
     * Catatan: versi lama memberi key untuk interval 7 (perfect 5th) yang tidak
     * pernah terpakai karena interval selalu dilipat ke <= 6, dan tidak punya
     * edge untuk tritone (6) sehingga sebagian transisi tidak terjangkau.
     * Versi ini memperbaiki keduanya.
     */
    public static final Map<Integer, Double> INTERVAL_WEIGHTS = Map.of(
        1, 0.9,   // Minor 2nd  — chromatic approach
        2, 1.0,   // Major 2nd  — langkah diatonik paling natural
        3, 0.7,   // Minor 3rd
        4, 0.55,  // Major 3rd
        5, 0.45,  // Perfect 4th / 5th (5 atau 7 semitone, dilipat ke 5)
        6, 0.3    // Tritone
    );

    /** Pengali bobot untuk transisi menuju chord tone. */
    public static final double CHORD_TONE_BONUS = 1.5;

    /** Jumlah default nada yang dihasilkan. */
    public static final int DEFAULT_MAX_NOTES = 32;

    /** Tempo default dalam BPM. */
    public static final int DEFAULT_TEMPO = 120;

    /**
     * Memecah nama chord menjadi {root, tipe}.
     * Contoh: "Dm7" → ["D","m7"], "C#maj7" → ["C#","maj7"], "Bbm7b5" → ["A#","m7b5"].
     * @return array [root, tipe] (root sudah dinormalisasi ke sharp), atau null jika tidak valid.
     */
    public static String[] parseChord(String chordName) {
        if (chordName == null || chordName.isEmpty()) return null;

        // Root bisa 1 atau 2 karakter (nada + aksidental # / b)
        String root;
        String type;
        if (chordName.length() >= 2 && (chordName.charAt(1) == '#' || chordName.charAt(1) == 'b')) {
            root = chordName.substring(0, 2);
            type = chordName.substring(2);
        } else {
            root = chordName.substring(0, 1);
            type = chordName.substring(1);
        }

        root = FLAT_TO_SHARP.getOrDefault(root, root);
        if (chromaticIndex(root) < 0) return null;
        if (!SCALE_INTERVALS.containsKey(type)) return null;

        return new String[]{root, type};
    }

    /**
     * Membangun daftar nada dari root + array interval semitone.
     */
    private static List<String> notesFromIntervals(String root, int[] intervals) {
        int rootIdx = chromaticIndex(root);
        List<String> notes = new ArrayList<>(intervals.length);
        for (int interval : intervals) {
            notes.add(CHROMATIC_NOTES[(rootIdx + interval) % 12]);
        }
        return notes;
    }

    /**
     * Skala (chord-scale) untuk chord tertentu, dihitung dari root + tipe.
     * @return list nada, atau null jika chord tidak valid.
     */
    public static List<String> getScale(String chordName) {
        String[] parsed = parseChord(chordName);
        if (parsed == null) return null;
        return notesFromIntervals(parsed[0], SCALE_INTERVALS.get(parsed[1]));
    }

    /**
     * Chord tone untuk chord tertentu, dihitung dari root + tipe.
     * @return list nada (root, 3rd, 5th, 7th/6th), atau null jika chord tidak valid.
     */
    public static List<String> getChordTones(String chordName) {
        String[] parsed = parseChord(chordName);
        if (parsed == null) return null;
        return notesFromIntervals(parsed[0], CHORD_TONE_INTERVALS.get(parsed[1]));
    }

    /**
     * Mendapatkan index kromatik suatu nada (0-11).
     */
    public static int chromaticIndex(String note) {
        for (int i = 0; i < CHROMATIC_NOTES.length; i++) {
            if (CHROMATIC_NOTES[i].equals(note)) return i;
        }
        return -1;
    }

    /**
     * Menghitung interval terkecil antara dua nada (dalam semitone, 0..6).
     * Mempertimbangkan wrapping (C ke B = 1 semitone, bukan 11).
     */
    public static int intervalSemitones(String a, String b) {
        int ia = chromaticIndex(a);
        int ib = chromaticIndex(b);
        int diff = Math.abs(ib - ia);
        return Math.min(diff, 12 - diff);
    }

    /** Mengecek apakah chord name valid (root kromatik + tipe didukung). */
    public static boolean isValidChord(String chordName) {
        return parseChord(chordName) != null;
    }
}
