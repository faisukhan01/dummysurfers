#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dummy Surfers — CARTOON HERO generator (v8, "Jake-style" Subway-Surfers look).

Writes Assets/Resources/HumanMesh.bytes — the exact binary format HumanRig.cs reads:

    magic "HM01"
    skinned body : int32 nv, per-vertex [pos 3f, nrm 3f, uv 2f, bidx 4xB, bwt 4xB, mat 1B],
                   int32 nt, nt*3 int32 triangle indices
    static head  : identical layout (skin weights present but ignored)

Art direction (Reference 02/05/09/10 — polished 3D cartoon runner):
    * chibi-cartoon proportions: oversized head (~27% of height), compact chest,
      short chunky legs, BIG sneakers
    * red cap + brim + white badge, brown hair tuft underneath
    * cream hoodie + hood bump at the neck + orange backpack with dark straps
    * denim jeans with light rolled cuffs, chunky white sneakers with red heel patch
    * expressive face: big white eyes, dark pupils, highlights, thick brows, smile + teeth

Lighting matches AnimeMesh.cs exactly: UV.x = NdotL * 0.5 + 0.5 with the fixed
light L = normalize((0.42, 0.80, 0.43)); the URP unlit shader samples the cel ramp.

Bone order MUST equal HumanRig.RestPos / CharacterRig bones[]:
    0 body, 1 torso, 2 armL, 3 elbL, 4 handL, 5 armR, 6 elbR, 7 handR,
    8 legL, 9 kneeL, 10 footL, 11 legR, 12 kneeR, 13 footR
