# Run: uv run --with numpy --with scipy python -I tools/bake_sounds.py <drekki.html> <out dir>
# Bakes the page's game-sound takes into files the game can play as the page plays them: pitch, filter and gain the
# game's engine cannot apply (pitch clamps to 0.5..2, volume to 1, no filters) are rendered into the file.
import re, base64, subprocess, json, sys, io, os
import numpy as np
from scipy.signal import lfilter
html = open(sys.argv[1]).read(); out = sys.argv[2]
def uris(name):
    if name == 'FOX_BITE': m = re.search(r"const FOX_BITE=\[([^\]]*)\]", html)
    else: m = re.search(r"\b" + name + r":\[('data:[^\]]*)\]", html)
    return re.findall(r"'data:audio/ogg;base64,([^']*)'", m.group(1))
SR = 48000
def dec(b64):
    r = subprocess.run(['ffmpeg','-v','error','-i','-','-ac','1','-ar',str(SR),'-f','f32le','-'], input=base64.b64decode(b64), capture_output=True, check=True)
    return np.frombuffer(r.stdout, np.float32).astype(np.float64)
def speed(x, rate):   # what a playbackRate does: read the take rate times faster (linear interpolation, as browsers do)
    n = int(len(x) / rate); return np.interp(np.arange(n) * rate, np.arange(len(x)), x)
def biquad(x, kind, f, q=0.0, gain=0.0):   # the Web Audio biquads (RBJ cookbook; lowpass Q is in dB, lowshelf has S=1)
    w = 2*np.pi*f/SR; c, s = np.cos(w), np.sin(w)
    if kind == 'lowpass':
        a_ = s / (2 * 10**(q/20)); b = [(1-c)/2, 1-c, (1-c)/2]; a = [1+a_, -2*c, 1-a_]
    else:
        A = 10**(gain/40); a_ = s/2*np.sqrt(2); sq = 2*np.sqrt(A)*a_
        b = [A*((A+1)-(A-1)*c+sq), 2*A*((A-1)-(A+1)*c), A*((A+1)-(A-1)*c-sq)]
        a = [(A+1)+(A-1)*c+sq, -2*((A-1)+(A+1)*c), (A+1)+(A-1)*c-sq]
    return lfilter(np.array(b)/a[0], np.array(a)/a[0], x)
def enc(x, path):
    subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i','-','-c:a','libvorbis','-q:a','5',path], input=x.astype(np.float32).tobytes(), check=True)
gains = {}; peaks = {}
def group(name, takes):   # one level for the group, so the takes keep their balance; the file is normalized to 0.9
    pk = max(np.abs(t).max() for t in takes); gains[name] = round(pk/0.9, 4)
    for i, t in enumerate(takes): enc(t*0.9/pk, f"{out}/{name}_{i+1}.ogg")
BITE_RATE = {1:1.15, 2:0.95, 3:0.75}; TONE = {1:None, 2:(3200,4), 3:(1400,9)}; PEAK = [0.09, 0.10]
bites = [dec(u) for u in uris('FOX_BITE')]
for s in (1,2,3):
    r = BITE_RATE[s]; takes = []
    for t, p in zip(bites, PEAK):
        y = speed(t, r)
        if TONE[s]: y = biquad(biquad(y, 'lowpass', TONE[s][0], 0.7), 'lowshelf', 260, gain=TONE[s][1])
        takes.append(np.concatenate([np.zeros(int(round((max(PEAK)-p)/r*SR))), y]))   # pad so every take's jaws meet at the same moment
    group(f"bite{s}", takes); peaks[f"bite{s}"] = round(max(PEAK)/r, 4)
ch = [dec(u) for u in uris('chime')]
group('chime', ch); gains['chime_hi'] = gains['chime']
for i, t in enumerate(ch): enc(speed(t, 4)*0.9/(gains['chime']*0.9), f"{out}/chime_hi_{i+1}.ogg")   # two octaves up, for notes past the game's double speed
for name, src in (('fire_shot','ballShot'), ('fire_ignite','ballIgnite'), ('fire_hit','ballHit')):
    group(name, [speed(dec(u), 2) for u in uris(src)])   # an octave up: the spit runs 1.6 to 3.7 times, past the game's limit
json.dump({'gains': gains, 'peaks': peaks}, open(f"{out}/bake.json", 'w'), indent=1)
print(json.dumps({'gains': gains, 'peaks': peaks}))
