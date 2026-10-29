// Genisource ERP promo — procedural music bed (104.4s, 92 BPM, A minor)
// Pure JS DSP: additive pads, sub, kick, hats, bell, impacts, riser, reverb.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUTWAV = path.join(HERE, 'music.wav');

const SR = 44100;
const DUR = 104.4;
const N = Math.ceil(SR * DUR);
const beat = 60 / 92;
const bar = beat * 4;

const L = new Float32Array(N);
const R = new Float32Array(N);
const padBus = new Float32Array(N);   // gets reverb
const bellBus = new Float32Array(N);

const add = (buf, i, v) => { if (i >= 0 && i < N) buf[i] += v; };

// ---------- note helpers ----------
const NOTE = { A1: 55.0, A2: 110.0, C3: 130.81, E3: 164.81, G2: 98.0, B2: 123.47, D3: 146.83,
  F2: 87.31, G3: 196.0, B3: 246.94, A3: 220.0, C4: 261.63, E4: 329.63, D4: 293.66, G4: 392.0, A4: 440.0 };

function sawish(t, f, detune, phase) {
  // 4 harmonics with 1/n rolloff, slight detune -> warm saw-ish
  let v = 0;
  const ff = f * (1 + detune);
  for (let h = 1; h <= 5; h++) {
    v += Math.sin(2 * Math.PI * ff * h * t + phase * h) / (h * 1.0);
  }
  return v / 2.2;
}

function padNote(start, dur, f, gain, pan) {
  const i0 = Math.floor(start * SR);
  const len = Math.floor(dur * SR);
  const atk = Math.floor(0.9 * SR), rel = Math.floor(1.4 * SR);
  const gl = gain * (1 - pan) , gr = gain * (1 + pan);
  for (let k = 0; k < len; k++) {
    const i = i0 + k;
    if (i < 0 || i >= N) continue;
    const t = k / SR;
    const env = Math.min(1, k / atk) * (k > len - rel ? Math.max(0, (len - k) / rel) : 1);
    // slow amplitude drift for movement
    const drift = 1 + 0.06 * Math.sin(2 * Math.PI * 0.11 * t + f);
    const v = (sawish(t, f, 0.0, f) * 0.6 + sawish(t, f, 0.0035, f * 2) * 0.4) * env * drift;
    padBus[i] += v * gain;
    L[i] += v * gl * 0.55;
    R[i] += v * gr * 0.55;
  }
}

function subNote(start, dur, f, gain) {
  const i0 = Math.floor(start * SR), len = Math.floor(dur * SR);
  const atk = Math.floor(0.4 * SR), rel = Math.floor(1.0 * SR);
  for (let k = 0; k < len; k++) {
    const i = i0 + k; if (i < 0 || i >= N) continue;
    const t = k / SR;
    const env = Math.min(1, k / atk) * (k > len - rel ? Math.max(0, (len - k) / rel) : 1);
    const v = Math.sin(2 * Math.PI * f * t) * env * gain;
    L[i] += v; R[i] += v;
  }
}

function bell(start, f, gain, pan = 0) {
  const len = Math.floor(1.5 * SR), i0 = Math.floor(start * SR);
  for (let k = 0; k < len; k++) {
    const i = i0 + k; if (i < 0 || i >= N) continue;
    const t = k / SR;
    const env = Math.exp(-t * 4.2) * Math.min(1, k / (0.004 * SR));
    const v = (Math.sin(2 * Math.PI * f * t) + 0.35 * Math.sin(2 * Math.PI * f * 2.01 * t) + 0.12 * Math.sin(2 * Math.PI * f * 3.02 * t)) * env * gain;
    bellBus[i] += v;
    L[i] += v * (1 - pan) * 0.6; R[i] += v * (1 + pan) * 0.6;
  }
}

function kick(start, gain, f0 = 118, f1 = 44) {
  const len = Math.floor(0.42 * SR), i0 = Math.floor(start * SR);
  for (let k = 0; k < len; k++) {
    const i = i0 + k; if (i < 0 || i >= N) continue;
    const t = k / SR;
    const f = f1 + (f0 - f1) * Math.exp(-t * 26);
    const env = Math.exp(-t * 9) * Math.min(1, k / (0.003 * SR));
    const v = Math.sin(2 * Math.PI * f * t) * env * gain;
    L[i] += v; R[i] += v;
  }
}

