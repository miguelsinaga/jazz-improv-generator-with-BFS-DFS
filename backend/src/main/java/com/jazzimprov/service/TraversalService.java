package com.jazzimprov.service;

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
            double randomness) {

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

            // Pilih nada berikutnya secara weighted random
            String next = weightedRandomChoice(neighbors, randomness);

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
            double randomness) {

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

                String next = weightedRandomChoice(available, randomness);
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
     * Memilih nada secara weighted random dari kandidat.
     *
     * Dengan probabilitas (1 - randomness), pilih berdasarkan bobot:
     * nada dengan bobot lebih tinggi lebih sering terpilih.
     *
     * Dengan probabilitas randomness, pilih secara uniformly random:
     * semua nada punya kesempatan yang sama.
     *
     * @param candidates Map dari nama nada ke bobot
     * @param randomness tingkat keacakan (0.0 - 1.0)
     * @return nama nada yang terpilih, atau null jika candidates kosong
     */
    private String weightedRandomChoice(Map<String, Double> candidates, double randomness) {
        if (candidates.isEmpty()) return null;

        List<Map.Entry<String, Double>> entries = new ArrayList<>(candidates.entrySet());

        // Dengan probabilitas randomness, pilih secara acak total
        if (rng.nextDouble() < randomness) {
            return entries.get(rng.nextInt(entries.size())).getKey();
        }

        // Weighted random: nada dengan bobot lebih tinggi lebih sering dipilih
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
}
