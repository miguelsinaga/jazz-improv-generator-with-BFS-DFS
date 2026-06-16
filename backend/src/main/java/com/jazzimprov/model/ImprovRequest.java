package com.jazzimprov.model;

import java.util.List;

/**
 * DTO untuk request generasi improvisasi.
 * Dikirim dari frontend via POST /api/generate.
 */
public class ImprovRequest {

    /** Chord progression, misal ["Dm7", "G7", "Cmaj7"] */
    private List<String> chordProgression;

    /** Algoritma traversal: "BFS" atau "DFS" */
    private String algorithm;

    /** Tingkat randomness: 0.0 (deterministik) sampai 1.0 (chaotic) */
    private double randomness;

    /** Mode rhythm: "weight" (berbasis bobot edge) atau "swing" (triplet feel) */
    private String rhythmMode;

    /** Jumlah nada yang dihasilkan (opsional, default 32) */
    private int maxNotes;

    /** Tempo dalam BPM (opsional, default 120) */
    private int tempo;

    // === Constructors ===

    public ImprovRequest() {
        this.maxNotes = 32;
        this.randomness = 0.3;
        this.algorithm = "BFS";
        this.rhythmMode = "swing";
        this.tempo = 120;
    }

    // === Getters & Setters ===

    public List<String> getChordProgression() {
        return chordProgression;
    }

    public void setChordProgression(List<String> chordProgression) {
        this.chordProgression = chordProgression;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public double getRandomness() {
        return randomness;
    }

    public void setRandomness(double randomness) {
        this.randomness = randomness;
    }

    public String getRhythmMode() {
        return rhythmMode;
    }

    public void setRhythmMode(String rhythmMode) {
        this.rhythmMode = rhythmMode;
    }

    public int getMaxNotes() {
        return maxNotes;
    }

    public void setMaxNotes(int maxNotes) {
        this.maxNotes = maxNotes;
    }

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        this.tempo = tempo;
    }
}
