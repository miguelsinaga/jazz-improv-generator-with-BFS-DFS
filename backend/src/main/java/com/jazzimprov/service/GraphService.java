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
        // Node graf terdiri dari tiga kelas nada:
        //  - CHORD tone  : nada pembentuk chord (titik mendarat yang stabil).
        //  - SCALE tone  : nada lain pada BEBOP SCALE (skala + passing kromatik)
        //                  → bahan scalar run khas bebop, tetap in-context.
        //  - APPROACH    : nada kromatik pengepung (enclosure) di luar skala.
        // Contoh Cmaj7: chord {C,E,G,B} + scale {D,F,G#,A} + approach {C#,F#,A#}.
        List<String> chordTones = MusicConfig.getChordTones(chordName);
        List<String> bebop = MusicConfig.getBebopScale(chordName);
        List<String> approaches = MusicConfig.getApproachNotes(chordName);

        if (chordTones == null || chordTones.isEmpty() || bebop == null) {
            return null;
        }

        Set<String> chordSet = new LinkedHashSet<>(chordTones);
        Set<String> scaleSet = new LinkedHashSet<>(bebop);
        scaleSet.removeAll(chordSet); // SCALE = bebop scale di luar chord tone

        // Kumpulan semua node (urut: chord → scale → approach, tanpa duplikat)
        Set<String> allNotes = new LinkedHashSet<>(bebop);
        allNotes.addAll(approaches);

        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        for (String note : allNotes) {
            graph.addVertex(note);
        }

        for (String source : allNotes) {
            for (String target : allNotes) {
                if (source.equals(target)) continue;

                Double weight = edgeWeight(source, target, chordSet, scaleSet);
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
     * melodik bebop (scalar run, chromatic approach & enclosure).
     *
     * Aturan utama:
     * - Nada NON-chord hanya bergerak melangkah (≤ 2 semitone) → garis tetap halus.
     * - Mendarat di chord tone selalu kuat (resolve), terutama ½ langkah.
     * - Antar chord tone boleh melompat (arpeggiation) sesuai bobot interval.
     * - Nada skala saling sambung membentuk scalar run; nada kromatik approach
     *   resolve/enclosure seperti sebelumnya.
     *
     * @return bobot, atau null bila transisi tidak diberi edge.
     */
    private Double edgeWeight(String source, String target,
                              Set<String> chordSet, Set<String> scaleSet) {
        int iv = MusicConfig.intervalSemitones(source, target);
        boolean srcChord = chordSet.contains(source);
        boolean tgtChord = chordSet.contains(target);

        // Nada non-chord (skala/approach) hanya boleh melangkah, tidak melompat.
        if (!srcChord && iv > 2) {
            return null;
        }

        if (tgtChord) {
            if (srcChord) {
                return MusicConfig.INTERVAL_WEIGHTS.get(iv); // arpeggiation
            }
            // Non-chord resolve menuju chord tone.
            if (iv == 1) return MusicConfig.RESOLUTION_WEIGHT;          // ½ langkah (terkuat)
            if (iv == 2 && scaleSet.contains(source)) {
                return MusicConfig.SCALE_RESOLUTION_WEIGHT;            // langkah penuh diatonis
            }
            return null;
        }

        // target = non-chord
        if (srcChord) {
            // Keluar dari chord tone secara melangkah (mulai run / enclosure).
            if (iv == 1 || iv == 2) {
                return scaleSet.contains(target)
                    ? MusicConfig.SCALE_STEP_WEIGHT       // ke nada skala (in-context)
                    : MusicConfig.APPROACH_ENTRY_WEIGHT;  // ke nada kromatik approach
            }
            return null;
        }

        // Non-chord → non-chord (lanjut run / enclosure / passing).
        if (scaleSet.contains(target) && iv <= 2) {
            return MusicConfig.SCALE_RUN_WEIGHT;          // scalar run khas bebop
        }
        if (MusicConfig.isEnclosurePair(source, target, chordSet)) {
            return MusicConfig.ENCLOSURE_WEIGHT;
        }
        if (iv == 1) {
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