function hat(start, gain, pan = 0.3) {
  const len = Math.floor(0.07 * SR), i0 = Math.floor(start * SR);
  let prev = 0;
  for (let k = 0; k < len; k++) {
    const i = i0 + k; if (i < 0 || i >= N) continue;
    const t = k / SR;
    const env = Math.exp(-t * 90);
    const n = Math.random() * 2 - 1;
    const hp = n - prev; prev = n;   // crude highpass
    const v = hp * env * gain;
    L[i] += v * (1 - pan); R[i] += v * (1 + pan);
  }
}

function impact(start, gain) {
  const len = Math.floor(1.6 * SR), i0 = Math.floor(start * SR);
  let prev = 0;
  for (let k = 0; k < len; k++) {
    const i = i0 + k; if (i < 0 || i >= N) continue;
    const t = k / SR;
    const f = 38 + 70 * Math.exp(-t * 12);
    const env = Math.exp(-t * 3.1);
    const boom = Math.sin(2 * Math.PI * f * t) * env;
    const n = Math.random() * 2 - 1; const hp = n - prev; prev = n;
    const air = hp * Math.exp(-t * 26) * 0.5;
    const v = (boom * 0.9 + air) * gain;
    L[i] += v; R[i] += v;
  }
}

function riser(start, dur, gain) {
  const len = Math.floor(dur * SR), i0 = Math.floor(start * SR);
  let lp = 0;
  for (let k = 0; k < len; k++) {
    const i = i0 + k; if (i < 0 || i >= N) continue;
    const p = k / len;
    const n = Math.random() * 2 - 1;
    lp += (n - lp) * (0.02 + 0.5 * p * p);      // opening lowpass
    const env = Math.pow(p, 1.7);
    const v = lp * env * gain;
    L[i] += v * (1 - 0.4 * p); R[i] += v * (1 + 0.4 * p);
  }
}

// ---------- arrangement ----------
const chords = [
  { name: 'Am', notes: [NOTE.A2, NOTE.C3, NOTE.E3, NOTE.B3] },
  { name: 'F',  notes: [NOTE.F2, NOTE.A2, NOTE.C3, NOTE.E3] },
  { name: 'C',  notes: [NOTE.C3, NOTE.E3, NOTE.G3, NOTE.B3] },
  { name: 'G',  notes: [NOTE.G2, NOTE.B2, NOTE.D3, NOTE.A3] },
];
const chordLen = bar * 2;             // 5.217s
const chordStart = 4.7;
const chordCount = Math.ceil((96.4 - chordStart) / chordLen);

for (let c = 0; c < chordCount; c++) {
  const t0 = chordStart + c * chordLen;
  const ch = chords[c % 4];
  const gain = t0 < 14.9 ? 0.062 : 0.058;
  ch.notes.forEach((f, idx) => {
    const pan = ((idx % 2) ? 1 : -1) * 0.28;
    padNote(t0, chordLen + 1.2, f, gain, pan);
  });
  // root sub
  subNote(t0, chordLen * 0.98, ch.notes[0] / 2, 0.062);
}

// intro drone + wash
subNote(0.0, 5.2, NOTE.A1, 0.052);
riser(2.0, 2.7, 0.055);

// drums 14.9 -> 82.9
const drumStart = 14.8;
const drumEnd = 85.2;
for (let b = 0; drumStart + b * bar < drumEnd; b++) {
  const tb = drumStart + b * bar;
  kick(tb, 0.46); kick(tb + beat * 2, 0.34);
  if (b % 2 === 1) kick(tb + beat * 3.5, 0.18);
  for (let e = 0; e < 8; e++) {
    const te = tb + e * beat / 2;
    if (te > drumEnd) break;
    if (e % 2 === 1) hat(te, 0.062, 0.25);
    else hat(te, 0.025, -0.2);
  }
}

// bell arpeggio (sparse, upper register) 15.2 -> 74.0
const bellScale = [NOTE.A4, NOTE.G4, NOTE.E4, NOTE.D4, NOTE.C4, NOTE.E4, NOTE.G4, NOTE.A4];
let bi = 0;
for (let t = 16.2; t < 84.2; t += beat) {
  const step = bi++;
  if (step % 8 === 3 || step % 8 === 6) bell(t, bellScale[step % bellScale.length] * 1.0, 0.030, (step % 2 ? 0.35 : -0.35));
  else if (step % 4 === 0) bell(t, bellScale[(step + 2) % bellScale.length] * 0.5, 0.020, (step % 2 ? -0.3 : 0.3));
}

