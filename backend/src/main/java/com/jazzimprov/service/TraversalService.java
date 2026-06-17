package com.jazzimprov.service;

import com.jazzimprov.config.MusicConfig;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service yang mengimplementasikan algoritma BFS dan DFS
 * untuk men-generate sequence nada improvisasi jazz.
 *
 * Perbedaan kunci antara BFS dan DFS di konteks ini:
 *
 * DFS (Depth-First Search):
 * - Menggunakan Stack untuk menyimpan path
 * - Menghasilkan melodi yang "mengalir" — cenderung bergerak terus ke depan
 * - Backtrack hanya jika sudah tidak ada pilihan
 * - Karakter: melodi yang lebih linear dan kohesif
 *
 * BFS (Breadth-First Search):
 * - Menggunakan Queue untuk menyimpan frontier
 * - Mengeksplorasi semua nada yang berjarak 1 langkah sebelum lanjut
 * - Memilih dari semua kemungkinan pada level yang sama
 * - Karakter: melodi yang lebih "merata" dan exploratory
 *
 * Catatan: Karena ini generasi musik (bukan pathfinding), revisit node
 * diperbolehkan. Parameter randomness mengontrol seberapa deterministik
 * pilihan nada berikutnya.
 */
@Service
public class TraversalService {

    @Autowired
    private GraphService graphService;

    private final Random rng = new Random();

    /**
     * Generate sequence nada menggunakan DFS (Depth-First Search).
     *
     * Algoritma:
     * 1. Push startNote ke stack, tambahkan ke sequence
     * 2. Peek top of stack sebagai nada saat ini
     * 3. Ambil semua tetangga, pilih secara weighted random
     * 4. Push nada terpilih ke stack, tambahkan ke sequence
     * 5. Jika tidak ada tetangga valid, pop stack (backtrack)
     * 6. Ulangi hingga maxNotes tercapai
     *
     * @param graph      Graf berarah berbobot
     * @param startNote  Nada awal (biasanya root chord)
     * @param maxNotes   Jumlah nada yang dihasilkan
     * @param randomness 0.0 = selalu pilih bobot tertinggi, 1.0 = acak total
     * @return List nama nada hasil traversal DFS
     */
    public List<String> dfsImprovise(
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
            String startNote,
            int maxNotes,
            double randomness,
            String chordName) {

        Set<String> tones = new HashSet<>(MusicConfig.getChordTones(chordName));

        List<String> sequence = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>(); // Stack untuk DFS

        stack.push(startNote);
        sequence.add(startNote);

        while (sequence.size() < maxNotes && !stack.isEmpty()) {
            String current = stack.peek();
            Map<String, Double> neighbors = graphService.getNeighbors(graph, current);

            if (neighbors.isEmpty()) {
                // Backtrack: pop stack, kembali ke nada sebelumnya
                stack.pop();
                continue;
            }

            // Pilih nada berikutnya: bobot graf disesuaikan konteks melodik
            // (resolusi & penyelesaian enclosure) agar sequence paling cocok.
            Map<String, Double> scored = applyContext(neighbors, sequence, tones);
            String next = weightedRandomChoice(scored, randomness);

            if (next != null) {
                // DFS: push ke stack (go deeper)
                stack.push(next);
                sequence.add(next);
            } else {
                stack.pop();
            }
        }

        return sequence;
    }

