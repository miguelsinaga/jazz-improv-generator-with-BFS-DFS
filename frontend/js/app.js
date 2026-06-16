/**
 * app.js — Main application orchestrator.
 *
 * Mengelola:
 * - Application state (chord progression, settings, playback)
 * - API calls ke backend Java (POST /api/generate)
 * - Event handlers untuk semua interaksi UI
 * - Playback loop (polling current note untuk highlight)
 */

const App = (() => {

  // ==========================================
  // STATE
  // ==========================================
  const state = {
    chords: [
      { root: 'D', type: 'm7' },
      { root: 'G', type: '7' },
      { root: 'C', type: 'maj7' },
    ],
    algorithm: 'BFS',
    rhythmMode: 'swing',
    randomness: 0.30,
    tempo: Config.TEMPO_BPM,

    // Hasil generasi
    notes: [],
    durations: [],
    chordSequence: [],

    // Playback
    isPlaying: false,
    isLooping: false,
    currentNoteIndex: -1,
    playbackTimer: null,
    playbackInfo: null,
    generationCount: 0,
  };

  // ==========================================
  // API CALLS
  // ==========================================

  /**
   * Kirim request ke backend untuk generate improvisasi.
   *
   * POST /api/generate
   * Body: { chordProgression, algorithm, randomness, rhythmMode, maxNotes }
   *
   * @returns {Promise<Object>} Response berisi notes, durations, stats
   */
  async function callGenerate() {
    const chordNames = state.chords.map(c => c.root + c.type);

    const body = {
      chordProgression: chordNames,
      algorithm: state.algorithm,
      randomness: state.randomness,
      rhythmMode: state.rhythmMode,
      tempo: state.tempo,
      maxNotes: 32,
    };

    const response = await fetch(`${Config.API_BASE}/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return await response.json();
  }

  // ==========================================
  // EVENT HANDLERS
  // ==========================================

  /**
   * Handle klik tombol Generate.
   * Memanggil backend API, lalu render hasil ke UI.
   */
  async function handleGenerate() {
    // Stop playback yang sedang berjalan
    stopPlayback();

    // Set loading state
    UI.setGenerateButton('loading');
    UI.setStatus('generating');

    try {
      const data = await callGenerate();

      // Simpan hasil ke state
      state.notes = data.notes;
      state.durations = data.durations;
      state.chordSequence = data.chordSequence || [];
      state.generationCount++;
      state.currentNoteIndex = -1;

      // Render ke UI
      UI.renderTimeline(data.notes, state.chords);
      UI.updateStats(data.stats);

      // Update track info
      document.getElementById('trackName').textContent =
        `Improv_#${state.generationCount}.sync`;

      // Hitung total durasi untuk progress bar
      const secPerBeat = 60 / (data.tempoBpm || Config.TEMPO_BPM);
      const totalDur = data.durations.reduce((s, d) => s + d * secPerBeat, 0);
      UI.updateProgress(0, 0, totalDur);

      UI.setStatus('ready');
    } catch (err) {
      console.error('Generate error:', err);
      UI.setStatus('error');

      // Tampilkan pesan error di timeline
      const timeline = document.getElementById('noteTimeline');
      timeline.innerHTML = `<div class="flex items-center justify-center h-full w-full text-error text-body-sm font-label-caps">
        Failed to connect to backend. Make sure Java server is running on port 8080.
      </div>`;
    }

    UI.setGenerateButton('idle');
  }

  /**
   * Toggle play/pause.
   */
  function togglePlay() {
    if (state.notes.length === 0) {
      // Belum ada notes, generate dulu
      handleGenerate().then(() => {
        if (state.notes.length > 0) {
          startPlayback(0);
        }
      });
      return;
    }

    if (state.isPlaying) {
      stopPlayback();
    } else {
      startPlayback(0);
    }
  }

  /**
   * Mulai playback dari index tertentu.
   * @param {number} startIdx - Index nada pertama
   */
  function startPlayback(startIdx) {
    const tempo = state.tempo;
    const info = Audio.playNotes(
      state.notes, state.durations, tempo, startIdx, state.chordSequence);

    state.isPlaying = true;
    state.playbackInfo = info;
    UI.updatePlayButton(true);

    const secPerBeat = 60 / tempo;
    const totalDuration = info.totalDuration;

    // Polling loop untuk update UI saat playback
    if (state.playbackTimer) clearInterval(state.playbackTimer);

    state.playbackTimer = setInterval(() => {
      if (!state.isPlaying) {
        clearInterval(state.playbackTimer);
        return;
      }

      const now = info.getCurrentTime() - info.startTime + info.offset;

      // Cari nada yang sedang dimainkan
      let cumulative = 0;
      let currentIdx = 0;
      for (let i = 0; i < state.durations.length; i++) {
        cumulative += state.durations[i] * secPerBeat;
        if (now < cumulative) {
          currentIdx = i;
          break;
        }
        if (i === state.durations.length - 1) currentIdx = i;
      }

      // Highlight nada saat ini
      if (currentIdx !== state.currentNoteIndex) {
        state.currentNoteIndex = currentIdx;
        UI.highlightNote(currentIdx);
      }

      // Update progress bar
      const progress = Math.min(now / totalDuration, 1);
      UI.updateProgress(progress, now, totalDuration);

      // Cek apakah selesai
      if (now >= totalDuration) {
        clearInterval(state.playbackTimer);
        state.isPlaying = false;
        state.currentNoteIndex = -1;
        UI.updatePlayButton(false);
        UI.clearHighlight();

        // Loop jika diaktifkan
        if (state.isLooping) {
          setTimeout(() => startPlayback(0), 300);
        }
      }
    }, 50);
  }

  /** Stop playback */
  function stopPlayback() {
    state.isPlaying = false;
    Audio.stopAll();
    if (state.playbackTimer) clearInterval(state.playbackTimer);
    UI.updatePlayButton(false);
  }

  /** Toggle loop mode */
  function toggleLoop() {
    state.isLooping = !state.isLooping;
    const btn = document.getElementById('loopBtn');
    btn.className = state.isLooping
      ? 'text-electric-blue transition-colors'
      : 'player-btn';
  }

  /** Shuffle — regenerate dengan seed baru */
  function handleShuffle() {
    handleGenerate();
  }

  /** Skip ke awal */
  function skipToStart() {
    if (state.isPlaying) {
      stopPlayback();
      startPlayback(0);
    } else {
      UI.updateProgress(0, 0, 0);
    }
  }

  /** Skip ke akhir (stop) */
  function skipToEnd() {
    stopPlayback();
  }

  /** Seek via progress bar click */
  function seekTo(event) {
    if (state.notes.length === 0) return;
    const container = document.getElementById('progressContainer');
    const rect = container.getBoundingClientRect();
    const pct = (event.clientX - rect.left) / rect.width;
    const noteIdx = Math.floor(pct * state.notes.length);

    stopPlayback();
    startPlayback(Math.max(0, noteIdx));
  }

  // ==========================================
  // SETTINGS HANDLERS
  // ==========================================

  function setAlgorithm(algo) {
    state.algorithm = algo;
    const btnBFS = document.getElementById('btnBFS');
    const btnDFS = document.getElementById('btnDFS');
    btnBFS.className = `toggle-btn ${algo === 'BFS' ? 'active-algo' : ''}`;
    btnDFS.className = `toggle-btn ${algo === 'DFS' ? 'active-algo' : ''}`;
  }

  function setRhythm(mode) {
    state.rhythmMode = mode;
    const btnW = document.getElementById('btnWeight');
    const btnS = document.getElementById('btnSwing');
    btnW.className = `toggle-btn ${mode === 'weight' ? 'active-rhythm' : ''}`;
    btnS.className = `toggle-btn ${mode === 'swing' ? 'active-rhythm' : ''}`;
  }

  function updateRandomness(val) {
    state.randomness = parseFloat(val);
    document.getElementById('randomnessValue').textContent = parseFloat(val).toFixed(2);
  }

  /**
   * Update tempo (BPM). Jika sedang playback, restart agar tempo baru langsung terdengar.
   */
  function updateTempo(val) {
    state.tempo = parseInt(val, 10);
    document.getElementById('tempoValue').textContent = `${state.tempo} BPM`;

    if (state.isPlaying) {
      const idx = Math.max(0, state.currentNoteIndex);
      stopPlayback();
      startPlayback(idx);
    } else if (state.notes.length > 0) {
      // Perbarui total durasi pada progress bar
      const secPerBeat = 60 / state.tempo;
      const totalDur = state.durations.reduce((s, d) => s + d * secPerBeat, 0);
      UI.updateProgress(0, 0, totalDur);
    }
  }

  // ==========================================
  // CHORD MANAGEMENT
  // ==========================================

  function onChordUpdate(idx, field, value) {
    state.chords[idx][field] = value;
    renderChordUI();
  }

  function onChordAdd() {
    state.chords.push({ root: 'C', type: 'maj7' });
    renderChordUI();
  }

  function onChordRemove(idx) {
    if (state.chords.length <= 1) return;
    state.chords.splice(idx, 1);
    renderChordUI();
  }

  function renderChordUI() {
    UI.renderChords(state.chords, onChordUpdate, onChordAdd, onChordRemove);
  }

  // ==========================================
  // INIT
  // ==========================================

  function init() {
    renderChordUI();
    console.log('Syncopate Pro initialized. Backend expected at', Config.API_BASE);
  }

  // Run on load
  init();

  // Public API
  return {
    handleGenerate,
    togglePlay,
    toggleLoop,
    handleShuffle,
    skipToStart,
    skipToEnd,
    seekTo,
    setAlgorithm,
    setRhythm,
    updateRandomness,
    updateTempo,
  };

})();
