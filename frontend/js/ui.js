/**
 * ui.js — DOM rendering dan interaksi UI.
 *
 * Bertanggung jawab untuk:
 * - Render chord cards (progression input)
 * - Render note timeline (hasil generasi)
 * - Update stats panel
 * - Update player bar (progress, time, play/pause)
 * - Highlight nada saat playback
 */

const UI = (() => {

  /**
   * Render chord cards ke #chordContainer.
   *
   * Setiap card memiliki:
   * - Dropdown root nada (C, D, E, ...)
   * - Dropdown tipe chord (Minor 7, Dominant 7, Major 7)
   * - Tombol hapus (jika > 1 chord)
   * - Tombol tambah chord di akhir
   *
   * @param {Array} chords    - Array of {root, type}
   * @param {Function} onUpdate - Callback saat chord berubah
   * @param {Function} onAdd    - Callback saat tombol tambah diklik
   * @param {Function} onRemove - Callback saat chord dihapus
   */
  function renderChords(chords, onUpdate, onAdd, onRemove) {
    const container = document.getElementById('chordContainer');
    container.innerHTML = '';

    chords.forEach((chord, idx) => {
      const card = document.createElement('div');
      card.className = 'min-w-[140px] bg-surface-charcoal border border-outline-variant p-4 rounded-xl hover:border-electric-blue/50 transition-all group relative';

      // Tombol hapus
      if (chords.length > 1) {
        const removeBtn = document.createElement('button');
        removeBtn.className = 'absolute top-2 right-2 text-outline hover:text-error opacity-0 group-hover:opacity-100 transition-opacity';
        removeBtn.innerHTML = '<span class="material-symbols-outlined text-sm">close</span>';
        removeBtn.onclick = () => onRemove(idx);
        card.appendChild(removeBtn);
      }

      // Nama chord
      const nameDiv = document.createElement('div');
      nameDiv.className = 'text-headline-sm font-headline-md mb-2';
      nameDiv.textContent = chord.root + chord.type;
      card.appendChild(nameDiv);

      // Root selector
      const rootSelect = document.createElement('select');
      rootSelect.className = 'w-full bg-background border-none text-body-sm text-on-surface-variant rounded focus:ring-1 focus:ring-electric-blue p-1 mb-1 cursor-pointer';
      Config.ROOTS.forEach(r => {
        const opt = document.createElement('option');
        opt.value = r; opt.textContent = r;
        if (r === chord.root) opt.selected = true;
        rootSelect.appendChild(opt);
      });
      rootSelect.onchange = (e) => onUpdate(idx, 'root', e.target.value);
      card.appendChild(rootSelect);

      // Type selector
      const typeSelect = document.createElement('select');
      typeSelect.className = 'w-full bg-background border-none text-body-sm text-on-surface-variant rounded focus:ring-1 focus:ring-electric-blue p-1 cursor-pointer';
      Object.entries(Config.CHORD_TYPES).forEach(([key, label]) => {
        const opt = document.createElement('option');
        opt.value = key; opt.textContent = label;
        if (key === chord.type) opt.selected = true;
        typeSelect.appendChild(opt);
      });
      typeSelect.onchange = (e) => onUpdate(idx, 'type', e.target.value);
      card.appendChild(typeSelect);

      container.appendChild(card);
    });

    // Tombol tambah chord
    if (chords.length < 8) {
      const addBtn = document.createElement('button');
      addBtn.className = 'min-w-[140px] h-[100px] border-2 border-dashed border-outline-variant rounded-xl flex flex-col items-center justify-center text-outline hover:border-electric-blue hover:text-electric-blue transition-all';
      addBtn.innerHTML = '<span class="material-symbols-outlined text-3xl mb-1">add_circle</span><span class="text-label-caps font-label-caps text-[10px]">APPEND CHORD</span>';
      addBtn.onclick = onAdd;
      container.appendChild(addBtn);
    }
  }

  /**
   * Render note timeline ke #noteTimeline.
   *
   * Setiap nada ditampilkan sebagai tile dengan warna:
   * - Biru (electric-blue): root chord
   * - Emas (metallic-gold): chord tone
   * - Abu (surface-variant): passing tone
   *
   * @param {string[]} notes  - Array nama nada
   * @param {Array}    chords - Chord progression untuk menentukan highlighting
   */
  function renderTimeline(notes, chords) {
    const timeline = document.getElementById('noteTimeline');
    timeline.innerHTML = '';

    // Kumpulkan semua chord tones dan roots
    const allChordTones = new Set();
    const roots = new Set();
    chords.forEach(ch => {
      roots.add(ch.root);
      const tones = Config.computeChordTones(ch.root, ch.type);
      if (tones) tones.forEach(t => allChordTones.add(t));
    });

    notes.forEach((note, i) => {
      // Connector dash
      if (i > 0) {
        const dash = document.createElement('div');
        dash.className = 'note-connector';
        dash.textContent = '─';
        timeline.appendChild(dash);
      }

      const el = document.createElement('div');
      el.id = `note-${i}`;
      el.className = 'note-tile note-appear';
      el.style.animationDelay = `${i * 0.04}s`;
      el.style.opacity = '0';

      // Tentukan tipe nada untuk warna
      if (roots.has(note)) {
        el.classList.add('note-root');
      } else if (allChordTones.has(note)) {
        el.classList.add('note-chord-tone');
      } else {
        el.classList.add('note-passing');
      }

      el.textContent = Config.NOTE_DISPLAY[note] || note;
      timeline.appendChild(el);
    });
  }

  /**
   * Highlight nada yang sedang dimainkan.
   * @param {number} idx - Index nada
   */
  function highlightNote(idx) {
    // Clear semua highlight
    document.querySelectorAll('[id^="note-"]').forEach(el => {
      el.classList.remove('note-playing');
    });

    const el = document.getElementById(`note-${idx}`);
    if (el) {
      el.classList.add('note-playing');
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
    }
  }

  /** Clear semua highlight nada */
  function clearHighlight() {
    document.querySelectorAll('[id^="note-"]').forEach(el => {
      el.classList.remove('note-playing');
    });
  }

  /**
   * Update stats panel.
   * @param {Object} stats - { uniqueNotes, avgInterval, complexity, totalNotes }
   */
  function updateStats(stats) {
    document.getElementById('statUnique').textContent = stats.uniqueNotes ?? '—';
    document.getElementById('statInterval').textContent = stats.avgInterval ?? '—';
    document.getElementById('statComplexity').textContent = stats.complexity ?? '—';
    document.getElementById('statTotal').textContent = stats.totalNotes ?? '—';
  }

  /**
   * Update play/pause button icon.
   * @param {boolean} isPlaying
   */
  function updatePlayButton(isPlaying) {
    const icon = document.querySelector('#playBtn .material-symbols-outlined');
    icon.textContent = isPlaying ? 'pause' : 'play_arrow';
  }

  /**
   * Update progress bar dan time display.
   * @param {number} progress - 0.0 - 1.0
   * @param {number} elapsed  - Seconds elapsed
   * @param {number} total    - Total seconds
   */
  function updateProgress(progress, elapsed, total) {
    const pct = Math.max(0, Math.min(100, progress * 100));
    document.getElementById('progressBar').style.width = pct + '%';
    document.getElementById('progressThumb').style.left = pct + '%';
    document.getElementById('timeElapsed').textContent = formatTime(elapsed);
    document.getElementById('timeTotal').textContent = formatTime(total);
    document.getElementById('trackTime').textContent =
      `${formatTime(elapsed)} / ${formatTime(total)}`;
  }

  /**
   * Set status indicator (dot + text).
   * @param {'ready'|'generating'|'error'} status
   */
  function setStatus(status) {
    const dot = document.getElementById('statusDot');
    const text = document.getElementById('statusText');
    switch (status) {
      case 'generating':
        dot.className = 'w-2 h-2 rounded-full bg-metallic-gold animate-pulse';
        text.textContent = 'GENERATING';
        break;
      case 'error':
        dot.className = 'w-2 h-2 rounded-full bg-error';
        text.textContent = 'ERROR';
        break;
      default:
        dot.className = 'w-2 h-2 rounded-full bg-status-success animate-pulse';
        text.textContent = 'ENGINE READY';
    }
  }

  /**
   * Set generate button state.
   * @param {'idle'|'loading'} state
   */
  function setGenerateButton(state) {
    const icon = document.getElementById('generateIcon');
    const text = document.getElementById('generateText');
    if (state === 'loading') {
      icon.textContent = 'hourglass_top';
      icon.classList.add('generating-spin');
      text.textContent = 'Composing...';
    } else {
      icon.textContent = 'auto_fix_high';
      icon.classList.remove('generating-spin');
      text.textContent = 'Generate Improvisation';
    }
  }

  /** Format seconds to M:SS */
  function formatTime(seconds) {
    if (!seconds || isNaN(seconds)) return '0:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  return {
    renderChords,
    renderTimeline,
    highlightNote,
    clearHighlight,
    updateStats,
    updatePlayButton,
    updateProgress,
    setStatus,
    setGenerateButton,
  };

})();
