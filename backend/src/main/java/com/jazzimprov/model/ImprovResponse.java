package com.jazzimprov.model;

import java.util.List;
import java.util.Map;

/**
 * DTO untuk response generasi improvisasi.
 * Dikembalikan ke frontend setelah proses BFS/DFS selesai.
 */
public class ImprovResponse {

    /** Sequence nada yang dihasilkan, misal ["D", "F", "A", "G", ...] */
    private List<String> notes;

    /** Durasi tiap nada dalam satuan beat, misal [0.375, 0.125, ...] */
    private List<Double> durations;

    /** Nama chord yang aktif untuk tiap nada (sejajar dengan notes), untuk iringan akor */
    private List<String> chordSequence;

    /** Tempo dalam BPM */
    private int tempoBpm;

    /** Algoritma yang digunakan: "BFS" atau "DFS" */
    private String algorithm;

    /** Mode rhythm yang digunakan: "weight" atau "swing" */
    private String rhythmMode;

    /** Statistik generasi (uniqueNotes, avgInterval, complexity, totalNotes) */
    private Map<String, Object> stats;

    /** Info graf: jumlah node dan edge */
    private Map<String, Object> graphInfo;

    // === Constructors ===

    public ImprovResponse() {}

    // === Getters & Setters ===

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }

    public List<Double> getDurations() {
        return durations;
    }

    public void setDurations(List<Double> durations) {
        this.durations = durations;
    }

    public List<String> getChordSequence() {
        return chordSequence;
    }

    public void setChordSequence(List<String> chordSequence) {
        this.chordSequence = chordSequence;
    }

    public int getTempoBpm() {
        return tempoBpm;
    }

    public void setTempoBpm(int tempoBpm) {
        this.tempoBpm = tempoBpm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getRhythmMode() {
        return rhythmMode;
    }

    public void setRhythmMode(String rhythmMode) {
        this.rhythmMode = rhythmMode;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }

    public Map<String, Object> getGraphInfo() {
        return graphInfo;
    }

    public void setGraphInfo(Map<String, Object> graphInfo) {
        this.graphInfo = graphInfo;
    }
}
