package com.jazzimprov.service;

import com.jazzimprov.config.MusicConfig;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service untuk membangun graf nada jazz berbobot.
 *
 * Graf direpresentasikan sebagai DefaultDirectedWeightedGraph dari JGraphT:
 * - Node = nada dalam skala jazz (String)
 * - Edge = transisi valid antar nada (interval <= 7 semitone)
 * - Bobot = INTERVAL_WEIGHTS[interval] * CHORD_TONE_BONUS (jika target chord tone)
 *
 * Referensi teori:
 * - Dorian mode untuk chord minor 7
 * - Mixolydian mode untuk chord dominant 7
 * - Ionian mode untuk chord major 7
 */
@Service
public class GraphService {

    /**
     * Membangun graf berarah berbobot untuk satu chord.
     *
     * Proses:
     * 1. Ambil skala dan chord tones berdasarkan nama chord
     * 2. Buat node untuk setiap nada dalam skala
     * 3. Untuk setiap pasang nada, hitung interval
     * 4. Jika interval ada di INTERVAL_WEIGHTS, buat edge dua arah
     * 5. Bobot edge = weight dasar * bonus chord tone
     *
     * @param chordName Nama chord (misal "Dm7", "G7", "Cmaj7")
     * @return Graf berarah berbobot, atau null jika chord tidak valid
     */
    public DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> buildGraph(String chordName) {
        List<String> scale = MusicConfig.getScale(chordName);
        List<String> chordTones = MusicConfig.getChordTones(chordName);

        if (scale == null || chordTones == null) {
            return null;
        }

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        // Tambahkan semua nada dalam skala sebagai node
        for (String note : scale) {
            graph.addVertex(note);
        }

        // Buat edge untuk setiap pasang nada dengan interval valid
        for (int i = 0; i < scale.size(); i++) {
            for (int j = 0; j < scale.size(); j++) {
                if (i == j) continue;

                String source = scale.get(i);
                String target = scale.get(j);
                int interval = MusicConfig.intervalSemitones(source, target);

                Double baseWeight = MusicConfig.INTERVAL_WEIGHTS.get(interval);
                if (baseWeight != null) {
                    // Berikan bonus jika nada target adalah chord tone
                    double weight = baseWeight;
                    if (chordTones.contains(target)) {
                        weight *= MusicConfig.CHORD_TONE_BONUS;
                    }

                    DefaultWeightedEdge edge = graph.addEdge(source, target);
                    if (edge != null) {
                        graph.setEdgeWeight(edge, weight);
                    }
                }
            }
        }

        return graph;
    }

    /**
     * Mendapatkan semua tetangga dari suatu nada beserta bobotnya.
     *
     * @param graph Graf nada
     * @param note  Nada sumber
     * @return Map dari nada tujuan ke bobot edge
     */
    public Map<String, Double> getNeighbors(
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
            String note) {

        Map<String, Double> neighbors = new LinkedHashMap<>();
        Set<DefaultWeightedEdge> edges = graph.outgoingEdgesOf(note);

        for (DefaultWeightedEdge edge : edges) {
            String target = graph.getEdgeTarget(edge);
            double weight = graph.getEdgeWeight(edge);
            neighbors.put(target, weight);
        }

        return neighbors;
    }

    /**
     * Mendapatkan informasi ringkasan graf.
     *
     * @param graph Graf nada
     * @return Map berisi nodeCount dan edgeCount
     */
    public Map<String, Object> getGraphInfo(
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph) {

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("nodeCount", graph.vertexSet().size());
        info.put("edgeCount", graph.edgeSet().size());
        info.put("nodes", new ArrayList<>(graph.vertexSet()));
        return info;
    }
}
