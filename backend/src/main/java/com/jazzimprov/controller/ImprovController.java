package com.jazzimprov.controller;

import com.jazzimprov.config.MusicConfig;
import com.jazzimprov.model.ImprovRequest;
import com.jazzimprov.model.ImprovResponse;
import com.jazzimprov.service.GraphService;
import com.jazzimprov.service.RhythmService;
import com.jazzimprov.service.TraversalService;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST Controller untuk Jazz Improvisation Generator.
 *
 * Endpoints:
 * - POST /api/generate  → Generate improvisasi jazz berdasarkan parameter
 * - GET  /api/chords    → Daftar chord yang tersedia
 * - GET  /api/health    → Health check
 */
@RestController
@RequestMapping("/api")
public class ImprovController {

    @Autowired
    private GraphService graphService;

    @Autowired
    private TraversalService traversalService;

    @Autowired
    private RhythmService rhythmService;

    /**
     * POST /api/generate
     *
     * Alur proses:
     * 1. Validasi chord progression dari request
     * 2. Untuk setiap chord, build graf nada berbobot
     * 3. Jalankan BFS atau DFS pada graf
     * 4. Gabungkan semua sequence nada dari setiap chord
     * 5. Assign durasi sesuai rhythm mode
     * 6. Hitung statistik
     * 7. Return response dengan notes, durations, dan stats
     *
     * @param request ImprovRequest berisi chordProgression, algorithm, randomness, rhythmMode
     * @return ImprovResponse berisi notes, durations, stats
     */
    @PostMapping("/generate")
    public ResponseEntity<ImprovResponse> generate(@RequestBody ImprovRequest request) {
        // Validasi input
        List<String> chords = request.getChordProgression();
        if (chords == null || chords.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validasi semua chord
        for (String chord : chords) {
            if (!MusicConfig.isValidChord(chord)) {
                return ResponseEntity.badRequest().build();
            }
        }

        String algorithm = request.getAlgorithm() != null ? request.getAlgorithm() : "BFS";
        double randomness = Math.max(0.0, Math.min(1.0, request.getRandomness()));
        String rhythmMode = request.getRhythmMode() != null ? request.getRhythmMode() : "swing";
        int maxNotes = request.getMaxNotes() > 0 ? request.getMaxNotes() : MusicConfig.DEFAULT_MAX_NOTES;
        int tempo = request.getTempo() > 0 ? request.getTempo() : MusicConfig.DEFAULT_TEMPO;

        // Generate nada untuk setiap chord dalam progression
        List<String> allNotes = new ArrayList<>();
        // Chord yang aktif untuk tiap nada (sejajar dengan allNotes) — dipakai untuk iringan akor
        List<String> chordSequence = new ArrayList<>();
        int notesPerChord = maxNotes / chords.size();
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> lastGraph = null;

        for (int c = 0; c < chords.size(); c++) {
            String chordName = chords.get(c);

            // Build graf untuk chord ini
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph =
                graphService.buildGraph(chordName);

            if (graph == null) continue;
            lastGraph = graph;

            // Tentukan jumlah nada untuk chord ini
            int count = (c == chords.size() - 1)
                ? maxNotes - allNotes.size()  // Chord terakhir ambil sisa
                : notesPerChord;

            // Tentukan nada awal (root chord tone)
            List<String> chordTones = MusicConfig.getChordTones(chordName);
            String startNote = chordTones.get(0);

            // Jalankan traversal
            List<String> notes;
            if ("DFS".equalsIgnoreCase(algorithm)) {
                notes = traversalService.dfsImprovise(graph, startNote, count, randomness);
            } else {
                notes = traversalService.bfsImprovise(graph, startNote, count, randomness);
            }

            allNotes.addAll(notes);
            // Tandai setiap nada ini dengan chord yang sedang berbunyi
            for (int n = 0; n < notes.size(); n++) {
                chordSequence.add(chordName);
            }
        }

        // Assign durasi
        List<Double> durations;
        if ("weight".equalsIgnoreCase(rhythmMode) && lastGraph != null) {
            durations = rhythmService.assignByWeight(lastGraph, allNotes);
        } else {
            durations = rhythmService.assignSwing(allNotes);
        }

        // Hitung statistik
        Map<String, Object> stats = rhythmService.computeStats(allNotes);

        // Ambil info graf terakhir
        Map<String, Object> graphInfo = lastGraph != null
            ? graphService.getGraphInfo(lastGraph)
            : Map.of();

        // Build response
        ImprovResponse response = new ImprovResponse();
        response.setNotes(allNotes);
        response.setDurations(durations);
        response.setChordSequence(chordSequence);
        response.setTempoBpm(tempo);
        response.setAlgorithm(algorithm.toUpperCase());
        response.setRhythmMode(rhythmMode);
        response.setStats(stats);
        response.setGraphInfo(graphInfo);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/chords
     *
     * Mengembalikan daftar chord yang tersedia, dikelompokkan berdasarkan tipe.
     *
     * @return Map berisi daftar chord per kategori (minor7, dominant7, major7)
     */
    @GetMapping("/chords")
    public ResponseEntity<Map<String, Object>> getAvailableChords() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Semua 12 root kromatik dan tipe chord yang didukung generator.
        result.put("roots", List.of(
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"));
        result.put("types", List.of("maj7", "7", "m7", "m7b5", "dim7", "6", "m6"));

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/health
     *
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "engine", "Syncopate Jazz Improv v1.0"
        ));
    }
}