// section impacts
impact(4.65, 0.34);
impact(22.95, 0.22);
impact(31.35, 0.18);
impact(38.15, 0.18);
impact(45.75, 0.16);
impact(53.75, 0.16);
impact(61.35, 0.18);
impact(69.35, 0.20);
impact(76.95, 0.22);
impact(85.55, 0.26);
impact(96.35, 0.20);
impact(99.35, 0.28);

// risers into big sections
riser(21.6, 1.3, 0.035);
riser(83.0, 2.4, 0.050);
riser(75.4, 1.4, 0.030);

// outro pad swell
padNote(85.6, 10.6, NOTE.A2, 0.070, -0.2);
padNote(85.6, 10.6, NOTE.E3, 0.058, 0.2);
padNote(85.6, 10.6, NOTE.C4, 0.046, 0.0);

// ---------- reverb (Schroeder) ----------
function reverb(input, mix, decay = 0.78) {
  const combs = [1557, 1617, 1491, 1422, 1277, 1356].map(d => ({ buf: new Float32Array(d), i: 0, d }));
  const aps = [225, 556].map(d => ({ buf: new Float32Array(d), i: 0, d }));
  const out = new Float32Array(N);
  for (let n = 0; n < N; n++) {
    let s = 0;
    for (const c of combs) {
      const y = c.buf[c.i];
      c.buf[c.i] = input[n] + y * decay;
      c.i = (c.i + 1) % c.d;
      s += y;
    }
    s /= combs.length;
    for (const a of aps) {
      const y = a.buf[a.i];
      const v = -s + y;
      a.buf[a.i] = s + y * 0.5;
      a.i = (a.i + 1) % a.d;
      s = v;
    }
    out[n] = s;
  }
  return out;
}

const wetPad = reverb(padBus, 0.3, 0.80);
const wetBell = reverb(bellBus, 0.35, 0.72);
for (let n = 0; n < N; n++) {
  L[n] += wetPad[n] * 0.42 + wetBell[n] * 0.30;
  R[n] += wetPad[n] * 0.46 + wetBell[n] * 0.34;
}

// ---------- master ----------
// gentle high-shelf tame + soft clip + fade + normalize
let peak = 0;
const fadeIn = Math.floor(0.25 * SR);
const fadeOutStart = Math.floor(101.4 * SR);
const endFade = Math.floor(2.9 * SR);
for (let n = 0; n < N; n++) {
  let l = L[n], r = R[n];
  const t = n / SR;
  let g = 1;
  if (n < fadeIn) g *= n / fadeIn;
  if (n > fadeOutStart) g *= Math.max(0, 1 - (n - fadeOutStart) / endFade);
  l = Math.tanh(l * 1.15) * g;
  r = Math.tanh(r * 1.15) * g;
  L[n] = l; R[n] = r;
  peak = Math.max(peak, Math.abs(l), Math.abs(r));
}
const norm = 0.89 / (peak || 1);
for (let n = 0; n < N; n++) { L[n] *= norm; R[n] *= norm; }

// rms check
let sum = 0;
for (let n = 0; n < N; n++) sum += L[n] * L[n] + R[n] * R[n];
const rms = Math.sqrt(sum / (2 * N));

// ---------- write wav ----------
const buf = Buffer.alloc(44 + N * 4);
buf.write('RIFF', 0); buf.writeUInt32LE(36 + N * 4, 4); buf.write('WAVE', 8);
buf.write('fmt ', 12); buf.writeUInt32LE(16, 16); buf.writeUInt16LE(1, 20); buf.writeUInt16LE(2, 22);
buf.writeUInt32LE(SR, 24); buf.writeUInt32LE(SR * 4, 28); buf.writeUInt16LE(4, 32); buf.writeUInt16LE(16, 34);
buf.write('data', 36); buf.writeUInt32LE(N * 4, 40);
for (let n = 0; n < N; n++) {
  buf.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(L[n] * 32767))), 44 + n * 4);
  buf.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(R[n] * 32767))), 46 + n * 4);
}
fs.writeFileSync(OUTWAV, buf);
console.log('wrote', OUTWAV, (N / SR).toFixed(2) + 's', 'peak', peak.toFixed(3), 'rms', (20 * Math.log10(rms)).toFixed(1), 'dBFS');
