/**
 * audio.js — Web Audio API playback engine.
 *
 * Memainkan dua lapis suara:
 * 1. MELODI  — tiap nada hasil BFS/DFS sebagai triangle wave (tone hangat).
 * 2. IRINGAN AKOR — tiap chord pada progression dibunyikan sebagai "pad"
 *    lembut (sine wave, oktaf lebih rendah) selama nada-nada di chord itu
 *    berbunyi, sehingga melodi terdengar di atas akornya.
 */

const Audio = (() => {

  let audioCtx = null;
  let masterGain = null;
  let scheduledOscillators = [];
  let volume = 0.7;

  /**
   * Memastikan AudioContext sudah dibuat dan aktif.
   * AudioContext hanya bisa dibuat setelah user interaction (browser policy).
   */
  function ensureContext() {
    if (!audioCtx || audioCtx.state === 'closed') {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      masterGain = audioCtx.createGain();
      masterGain.gain.value = volume;
      masterGain.connect(audioCtx.destination);
    }
    if (audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
  }

  /**
   * Menghentikan semua oscillator yang sedang dijadwalkan.
   */
  function stopAll() {
    scheduledOscillators.forEach(osc => {
      try { osc.stop(); } catch (e) { /* already stopped */ }
    });
    scheduledOscillators = [];
  }

  /**
   * Menjadwalkan satu nada melodi.
   */
  function scheduleMelodyNote(freq, startTime, dur) {
    const osc = audioCtx.createOscillator();
    const noteGain = audioCtx.createGain();

    osc.connect(noteGain);
    noteGain.connect(masterGain);

    osc.type = 'triangle'; // Warm jazz tone
    osc.frequency.setValueAtTime(freq, startTime);

    // Envelope: smooth attack dan release
    const attackTime = Math.min(0.02, dur * 0.1);
    const releaseStart = dur * 0.7;

    noteGain.gain.setValueAtTime(0, startTime);
    noteGain.gain.linearRampToValueAtTime(0.5, startTime + attackTime);
    noteGain.gain.setValueAtTime(0.5, startTime + releaseStart);
    noteGain.gain.linearRampToValueAtTime(0, startTime + dur);

    osc.start(startTime);
    osc.stop(startTime + dur);
    scheduledOscillators.push(osc);
  }

  /**
   * Menjadwalkan satu "pad" akor: beberapa nada chord tone dibunyikan
   * bersamaan secara lembut (sine, oktaf lebih rendah).
   *
   * @param {string} chordName - nama chord, misal "Dm7"
   * @param {number} startTime - waktu mulai (detik, audio clock)
   * @param {number} dur       - durasi pad (detik)
   */
  function scheduleChordPad(chordName, startTime, dur) {
    const tones = Config.chordTones(chordName);
    if (!tones.length) return;

    tones.forEach((noteName, idx) => {
      const baseFreq = Config.NOTE_FREQ[noteName];
      if (!baseFreq) return;
      // Root & 3rd satu oktaf di bawah; tone lain tetap, agar akor terdengar
      // penuh namun tidak menutupi melodi.
      const freq = baseFreq / 2;

      const osc = audioCtx.createOscillator();
      const padGain = audioCtx.createGain();
      osc.connect(padGain);
      padGain.connect(masterGain);

      osc.type = 'triangle'; // sedikit lebih kaya agar akor jelas terdengar
      osc.frequency.setValueAtTime(freq, startTime);

      // Pad: attack & release halus, level cukup terdengar sebagai iringan
      const attack = Math.min(0.06, dur * 0.2);
      const release = Math.min(0.12, dur * 0.3);
      const level = 0.22 / Math.sqrt(tones.length); // cukup keras tapi tidak clipping

      padGain.gain.setValueAtTime(0, startTime);
      padGain.gain.linearRampToValueAtTime(level, startTime + attack);
      padGain.gain.setValueAtTime(level, startTime + dur - release);
      padGain.gain.linearRampToValueAtTime(0, startTime + dur);

      osc.start(startTime);
      osc.stop(startTime + dur);
      scheduledOscillators.push(osc);
    });
  }

  /**
   * Memainkan sequence nada (melodi) beserta iringan akornya.
   *
   * @param {string[]} notes         - Array nama nada melodi
   * @param {number[]} durations     - Array durasi tiap nada (beat)
   * @param {number}   tempoBpm      - Tempo dalam BPM
   * @param {number}   startIdx      - Index nada pertama yang dimainkan
   * @param {string[]} chordSequence - Nama chord aktif per nada (opsional)
   * @returns {{ startTime, totalDuration, offset, getCurrentTime }} timing info
   */
  function playNotes(notes, durations, tempoBpm, startIdx = 0, chordSequence = null) {
    ensureContext();
    stopAll();

    const secPerBeat = 60 / tempoBpm;
    const startTime = audioCtx.currentTime + 0.05;

    // Total durasi seluruh sequence
    let totalDuration = 0;
    for (let i = 0; i < durations.length; i++) {
      totalDuration += durations[i] * secPerBeat;
    }

    // Offset untuk startIdx
    let offset = 0;
    for (let i = 0; i < startIdx; i++) {
      offset += durations[i] * secPerBeat;
    }

    // --- Schedule iringan akor: gabungkan nada berurutan dengan chord sama ---
    if (chordSequence && chordSequence.length) {
      let spanChord = null;
      let spanStart = startTime;
      let spanDur = 0;

      for (let i = startIdx; i < notes.length; i++) {
        const chord = chordSequence[i] || null;
        const noteDur = durations[i] * secPerBeat;

        if (chord !== spanChord) {
          // Flush span sebelumnya
          if (spanChord && spanDur > 0) {
            scheduleChordPad(spanChord, spanStart, spanDur);
          }
          spanChord = chord;
          spanStart += spanDur;
          spanDur = noteDur;
        } else {
          spanDur += noteDur;
        }
      }
      // Flush span terakhir
      if (spanChord && spanDur > 0) {
        scheduleChordPad(spanChord, spanStart, spanDur);
      }
    }

    // --- Schedule melodi ---
    let scheduleTime = startTime;
    for (let i = startIdx; i < notes.length; i++) {
      const freq = Config.NOTE_FREQ[notes[i]];
      const dur = durations[i] * secPerBeat;
      if (freq) {
        scheduleMelodyNote(freq, scheduleTime, dur);
      }
      scheduleTime += dur;
    }

    return {
      startTime,
      totalDuration,
      offset,
      getCurrentTime: () => audioCtx.currentTime,
    };
  }

  /**
   * Mengatur volume master.
   * @param {number} val - Volume (0.0 - 1.0)
   */
  function setVolume(val) {
    volume = parseFloat(val);
    if (masterGain) {
      masterGain.gain.value = volume;
    }
  }

  return {
    playNotes,
    stopAll,
    setVolume,
  };

})();
