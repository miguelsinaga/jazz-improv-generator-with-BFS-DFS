package com.jazzimprov.service;

import com.jazzimprov.config.MusicConfig;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service untuk menentukan durasi (dalam beat) setiap nada dalam sequence.
 *
 * Dua mode tersedia:
 *
 * 1. Weight-based: durasi ditentukan dari bobot edge graf
 *    - Chord tone (bobot tinggi) → quarter note (0.5 beat)
 *    - Passing tone (bobot rendah) → eighth note (0.25 beat)
 *
 * 2. Swing: pola durasi berulang khas jazz
 *    - Long-short-long-short: [0.375, 0.125, 0.375, 0.125, ...]
 *    - Merepresentasikan triplet feel (2/3 + 1/3 dari beat)
 */
@Service
public class RhythmService {

    /**
     * Assign durasi berdasarkan bobot edge antar nada berurutan.
     *
     * Logika:
     * - Nada pertama selalu quarter note (0.5 beat)
     * - Untuk nada ke-i, cek bobot edge dari nada (i-1) ke nada (i)
     * - Jika bobot >= 1.0 (chord tone) → 0.5 beat (quarter note)
     * - Jika bobot < 1.0 (passing tone) → 0.25 beat (eighth note)
     * - Jika tidak ada edge (nada dari chord berbeda) → 0.375 beat
     *
     * @param graph        Graf nada
     * @param noteSequence Sequence nada hasil traversal
     * @return List durasi dalam beat, sepanjang noteSequence
     */
    public List<Double> assignByWeight(
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
            List<String> noteSequence) {

        List<Double> durations = new ArrayList<>();
        durations.add(0.5); // Nada pertama selalu quarter note

        for (int i = 1; i < noteSequence.size(); i++) {
            String prev = noteSequence.get(i - 1);
            String curr = noteSequence.get(i);

            // Cek apakah ada edge dari prev ke curr
            if (graph.containsVertex(prev) && graph.containsVertex(curr)) {
                DefaultWeightedEdge edge = graph.getEdge(prev, curr);
                if (edge != null) {
                    double weight = graph.getEdgeWeight(edge);
                    // Chord tone (bobot tinggi) → durasi lebih panjang
                    durations.add(weight >= 1.0 ? 0.5 : 0.25);
                } else {
                    // Tidak ada edge langsung — transisi antar chord
                    durations.add(0.375);
                }
            } else {
                // Nada dari chord berbeda (vertex tidak ada di graf ini)
                durations.add(0.375);
            }
        }

        return durations;
    }

    /**
     * Assign durasi dengan pola swing jazz.
     *
     * Pattern: alternating 0.375 dan 0.125 beat.
     * Ini merepresentasikan swing eighth notes (triplet feel):
     * - Beat ganjil: 2/3 dari satu beat
     * - Beat genap: 1/3 dari satu beat
     *
     * Efek: ritme "long-short-long-short" yang khas jazz.
     *
     * @param noteSequence Sequence nada (dibutuhkan hanya untuk panjangnya)
     * @return List durasi dalam beat, sepanjang noteSequence
     */
    public List<Double> assignSwing(List<String> noteSequence) {
        List<Double> durations = new ArrayList<>();
        for (int i = 0; i < noteSequence.size(); i++) {
            // Alternating: long (0.375) - short (0.125)
            durations.add(i % 2 == 0 ? 0.375 : 0.125);
        }
        return durations;
    }

    /**
     * Menghitung statistik dari sequence nada yang dihasilkan.
     *
     * @param noteSequence Sequence nada
     * @return Map berisi uniqueNotes, avgInterval, complexity, totalNotes
     */
    public Map<String, Object> computeStats(List<String> noteSequence) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Unique notes
        Set<String> unique = new HashSet<>(noteSequence);
        stats.put("uniqueNotes", unique.size());

        // Average interval
        double totalInterval = 0;
        for (int i = 1; i < noteSequence.size(); i++) {
            totalInterval += MusicConfig.intervalSemitones(
                noteSequence.get(i - 1), noteSequence.get(i));
        }
        double avgInterval = noteSequence.size() > 1
            ? totalInterval / (noteSequence.size() - 1) : 0;
        stats.put("avgInterval", Math.round(avgInterval * 10.0) / 10.0);

        // Complexity rating
        String complexity;
        if (unique.size() >= 6 && avgInterval >= 2.0) {
            complexity = "High";
        } else if (unique.size() >= 5 || avgInterval >= 1.5) {
            complexity = "Med-High";
        } else if (unique.size() >= 4) {
            complexity = "Medium";
        } else {
            complexity = "Low";
        }
        stats.put("complexity", complexity);

        // Total notes
        stats.put("totalNotes", noteSequence.size());

        return stats;
    }
}
