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
        // Node graf = chord tone + nada KROMATIK pengepung (approach/enclosure).
        // Chord tone menjaga melodi tetap sesuai konteks chord, sementara nada
        // kromatik memberi rasa bebop (enclosure) — dengan bobot yang membuat
        // nada kromatik selalu cenderung resolve ke chord tone.
        // Contoh Cmaj7: chord tone {C,E,G,B} + approach {C#,D#,F,F#,G#,A#,...}.
        List<String> chordTones = MusicConfig.getChordTones(chordName);
        List<String> approaches = MusicConfig.getApproachNotes(chordName);

        if (chordTones == null || chordTones.isEmpty()) {
            return null;
        }

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        List<String> allNotes = new ArrayList<>(chordTones);
        allNotes.addAll(approaches);
        for (String note : allNotes) {
            graph.addVertex(note);
        }

        // Buat edge berbobot untuk tiap pasang nada.
        for (String source : allNotes) {
            for (String target : allNotes) {
                if (source.equals(target)) continue;

                Double weight = edgeWeight(source, target, chordTones);
                if (weight != null) {
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
     * Menghitung bobot transisi {@code source → target} berdasarkan teori
     * melodik jazz (chromatic approach & enclosure).
     *
     * Prioritas (bobot tinggi = lebih disukai):
     * 1. approach → chord tone (resolve setengah langkah)  → RESOLUTION_WEIGHT
     * 2. approach ↔ approach pengepung target sama (enclosure) → ENCLOSURE_WEIGHT
     * 3. chord tone → approach (memulai enclosure, ½ langkah) → APPROACH_ENTRY_WEIGHT
     * 4. approach → approach kromatik biasa (½ langkah)     → CHROMATIC_PASSING_WEIGHT
     * 5. chord tone → chord tone                            → INTERVAL_WEIGHTS
     *
     * @return bobot, atau null bila transisi tidak diberi edge.
     */
    private Double edgeWeight(String source, String target, List<String> chordTones) {
        boolean srcTone = chordTones.contains(source);
        boolean tgtTone = chordTones.contains(target);
        int interval = MusicConfig.intervalSemitones(source, target);

        if (srcTone && tgtTone) {
            // Gerak antar chord tone — pakai bobot interval melodik biasa.
            return MusicConfig.INTERVAL_WEIGHTS.get(interval);
        }

        if (!srcTone && tgtTone) {
            // Nada kromatik menuju chord tone: resolve ½ langkah = paling kuat.
            if (interval == 1) return MusicConfig.RESOLUTION_WEIGHT;
            return null; // hindari lompatan dari approach langsung ke chord tone jauh
        }

        if (srcTone && !tgtTone) {
            // Chord tone melangkah keluar ke nada kromatik (½ langkah) → mulai enclosure.
            if (interval == 1) return MusicConfig.APPROACH_ENTRY_WEIGHT;
            return null;
        }

        // Keduanya approach note.
        if (MusicConfig.isEnclosurePair(source, target, chordTones)) {
            return MusicConfig.ENCLOSURE_WEIGHT;
        }
        if (interval == 1) {
            return MusicConfig.CHROMATIC_PASSING_WEIGHT;
        }
        return null;
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