    /**
     * Generate sequence nada menggunakan BFS (Breadth-First Search).
     *
     * Algoritma:
     * 1. Enqueue startNote, tambahkan ke sequence
     * 2. Dequeue nada dari depan queue (FIFO)
     * 3. Ambil semua tetangga (frontier level ini)
     * 4. Pilih 1-2 nada secara weighted random dari frontier
     * 5. Enqueue nada terpilih, tambahkan ke sequence
     * 6. Jika queue kosong tapi belum cukup nada, re-seed dari nada terakhir
     * 7. Ulangi hingga maxNotes tercapai
     *
     * @param graph      Graf berarah berbobot
     * @param startNote  Nada awal
     * @param maxNotes   Jumlah nada yang dihasilkan
     * @param randomness 0.0 = selalu pilih bobot tertinggi, 1.0 = acak total
     * @return List nama nada hasil traversal BFS
     */
    public List<String> bfsImprovise(
            DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph,
            String startNote,
            int maxNotes,
            double randomness,
            String chordName) {

        Set<String> tones = new HashSet<>(MusicConfig.getChordTones(chordName));

        List<String> sequence = new ArrayList<>();
        Queue<String> queue = new LinkedList<>(); // Queue untuk BFS

        queue.add(startNote);
        sequence.add(startNote);

        while (sequence.size() < maxNotes && !queue.isEmpty()) {
            String current = queue.poll(); // Dequeue (FIFO)
            Map<String, Double> neighbors = graphService.getNeighbors(graph, current);

            if (neighbors.isEmpty()) continue;

            // BFS: eksplorasi lebar — pilih 1-2 nada dari frontier
            int numToAdd = Math.min(2, neighbors.size());
            Set<String> added = new HashSet<>();

            for (int i = 0; i < numToAdd && sequence.size() < maxNotes; i++) {
                // Filter nada yang sudah dipilih di iterasi ini
                Map<String, Double> available = new LinkedHashMap<>();
                for (Map.Entry<String, Double> entry : neighbors.entrySet()) {
                    if (!added.contains(entry.getKey())) {
                        available.put(entry.getKey(), entry.getValue());
                    }
                }

                if (available.isEmpty()) break;

                // Sesuaikan bobot dengan konteks melodik sebelum memilih
                Map<String, Double> scored = applyContext(available, sequence, tones);
                String next = weightedRandomChoice(scored, randomness);
                if (next != null) {
                    added.add(next);
                    sequence.add(next);
                    queue.add(next); // Enqueue untuk eksplorasi berikutnya
                }
            }

            // Re-seed jika queue kosong tapi belum cukup nada
            if (queue.isEmpty() && sequence.size() < maxNotes) {
                queue.add(sequence.get(sequence.size() - 1));
            }
        }

        return sequence;
    }

