/**
 * engine.js — Mesin generator improvisasi (port dari backend Java ke browser).
 *
 * Berisi terjemahan 1:1 dari logika backend supaya aplikasi bisa berjalan
 * 100% di sisi klien (tanpa server), sehingga cukup di-deploy sebagai situs
 * statis. Hasilnya identik dengan ImprovController.generate di Java:
 *
 *   MusicConfig  → konstanta & teori (skala bebop, bobot interval, enclosure)
 *   GraphService → buildGraph + edgeWeight
 *   Traversal    → bfsImprovise / dfsImprovise + weighting kontekstual greedy
 *   RhythmService→ assignByWeight / assignSwing / computeStats
 *
 * Memanfaatkan helper yang sudah ada di Config (parseChord, chordTones, dll).
 */

const Engine = (() => {

  const CHROMATIC = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];

  // Bebop scale (8 nada: chord-scale + passing kromatik) per tipe chord.
  const BEBOP_SCALE_INTERVALS = {
    'maj7': [0, 2, 4, 5, 7, 8, 9, 11],
    '7':    [0, 2, 4, 5, 7, 9, 10, 11],
    'm7':   [0, 2, 3, 4, 5, 7, 9, 10],
    'm7b5': [0, 1, 3, 5, 6, 8, 10, 11],
    'dim7': [0, 2, 3, 5, 6, 8, 9, 11],
    '6':    [0, 2, 4, 5, 7, 8, 9, 11],
    'm6':   [0, 2, 3, 5, 7, 8, 9, 11],
  };

  // Bobot edge berdasarkan interval melodik (dilipat ke 1..6).
  const INTERVAL_WEIGHTS = { 1: 0.9, 2: 1.0, 3: 0.7, 4: 0.55, 5: 0.45, 6: 0.3 };

  // Konstanta bobot teori (chromatic approach, enclosure, scalar run).
  const W = {
    RESOLUTION: 2.6,
    ENCLOSURE: 1.8,
    APPROACH_ENTRY: 0.4,
    CHROMATIC_PASSING: 0.5,
    SCALE_RESOLUTION: 1.8,
    SCALE_STEP: 0.9,
    SCALE_RUN: 1.2,
  };

  const DEFAULT_MAX_NOTES = 32;
  const DEFAULT_TEMPO = 120;

  // ==========================================================
  // MUSIC CONFIG (teori)
  // ==========================================================

  function chromaticIndex(note) {
    return CHROMATIC.indexOf(note);
  }

  /** Interval terkecil (semitone, 0..6) antar dua nada. */
  function intervalSemitones(a, b) {
    const ia = chromaticIndex(a);
    const ib = chromaticIndex(b);
    if (ia < 0 || ib < 0) return -1;
    const diff = Math.abs(ib - ia);
    return Math.min(diff, 12 - diff);
  }

  function notesFromIntervals(root, intervals) {
    const rootIdx = chromaticIndex(root);
    return intervals.map(iv => CHROMATIC[(rootIdx + iv) % 12]);
  }

  /** Chord tone (reuse helper Config). */
  function getChordTones(chordName) {
    return Config.chordTones(chordName);
  }

  /** Bebop scale 8-nada untuk chord. */
  function getBebopScale(chordName) {
    const p = Config.parseChord(chordName);
    if (!p) return null;
    const ivs = BEBOP_SCALE_INTERVALS[p.type];
    if (!ivs) return null;
    return notesFromIntervals(p.root, ivs);
  }

  /** Nada kromatik pengepung (½ langkah atas/bawah chord tone, di luar chord). */
  function getApproachNotes(chordName) {
    const tones = getChordTones(chordName);
    if (!tones || !tones.length) return [];
    const set = new Set();
    tones.forEach(t => {
      const idx = chromaticIndex(t);
      const above = CHROMATIC[(idx + 1) % 12];
      const below = CHROMATIC[(idx + 11) % 12];
      if (!tones.includes(above)) set.add(above);
      if (!tones.includes(below)) set.add(below);
    });
    return [...set];
  }

  /** Apakah s & t mengepung (atas & bawah) chord tone yang sama → enclosure. */
  function isEnclosurePair(s, t, tones) {
    const is = chromaticIndex(s);
    const it = chromaticIndex(t);
    if (is < 0 || it < 0) return false;
    for (const u of tones) {
      const iu = chromaticIndex(u);
      const up = (iu + 1) % 12;
      const down = (iu + 11) % 12;
      if ((is === up && it === down) || (is === down && it === up)) return true;
    }
    return false;
  }

  // ==========================================================
  // GRAPH SERVICE
  // ==========================================================

  /**
   * Membangun graf berbobot: { nodes: string[], adj: { src: { tgt: weight } } }.
   */
  function buildGraph(chordName) {
    const chordTones = getChordTones(chordName);
    const bebop = getBebopScale(chordName);
    const approaches = getApproachNotes(chordName);
    if (!chordTones || !chordTones.length || !bebop) return null;

    const chordSet = new Set(chordTones);
    const scaleSet = new Set(bebop.filter(n => !chordSet.has(n)));

    // Semua node tanpa duplikat (urut: bebop scale lalu approach).
    const nodes = [];
    const seen = new Set();
    [...bebop, ...approaches].forEach(n => {
      if (!seen.has(n)) { seen.add(n); nodes.push(n); }
    });

    const adj = {};
    nodes.forEach(n => { adj[n] = {}; });
    nodes.forEach(s => {
      nodes.forEach(t => {
        if (s === t) return;
        const w = edgeWeight(s, t, chordSet, scaleSet);
        if (w != null) adj[s][t] = w;
      });
    });

    return { nodes, adj };
  }

  /** Bobot transisi source→target (teori bebop: scalar run, approach, enclosure). */
  function edgeWeight(source, target, chordSet, scaleSet) {
    const iv = intervalSemitones(source, target);
    const srcChord = chordSet.has(source);
    const tgtChord = chordSet.has(target);

    // Nada non-chord hanya boleh melangkah (≤ 2 semitone).
    if (!srcChord && iv > 2) return null;

    if (tgtChord) {
      if (srcChord) return INTERVAL_WEIGHTS[iv] ?? null;      // arpeggiation
      if (iv === 1) return W.RESOLUTION;                      // resolve ½ langkah
      if (iv === 2 && scaleSet.has(source)) return W.SCALE_RESOLUTION;
      return null;
    }

    // target = non-chord
    if (srcChord) {
      if (iv === 1 || iv === 2) {
        return scaleSet.has(target) ? W.SCALE_STEP : W.APPROACH_ENTRY;
      }
      return null;
    }

    // non-chord → non-chord
    if (scaleSet.has(target) && iv <= 2) return W.SCALE_RUN;
    if (isEnclosurePair(source, target, chordSet)) return W.ENCLOSURE;
    if (iv === 1) return W.CHROMATIC_PASSING;
    return null;
  }

  function getNeighbors(graph, note) {
    return graph.adj[note] || {};
  }

  // ==========================================================
  // TRAVERSAL (BFS / DFS) + weighting kontekstual greedy
  // ==========================================================

  function weightedRandomChoice(candidates, randomness) {
    const entries = Object.entries(candidates);
    if (!entries.length) return null;

    // Eksplorasi weighted-random dengan probabilitas randomness.
    if (Math.random() < randomness) {
      const total = entries.reduce((s, [, w]) => s + w, 0);
      let r = Math.random() * total;
      for (const [k, w] of entries) {
        r -= w;
        if (r <= 0) return k;
      }
      return entries[entries.length - 1][0];
    }

    // GREEDY: ambil bobot tertinggi (paling cocok dengan konteks).
    let best = entries[0];
    for (const e of entries) if (e[1] > best[1]) best = e;
    return best[0];
  }

  function applyContext(neighbors, sequence, tones) {
    const prev = sequence.length ? sequence[sequence.length - 1] : null;
    const prev2 = sequence.length >= 2 ? sequence[sequence.length - 2] : null;
    const adjusted = {};
    for (const [cand, w] of Object.entries(neighbors)) {
      adjusted[cand] = Math.max(w * contextMultiplier(prev, prev2, cand, tones), 0.0001);
    }
    return adjusted;
  }

  function contextMultiplier(prev, prev2, cand, tones) {
    if (prev == null) return 1.0;

    const candTone = tones.has(cand);
    const prevTone = tones.has(prev);
    let mult = 1.0;

    // Penyelesaian enclosure penuh: prev2 & prev mengepung target, cand = target.
    if (prev2 != null && !prevTone && !tones.has(prev2)
        && isEnclosurePair(prev2, prev, tones)
        && candTone
        && intervalSemitones(prev, cand) === 1
        && intervalSemitones(prev2, cand) === 1) {
      mult *= 2.5;
    }

    if (!prevTone) {
      const iv = intervalSemitones(prev, cand);
      if (candTone && iv <= 2) mult *= 2.6;                                  // resolve
      else if (!candTone && isEnclosurePair(prev, cand, tones)) mult *= 2.0; // enclosure
      else if (!candTone && iv <= 2) mult *= 1.3;                            // scalar run
      else mult *= 0.3;                                                      // lompatan: tekan
    }

    // Hindari bolak-balik A-B-A-B.
    if (prev2 != null && cand === prev2) mult *= 0.5;

    return mult;
  }

  function dfsImprovise(graph, startNote, maxNotes, randomness, chordName) {
    const tones = new Set(getChordTones(chordName));
    const sequence = [];
    const stack = [];
    stack.push(startNote);
    sequence.push(startNote);

    while (sequence.length < maxNotes && stack.length) {
      const current = stack[stack.length - 1];
      const neighbors = getNeighbors(graph, current);
      if (!Object.keys(neighbors).length) { stack.pop(); continue; }

      const scored = applyContext(neighbors, sequence, tones);
      const next = weightedRandomChoice(scored, randomness);
      if (next != null) { stack.push(next); sequence.push(next); }
      else stack.pop();
    }
    return sequence;
  }

  function bfsImprovise(graph, startNote, maxNotes, randomness, chordName) {
    const tones = new Set(getChordTones(chordName));
    const sequence = [];
    const queue = [];
    queue.push(startNote);
    sequence.push(startNote);

    while (sequence.length < maxNotes && queue.length) {
      const current = queue.shift();
      const neighbors = getNeighbors(graph, current);
      if (!Object.keys(neighbors).length) continue;

      const numToAdd = Math.min(2, Object.keys(neighbors).length);
      const added = new Set();

      for (let i = 0; i < numToAdd && sequence.length < maxNotes; i++) {
        const available = {};
        for (const [k, w] of Object.entries(neighbors)) {
          if (!added.has(k)) available[k] = w;
        }
        if (!Object.keys(available).length) break;

        const scored = applyContext(available, sequence, tones);
        const next = weightedRandomChoice(scored, randomness);
        if (next != null) { added.add(next); sequence.push(next); queue.push(next); }
      }

      if (!queue.length && sequence.length < maxNotes) {
        queue.push(sequence[sequence.length - 1]);
      }
    }
    return sequence;
  }

  // ==========================================================
  // RHYTHM SERVICE
  // ==========================================================

  function assignByWeight(graph, seq) {
    const durations = [0.5];
    for (let i = 1; i < seq.length; i++) {
      const w = graph.adj[seq[i - 1]] ? graph.adj[seq[i - 1]][seq[i]] : undefined;
      if (w != null) durations.push(w >= 1.0 ? 0.5 : 0.25);
      else durations.push(0.375);
    }
    return durations;
  }

  function assignSwing(seq) {
    return seq.map((_, i) => (i % 2 === 0 ? 0.375 : 0.125));
  }

  function computeStats(seq) {
    const unique = new Set(seq);
    let totalInterval = 0;
    for (let i = 1; i < seq.length; i++) {
      totalInterval += intervalSemitones(seq[i - 1], seq[i]);
    }
    const avgInterval = seq.length > 1 ? totalInterval / (seq.length - 1) : 0;
    const avgRounded = Math.round(avgInterval * 10) / 10;

    let complexity;
    if (unique.size >= 6 && avgInterval >= 2.0) complexity = 'High';
    else if (unique.size >= 5 || avgInterval >= 1.5) complexity = 'Med-High';
    else if (unique.size >= 4) complexity = 'Medium';
    else complexity = 'Low';

    return {
      uniqueNotes: unique.size,
      avgInterval: avgRounded,
      complexity,
      totalNotes: seq.length,
    };
  }

  // ==========================================================
  // ORCHESTRATOR (setara ImprovController.generate)
  // ==========================================================

  /**
   * @param {Object} req { chordProgression, algorithm, randomness, rhythmMode, tempo, maxNotes }
   * @returns {Object} { notes, durations, chordSequence, tempoBpm, algorithm, rhythmMode, stats }
   */
  function generate(req) {
    const chords = req.chordProgression || [];
    if (!chords.length) throw new Error('Chord progression kosong');

    const algorithm = req.algorithm || 'BFS';
    const randomness = Math.max(0, Math.min(1, req.randomness ?? 0.3));
    const rhythmMode = req.rhythmMode || 'swing';
    const maxNotes = req.maxNotes > 0 ? req.maxNotes : DEFAULT_MAX_NOTES;
    const tempo = req.tempo > 0 ? req.tempo : DEFAULT_TEMPO;

    const allNotes = [];
    const chordSequence = [];
    const notesPerChord = Math.floor(maxNotes / chords.length);
    let lastGraph = null;

    for (let c = 0; c < chords.length; c++) {
      const chordName = chords[c];
      const graph = buildGraph(chordName);
      if (!graph) continue;
      lastGraph = graph;

      const count = (c === chords.length - 1)
        ? maxNotes - allNotes.length
        : notesPerChord;

      const startNote = getChordTones(chordName)[0];
      const notes = algorithm.toUpperCase() === 'DFS'
        ? dfsImprovise(graph, startNote, count, randomness, chordName)
        : bfsImprovise(graph, startNote, count, randomness, chordName);

      notes.forEach(n => { allNotes.push(n); chordSequence.push(chordName); });
    }

    const durations = (rhythmMode.toLowerCase() === 'weight' && lastGraph)
      ? assignByWeight(lastGraph, allNotes)
      : assignSwing(allNotes);

    return {
      notes: allNotes,
      durations,
      chordSequence,
      tempoBpm: tempo,
      algorithm: algorithm.toUpperCase(),
      rhythmMode,
      stats: computeStats(allNotes),
    };
  }

  return { generate };

})();