"""

import math
import struct

# ----------------------------------------------------------------- palette
# MUST match HumanRig.cs Pal (index order included).
MAT_COLORS = [
    (0xF5, 0xC2, 0x9E),  # 0  skin
    (0x4A, 0x2C, 0x17),  # 1  hair
    (0xF6, 0xF2, 0xE7),  # 2  hoodie (cream)
    (0x4E, 0x6E, 0x9E),  # 3  jeans (denim)
    (0xFB, 0xFB, 0xF6),  # 4  shoe (white)
    (0xE9, 0xE4, 0xD8),  # 5  sole
    (0xEE, 0x8A, 0x3C),  # 6  backpack (orange)
    (0x33, 0x31, 0x3E),  # 7  straps (dark)
    (0x2A, 0x21, 0x1B),  # 8  eye dark (pupil / brows)
    (0xA8, 0x58, 0x4E),  # 9  mouth
    (0xD9, 0x3B, 0x2F),  # 10 cap red
    (0xA9, 0x2A, 0x22),  # 11 cap dark red (brim)
    (0xF8, 0xF4, 0xEA),  # 12 badge white
    (0xFE, 0xFE, 0xFA),  # 13 teeth / eye white
    (0x8F, 0xAF, 0xD9),  # 14 rolled cuff (light denim)
    (0xE2, 0xA6, 0x7F),  # 15 skin shade (nose / ears)
]

M_SKIN, M_HAIR, M_HOODIE, M_JEANS, M_SHOE, M_SOLE, M_PACK, M_STRAP, \
    M_EYE, M_MOUTH, M_CAP, M_CAPD, M_BADGE, M_TEETH, M_CUFF, M_SKIN2 = range(16)

# ----------------------------------------------------------------- bones
B_BODY, B_TORSO, B_ARML, B_ELBL, B_HANDL, B_ARMR, B_ELBRT, B_HANDR, \
    B_LEGL, B_KNEEL, B_FOOTL, B_LEGR, B_KNEER, B_FOOTR = range(14)

REST = [
    (0.000, 0.000, 0.000),   # 0  body
    (0.000, 0.840, 0.000),   # 1  torso (hips)
    (-0.252, 1.235, 0.000),  # 2  armL
    (-0.258, 0.925, 0.000),  # 3  elbL
    (-0.262, 0.665, 0.000),  # 4  handL
    (0.252, 1.235, 0.000),   # 5  armR
    (0.258, 0.925, 0.000),   # 6  elbR
    (0.262, 0.665, 0.000),   # 7  handR
    (-0.118, 0.780, 0.000),  # 8  legL
    (-0.118, 0.415, 0.000),  # 9  kneeL
    (-0.118, 0.075, 0.000),  # 10 footL
    (0.118, 0.780, 0.000),   # 11 legR
    (0.118, 0.415, 0.000),   # 12 kneeR
    (0.118, 0.075, 0.000),   # 13 footR
]

HEAD_PIVOT = (0.0, 1.30, 0.0)   # CharacterRig mixamorig:Head target / HumanRig headOffset

L_DIR_N = (0.42, 0.80, 0.43)
_l = math.sqrt(sum(c * c for c in L_DIR_N))
L = tuple(c / _l for c in L_DIR_N)

# ----------------------------------------------------------------- small math lib
def vadd(a, b):  return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def vsub(a, b):  return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def vmul(a, s):  return (a[0] * s, a[1] * s, a[2] * s)
def dot(a, b):   return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
def norm(a):
    l = math.sqrt(dot(a, a))
    return (0.0, 1.0, 0.0) if l < 1e-9 else (a[0] / l, a[1] / l, a[2] / l)

def quat(axis, deg):
    """Quaternion from axis + degrees."""
    a = norm(axis)
    r = math.radians(deg) * 0.5
    s = math.sin(r)
    return (math.cos(r), a[0] * s, a[1] * s, a[2] * s)  # (w, x, y, z)

def qrot(q, v):
    """Rotate vector v by quaternion q."""
    w, x, y, z = q
    # t = 2 * q_vec x v
    tx = 2.0 * (y * v[2] - z * v[1])
    ty = 2.0 * (z * v[0] - x * v[2])
    tz = 2.0 * (x * v[1] - y * v[0])
    return (v[0] + w * tx + (y * tz - z * ty),
            v[1] + w * ty + (z * tx - x * tz),
            v[2] + w * tz + (x * ty - y * tx))

def smoothstep(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3.0 - 2.0 * t)

# ----------------------------------------------------------------- mesh writer
class MeshBuf:
    """One mesh (body or head) — vertices + per-material triangle lists.
    All grids use closed rings (lathe/sphere surfaces)."""

    def __init__(self):
        self.pos = []
        self.nrm = []
        self.uv = []
        self.bidx = []   # list of 4-tuples
        self.bwt = []    # list of 4-tuples
        self.mat = []
        self.tris = []   # flat list per material: dict mat -> [i0,i1,i2,...]

    def add_vert(self, p, n, uvx, uvy, mat, weights):
        """weights: list of (bone, weight01); keeps the top 4. None -> rigid bone 0."""
        self.pos.append(p)
        ndl = max(0.0, min(1.0, dot(n, L) * 0.5 + 0.5))
        self.uv.append((uvx, uvy))
        self.nrm.append(n)
        if not weights:
            weights = [(0, 1.0)]
        ws = sorted(weights, key=lambda kv: -kv[1])[:4]
        total = sum(w for _, w in ws)
        if total <= 1e-6:
            ws = [(weights[0][0], 1.0)]
            total = 1.0
        idx = [0, 0, 0, 0]
        wt = [0, 0, 0, 0]
        for k in range(4):
            if k < len(ws):
                b, w = ws[k]
                idx[k] = b
                wt[k] = int(round(255.0 * w / total))
        self.bidx.append(tuple(idx))
        self.bwt.append(tuple(wt))
        self.mat.append(mat)

    def grid(self, verts, rows, cols, mat, weights=None):
        """Emit a rows x cols grid of vertices (row-major, closed rings), then quads."""
        base = len(self.pos)
        for (p, n, uvx, uvy, w) in verts:
            self.add_vert(p, n, uvx, uvy, mat, w if w is not None else weights)
        for i in range(rows - 1):
            for j in range(cols):
                jn = (j + 1) % cols
                a = base + i * cols + j
                b = base + i * cols + jn
                c = base + (i + 1) * cols + j
                d = base + (i + 1) * cols + jn
                self.tris.extend([a, b, d, a, d, c])

    def write(self, r, skinned):
        r(struct.pack('<i', len(self.pos)))
        for i in range(len(self.pos)):
            r(struct.pack('<3f', *self.pos[i]))
            r(struct.pack('<3f', *self.nrm[i]))
            r(struct.pack('<2f', *self.uv[i]))
            if skinned:
                r(bytes(self.bidx[i]))
                r(bytes(self.bwt[i]))
            else:
                r(bytes((0, 0, 0, 0)))
                r(bytes((0, 0, 0, 0)))
            r(struct.pack('<B', self.mat[i]))
        r(struct.pack('<i', len(self.tris) // 3))
        for t in self.tris:
            r(struct.pack('<i', t))


def sphere(buf, center, radii, mat, weights, rot=None, seg=16, rings=9,
           uvy0=0.0, phi_min=0.0, phi_max=math.pi):
    """UV-sphere / ellipsoid with analytic normals; optional quaternion rot."""
    q = rot if rot is not None else quat((0, 1, 0), 0.0)
    verts = []
    for i in range(rings):
        phi = phi_min + (phi_max - phi_min) * i / (rings - 1)
        for j in range(seg):
            th = 2.0 * math.pi * j / seg
            sp, cp = math.sin(phi), math.cos(phi)
            st, ct = math.sin(th), math.cos(th)
            off = (radii[0] * sp * ct, radii[1] * cp, radii[2] * sp * st)
            gn = norm((sp * ct / radii[0], cp / radii[1], sp * st / radii[2]))
            p = vadd(center, qrot(q, off))
            n = qrot(q, gn)
            verts.append((p, n, 0.0, uvy0 + i / (rings - 1), weights))
    buf.grid(verts, rings, seg, mat)


def lathe(buf, a, b, prof, mat, wfun, seg=16):
    """Revolve profile [(t, r)] along axis A->B. wfun: t -> weight list (or fixed list)."""
    ax = norm(vsub(b, a))
    Lax = vsub(b, a)
    # orthonormal frame around the axis
    up = (0, 1, 0) if abs(ax[1]) < 0.93 else (1, 0, 0)
    u = norm(cross(ax, up))
    w = cross(ax, u)
    rows = len(prof)
    verts = []
    for i, (t, r) in enumerate(prof):
        # 2D profile normal (dr, dtangent)
        ip = max(0, i - 1)
        inx = min(rows - 1, i + 1)
        dt = prof[inx][0] - prof[ip][0]
        dr = prof[inx][1] - prof[ip][1]
        nl = math.sqrt(dr * dr + dt * dt)
        if nl < 1e-6:
            dr, dt, nl = 1.0, 0.0, 1.0
        nr, nax = -dt / nl, dr / nl
        axis_pt = vadd(a, vmul(Lax, t))
        wf = wfun(t) if callable(wfun) else wfun
        for j in range(seg):
            th = 2.0 * math.pi * j / seg
            ct, st = math.cos(th), math.sin(th)
            radial = vadd(vmul(u, ct), vmul(w, st))
            p = vadd(axis_pt, vmul(radial, r))
            n = norm(vadd(vmul(radial, nr), vmul(ax, nax)))
            verts.append((p, n, 0.0, t, wf))
    buf.grid(verts, rows, seg, mat)


def w1(bone):
    return [(bone, 1.0)]

def w2(b1, w1f, b2):
    return [(b1, w1f), (b2, 1.0 - w1f)]

def wblend(seg_a, seg_b, b1, b2):
    """Weight list blending bone1 -> bone2 over t in [seg_a, seg_b]."""
    def f(t):
        k = smoothstep(seg_a, seg_b, t)
        return [(b1, 1.0 - k), (b2, k)]
    return f


# ----------------------------------------------------------------- build body
def build_body():
    m = MeshBuf()

    # ---- torso block (cream hoodie over denim hips) ----
    sphere(m, (0, 0.855, 0), (0.195, 0.150, 0.165), M_JEANS, w2(B_TORSO, 0.72, B_BODY))       # pelvis
    sphere(m, (0, 0.985, 0.010), (0.195, 0.160, 0.170), M_HOODIE, w1(B_TORSO))                # belly
    sphere(m, (0, 1.190, 0.005), (0.215, 0.225, 0.195), M_HOODIE, w1(B_TORSO))                # chest yoke (swallows the head base)
    sphere(m, (0, 1.255, -0.135), (0.135, 0.100, 0.080), M_HOODIE, w1(B_TORSO))               # hood bump
    # neck (thick, cartoon)
    lathe(m, (0, 1.235, 0), (0, 1.330, 0),
          [(0.0, 0.092), (0.5, 0.088), (1.0, 0.082)], M_SKIN, w1(B_TORSO), seg=12)

    # ---- backpack + shoulder straps ----
    sphere(m, (0, 1.100, -0.205), (0.155, 0.130, 0.072), M_PACK, w1(B_TORSO))
    sphere(m, (0.105, 1.272, -0.005), (0.036, 0.024, 0.100), M_STRAP, w1(B_TORSO))
    sphere(m, (-0.105, 1.272, -0.005), (0.036, 0.024, 0.100), M_STRAP, w1(B_TORSO))

    # ---- arms ----
    for sx, bA, bE, bH in ((-1.0, B_ARML, B_ELBL, B_HANDL), (1.0, B_ARMR, B_ELBRT, B_HANDR)):
        sh = REST[bA]
        el = REST[bE]
        hd = REST[bH]
        sphere(m, sh, (0.078, 0.078, 0.078), M_HOODIE, w2(bA, 0.68, B_TORSO), seg=14, rings=7)
        lathe(m, sh, el, [(0.0, 0.076), (0.5, 0.072), (1.0, 0.066)],
              M_HOODIE, wblend(0.72, 1.0, bA, bE))
        sphere(m, el, (0.072, 0.072, 0.072), M_HOODIE, w1(bE), seg=14, rings=7)
        lathe(m, el, hd, [(0.0, 0.070), (0.55, 0.064), (1.0, 0.058)],
              M_HOODIE, wblend(0.75, 1.0, bE, bH))
        hc = (hd[0], hd[1] - 0.020, hd[2] + 0.005)
        sphere(m, hc, (0.056, 0.068, 0.060), M_SKIN, w1(bH), seg=12, rings=7)
        sphere(m, (hd[0] - sx * 0.027, hc[1] - 0.010, hc[2] + 0.015), (0.024, 0.026, 0.024),
               M_SKIN, w1(bH), seg=8, rings=5)

    # ---- legs + BIG sneakers ----
    for sx, bL, bK, bF in ((-1.0, B_LEGL, B_KNEEL, B_FOOTL), (1.0, B_LEGR, B_KNEER, B_FOOTR)):
        hp = REST[bL]
        kn = REST[bK]
        an = REST[bF]
        lathe(m, hp, kn, [(0.0, 0.104), (0.5, 0.093), (1.0, 0.078)],
              M_JEANS, wblend(0.75, 1.0, bL, bK))
        sphere(m, kn, (0.080, 0.080, 0.080), M_JEANS, w1(bK), seg=14, rings=7)
        lathe(m, kn, (an[0], an[1] + 0.02, an[2]), [(0.0, 0.076), (0.55, 0.066), (1.0, 0.056)],
              M_JEANS, wblend(0.75, 1.0, bK, bF))
        # rolled cuff
        sphere(m, (an[0], 0.145, 0), (0.082, 0.048, 0.082), M_CUFF,
               [(bK, 0.65), (bF, 0.35)], seg=14, rings=6)
        # chunky sneaker
        sphere(m, (an[0], 0.085, 0.050), (0.085, 0.078, 0.125), M_SHOE, w1(bF))
        sphere(m, (an[0], 0.058, 0.150), (0.072, 0.056, 0.078), M_SHOE, w1(bF), seg=12, rings=6)
        sphere(m, (an[0], 0.024, 0.060), (0.095, 0.026, 0.160), M_SOLE, w1(bF), seg=14, rings=6)
        sphere(m, (an[0], 0.100, -0.048), (0.048, 0.055, 0.038), M_CAP, w1(bF), seg=10, rings=6)
        sphere(m, (an[0], 0.150, 0.112), (0.022, 0.014, 0.020), M_STRAP, w1(bF), seg=8, rings=5)
    return m


# ----------------------------------------------------------------- build head
def build_head():
    m = MeshBuf()

    # skull + jaw + ears (head mesh stays authored around skull center 1.505;
    # HumanRig rebases it onto the 1.26 pivot — the head sinks into the chest yoke)
    sphere(m, (0, 1.505, 0.005), (0.205, 0.198, 0.198), M_SKIN, None, seg=20, rings=11)
    sphere(m, (0, 1.395, 0.010), (0.125, 0.105, 0.125), M_SKIN, None, seg=14, rings=8)        # jaw filler
    for sx in (-1.0, 1.0):
        sphere(m, (sx * 0.200, 1.495, 0.0), (0.042, 0.055, 0.042), M_SKIN, None, seg=10, rings=6)
        sphere(m, (sx * 0.226, 1.495, 0.0), (0.015, 0.026, 0.020), M_SKIN2, None, seg=8, rings=5)

    # hair tuft under the cap (small — reads as a haircut, not a hood) + side tufts over the ears
    sphere(m, (0, 1.585, -0.100), (0.160, 0.100, 0.110), M_HAIR, None, seg=16, rings=9)
    sphere(m, (0, 1.615, 0.085), (0.150, 0.042, 0.100), M_HAIR, None, seg=14, rings=7)
    for sx in (-1.0, 1.0):
        sphere(m, (sx * 0.140, 1.600, -0.050), (0.060, 0.060, 0.080), M_HAIR, None, seg=10, rings=6)

    # big expressive eyes
    for sx in (-1.0, 1.0):
        sphere(m, (sx * 0.085, 1.525, 0.158), (0.052, 0.058, 0.036), M_TEETH, None, seg=12, rings=7)
        sphere(m, (sx * 0.088, 1.522, 0.188), (0.024, 0.028, 0.014), M_EYE, None, seg=10, rings=6)
        sphere(m, (sx * 0.077, 1.538, 0.199), (0.009, 0.009, 0.007), M_TEETH, None, seg=8, rings=5)
        brim_rot = quat((0, 0, 1), -7.0 * sx)
        sphere(m, (sx * 0.088, 1.590, 0.172), (0.056, 0.014, 0.020), M_EYE, None,
               rot=brim_rot, seg=10, rings=6)

    # nose + smile with teeth
    sphere(m, (0, 1.468, 0.198), (0.034, 0.028, 0.032), M_SKIN2, None, seg=10, rings=6)
    sphere(m, (0, 1.412, 0.175), (0.050, 0.020, 0.016), M_MOUTH, None, seg=12, rings=7)
    sphere(m, (0, 1.424, 0.183), (0.034, 0.009, 0.010), M_TEETH, None, seg=10, rings=5)

    # red cap: squashed dome + brim + badge + button
    sphere(m, (0, 1.625, 0), (0.212, 0.085, 0.212), M_CAP, None,
           seg=20, rings=8, phi_max=math.radians(100))
    sphere(m, (0, 1.625, 0.205), (0.135, 0.022, 0.115), M_CAPD, None,
           rot=quat((1, 0, 0), -8.0), seg=14, rings=7)
    sphere(m, (0, 1.658, 0.192), (0.048, 0.040, 0.016), M_BADGE, None,
           rot=quat((1, 0, 0), -22.0), seg=12, rings=7)
    sphere(m, (0, 1.712, 0), (0.024, 0.024, 0.024), M_CAP, None, seg=8, rings=5)
    return m


# ----------------------------------------------------------------- main
def main():
    body = build_body()
    head = build_head()
    out = "Assets/Resources/HumanMesh.bytes"
    with open(out, "wb") as f:
        w = lambda b: f.write(b)
        w(b"HM01")
        body.write(w, skinned=True)
        head.write(w, skinned=False)
    print("body verts:", len(body.pos), "tris:", len(body.tris) // 3)
    print("head verts:", len(head.pos), "tris:", len(head.tris) // 3)
    print("wrote", out)


if __name__ == "__main__":
    main()