    /**
     * Menyesuaikan bobot kandidat dengan KONTEKS melodik (1-2 nada terakhir).
     *
     * Bobot dasar dari graf hanya melihat "satu langkah". Di sini bobot tiap
     * kandidat dikalikan multiplier kontekstual agar generator memilih
     * note/sequence yang paling cocok pada saat itu — terutama untuk
     * menyelesaikan enclosure dan me-resolve nada kromatik ke chord tone.
     *
     * @param neighbors kandidat nada berikut + bobot graf
     * @param sequence  melodi yang sudah terbentuk (untuk melihat 2 nada terakhir)
     * @param tones     himpunan chord tone chord ini
     * @return map bobot yang sudah disesuaikan
     */
    private Map<String, Double> applyContext(
            Map<String, Double> neighbors, List<String> sequence, Set<String> tones) {

        String prev = sequence.isEmpty() ? null : sequence.get(sequence.size() - 1);
        String prev2 = sequence.size() >= 2 ? sequence.get(sequence.size() - 2) : null;

        Map<String, Double> adjusted = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : neighbors.entrySet()) {
            String cand = entry.getKey();
            double w = entry.getValue() * contextMultiplier(prev, prev2, cand, tones);
            adjusted.put(cand, Math.max(w, 0.0001));
        }
        return adjusted;
    }

    /**
     * Menghitung pengali bobot berdasarkan konteks melodik.
     *
     * Aturan (berbasis teori bebop enclosure):
     * - Jika 2 nada terakhir adalah pasangan pengepung (atas & bawah) dari
     *   chord tone, dan kandidat adalah chord tone target itu → SANGAT disukai
     *   (menyelesaikan enclosure).
     * - Jika nada terakhir adalah nada kromatik (approach):
     *     • kandidat chord tone ½ langkah → disukai (resolve);
     *     • kandidat approach yang mengepung target sama → disukai (lanjut enclosure);
     *     • kandidat approach lain → ditekan (hindari kromatik melantur).
     * - Jika nada terakhir chord tone & kandidat approach → sedikit didorong
     *   (sesekali memulai enclosure).
     */
    private double contextMultiplier(String prev, String prev2, String cand, Set<String> tones) {
        if (prev == null) return 1.0;

        boolean candTone = tones.contains(cand);
        boolean prevTone = tones.contains(prev);
        double mult = 1.0;

        // Penyelesaian enclosure penuh: prev2 & prev mengepung target, cand = target.
        if (prev2 != null && !prevTone && !tones.contains(prev2)
                && MusicConfig.isEnclosurePair(prev2, prev, tones)
                && candTone
                && MusicConfig.intervalSemitones(prev, cand) == 1
                && MusicConfig.intervalSemitones(prev2, cand) == 1) {
            mult *= 2.5;
        }

        if (!prevTone) {
            // prev = nada kromatik → HARUS "ingin" resolve agar tetap in-context.
            int iv = MusicConfig.intervalSemitones(prev, cand);
            if (candTone && iv == 1) {
                mult *= 3.0;                         // resolve ke chord tone (dominan)
            } else if (!candTone && MusicConfig.isEnclosurePair(prev, cand, tones)) {
                mult *= 2.0;                         // lanjut enclosure (atas↔bawah)
            } else {
                mult *= 0.25;                        // selain itu: tekan keras
            }
        }
        // prev = chord tone: tidak ada dorongan keluar. Greedy akan tetap pada
        // chord tone; nada kromatik (enclosure) muncul lewat eksplorasi randomness
        // lalu di-resolve greedy pada langkah berikutnya.

        // Hindari bolak-balik A-B-A-B (kembali ke nada 2 langkah lalu).
        if (prev2 != null && cand.equals(prev2)) {
            mult *= 0.5;
        }

        return mult;
    }

    /**
     * Memilih nada secara weighted random dari kandidat.
     *
     * Dengan probabilitas (1 - randomness), pilih berdasarkan bobot:
     * nada dengan bobot lebih tinggi lebih sering terpilih.
     *
     * Strategi GREEDY:
     * - Dengan probabilitas (1 - randomness): pilih kandidat dengan bobot
     *   (sudah disesuaikan konteks) TERTINGGI — selalu mengambil nada yang
     *   paling cocok dengan sequence saat itu. Inilah yang menjaga melodi
     *   tetap "in context" (resolve & menyelesaikan enclosure).
     * - Dengan probabilitas randomness: eksplorasi weighted-random untuk
     *   variasi (dan sesekali memicu nada kromatik yang lalu di-resolve greedy).
     *
     * @param candidates Map dari nama nada ke bobot
     * @param randomness tingkat keacakan (0.0 = greedy penuh, 1.0 = eksploratif)
     * @return nama nada yang terpilih, atau null jika candidates kosong
     */
    private String weightedRandomChoice(Map<String, Double> candidates, double randomness) {
        if (candidates.isEmpty()) return null;

        List<Map.Entry<String, Double>> entries = new ArrayList<>(candidates.entrySet());

        // Eksplorasi: weighted-random (variasi) dengan probabilitas randomness.
        if (rng.nextDouble() < randomness) {
            double totalWeight = entries.stream().mapToDouble(Map.Entry::getValue).sum();
            double r = rng.nextDouble() * totalWeight;
            for (Map.Entry<String, Double> entry : entries) {
                r -= entry.getValue();
                if (r <= 0) {
                    return entry.getKey();
                }
            }
            return entries.get(entries.size() - 1).getKey();
        }

        // GREEDY: ambil bobot tertinggi (paling sesuai konteks sequence saat ini).
        Map.Entry<String, Double> best = entries.get(0);
        for (Map.Entry<String, Double> entry : entries) {
            if (entry.getValue() > best.getValue()) {
                best = entry;
            }
        }
        return best.getKey();
    }
}
