package com.jazzimprov.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class MusicConfig {

    private MusicConfig() {} 

    public static final String[] CHROMATIC_NOTES = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };


    public static final Map<String, List<String>> CHORD_SCALES;


    public static final Map<String, List<String>> CHORD_TONES;

    /**
     * Bobot edge berdasarkan interval (dalam semitone).
     * Interval yang lebih kecil (stepwise) mendapat bobot lebih tinggi
     * karena lebih natural dalam melodi jazz.
     * Hanya interval <= 7 semitone yang menghasilkan edge.
     */
    public static final Map<Integer, Double> INTERVAL_WEIGHTS = Map.of(
        1, 0.9,   // Minor 2nd 
        2, 1.0,   // Major 2nd 
        3, 0.8,   // Minor 3rd
        4, 0.7,   // Major 3rd
        5, 0.6,   // Perfect 4th
        7, 0.5    // Perfect 5th
    );

    /** Pengali bobot untuk nada yang merupakan chord tone */
    public static final double CHORD_TONE_BONUS = 1.5;

    /** Jumlah default nada yang dihasilkan */
    public static final int DEFAULT_MAX_NOTES = 32;

    /** Tempo default dalam BPM */
    public static final int DEFAULT_TEMPO = 120;

    static {
        // Initialize chord scales
        Map<String, List<String>> scales = new HashMap<>();
        // Minor 7 chords — Dorian mode
        scales.put("Cm7",  List.of("C", "D", "D#", "F", "G", "G#", "A#"));
        scales.put("Dm7",  List.of("D", "E", "F", "G", "A", "A#", "C"));
        scales.put("Em7",  List.of("E", "F#", "G", "A", "B", "C", "D"));
        scales.put("Fm7",  List.of("F", "G", "G#", "A#", "C", "C#", "D#"));
        scales.put("Gm7",  List.of("G", "A", "A#", "C", "D", "D#", "F"));
        scales.put("Am7",  List.of("A", "B", "C", "D", "E", "F", "G"));
        scales.put("Bm7",  List.of("B", "C#", "D", "E", "F#", "G", "A"));
        // Dominant 7 chords — Mixolydian mode
        scales.put("C7",   List.of("C", "D", "E", "F", "G", "A", "A#"));
        scales.put("D7",   List.of("D", "E", "F#", "G", "A", "B", "C"));
        scales.put("E7",   List.of("E", "F#", "G#", "A", "B", "C#", "D"));
        scales.put("F7",   List.of("F", "G", "A", "A#", "C", "D", "D#"));
        scales.put("G7",   List.of("G", "A", "B", "C", "D", "E", "F"));
        scales.put("A7",   List.of("A", "B", "C#", "D", "E", "F#", "G"));
        scales.put("Bb7",  List.of("A#", "C", "D", "D#", "F", "G", "G#"));
        // Major 7 chords — Ionian mode
        scales.put("Cmaj7", List.of("C", "D", "E", "F", "G", "A", "B"));
        scales.put("Dmaj7", List.of("D", "E", "F#", "G", "A", "B", "C#"));
        scales.put("Emaj7", List.of("E", "F#", "G#", "A", "B", "C#", "D#"));
        scales.put("Fmaj7", List.of("F", "G", "A", "A#", "C", "D", "E"));
        scales.put("Gmaj7", List.of("G", "A", "B", "C", "D", "E", "F#"));
        scales.put("Amaj7", List.of("A", "B", "C#", "D", "E", "F#", "G#"));
        scales.put("Bbmaj7", List.of("A#", "C", "D", "D#", "F", "G", "A"));
        CHORD_SCALES = Collections.unmodifiableMap(scales);

        // Initialize chord tones
        Map<String, List<String>> tones = new HashMap<>();
        // Minor 7
        tones.put("Cm7",  List.of("C", "D#", "G", "A#"));
        tones.put("Dm7",  List.of("D", "F", "A", "C"));
        tones.put("Em7",  List.of("E", "G", "B", "D"));
        tones.put("Fm7",  List.of("F", "G#", "C", "D#"));
        tones.put("Gm7",  List.of("G", "A#", "D", "F"));
        tones.put("Am7",  List.of("A", "C", "E", "G"));
        tones.put("Bm7",  List.of("B", "D", "F#", "A"));
        // Dominant 7
        tones.put("C7",   List.of("C", "E", "G", "A#"));
        tones.put("D7",   List.of("D", "F#", "A", "C"));
        tones.put("E7",   List.of("E", "G#", "B", "D"));
        tones.put("F7",   List.of("F", "A", "C", "D#"));
        tones.put("G7",   List.of("G", "B", "D", "F"));
        tones.put("A7",   List.of("A", "C#", "E", "G"));
        tones.put("Bb7",  List.of("A#", "D", "F", "G#"));
        // Major 7
        tones.put("Cmaj7", List.of("C", "E", "G", "B"));
        tones.put("Dmaj7", List.of("D", "F#", "A", "C#"));
        tones.put("Emaj7", List.of("E", "G#", "B", "D#"));
        tones.put("Fmaj7", List.of("F", "A", "C", "E"));
        tones.put("Gmaj7", List.of("G", "B", "D", "F#"));
        tones.put("Amaj7", List.of("A", "C#", "E", "G#"));
        tones.put("Bbmaj7", List.of("A#", "D", "F", "A"));
        CHORD_TONES = Collections.unmodifiableMap(tones);
    }

    /**
     * Mendapatkan index kromatik suatu nada (0-11).
     * @param note nama nada (misal "C", "D#")
     * @return index 0-11, atau -1 jika tidak ditemukan
     */
    public static int chromaticIndex(String note) {
        for (int i = 0; i < CHROMATIC_NOTES.length; i++) {
            if (CHROMATIC_NOTES[i].equals(note)) return i;
        }
        return -1;
    }

    /**
     * Menghitung interval terkecil antara dua nada (dalam semitone).
     * Mempertimbangkan wrapping (misal C ke B = 1 semitone, bukan 11).
     */
    public static int intervalSemitones(String a, String b) {
        int ia = chromaticIndex(a);
        int ib = chromaticIndex(b);
        int diff = Math.abs(ib - ia);
        return Math.min(diff, 12 - diff);
    }

    /** Mengecek apakah chord name valid (terdaftar di CHORD_SCALES) */
    public static boolean isValidChord(String chordName) {
        return CHORD_SCALES.containsKey(chordName);
    }
}
