"""Generates stylized ASL hand drawings as primitive lists.
Preview -> PNG grid. Export -> Kotlin HandShapes.kt.
Coordinate space 100 x 120 (x right, y down). Right hand, palm facing viewer.
Primitive types: 0 palm polygon, 1 capsule (finger, light), 3 capsule shaded (curled), 2 arrow polyline.
"""
import math, sys

MCP = {'i': (35, 62), 'm': (46, 59), 'r': (57, 60), 'p': (67, 64)}
LEN = {'i': 35, 'm': 39, 'r': 36, 'p': 28}
WID = {'i': 10.5, 'm': 11, 'r': 10.5, 'p': 9}
BASE_ANG = {'i': -5, 'm': 0, 'r': 5, 'p': 11}
THUMB_W = 11.5


def rounded_poly(pts, r=7, steps=5):
    out = []
    n = len(pts)
    for k in range(n):
        p0, p1, p2 = pts[k - 1], pts[k], pts[(k + 1) % n]
        v1 = (p0[0] - p1[0], p0[1] - p1[1]); v2 = (p2[0] - p1[0], p2[1] - p1[1])
        l1 = math.hypot(*v1); l2 = math.hypot(*v2)
        rr = min(r, l1 / 2, l2 / 2)
        a = (p1[0] + v1[0] / l1 * rr, p1[1] + v1[1] / l1 * rr)
        b = (p1[0] + v2[0] / l2 * rr, p1[1] + v2[1] / l2 * rr)
        for s in range(steps + 1):
            t = s / steps
            # quadratic bezier a -> p1 -> b
            x = (1 - t) ** 2 * a[0] + 2 * (1 - t) * t * p1[0] + t * t * b[0]
            y = (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * p1[1] + t * t * b[1]
            out.append((x, y))
    return out


PALM = rounded_poly([(29, 58), (73, 60), (76, 92), (66, 114), (66, 122), (36, 122), (36, 114), (27, 96)], r=9)


def dirpt(p, ang, L):
    a = math.radians(ang)
    return (p[0] + L * math.sin(a), p[1] - L * math.cos(a))


class Hand:
    def __init__(self):
        self.prims = []  # (type, width, pts)
        self.fingers = {}
        self.thumb = None
        self.thumb_layer = 'top'  # 'top' after fingers, 'mid' after palm before fingers
        self.arrows = []
        self.rot = 0
        self.extra_tips = {}

    def finger(self, f, state, ang=None):
        self.fingers[f] = (state, BASE_ANG[f] if ang is None else ang)
        return self

    def tip_of(self, f):
        state, ang = self.fingers.get(f, ('C', BASE_ANG[f]))
        p = MCP[f]
        if state == 'S':
            return dirpt(p, ang, LEN[f])
        if state == 'B':
            mid = dirpt(p, ang, LEN[f] * 0.5)
            return (mid[0] - 3, mid[1] + LEN[f] * 0.22)
        if state == 'H':
            mid = dirpt(p, ang, LEN[f] * 0.55)
            return dirpt(mid, ang - 55, LEN[f] * 0.32)
        if state == 'O':
            mid = dirpt(p, ang, LEN[f] * 0.42)
            return dirpt(mid, ang - 120, LEN[f] * 0.3)
        return (p[0], p[1] + 14)

    def finger_prims(self, f):
        state, ang = self.fingers.get(f, ('C', BASE_ANG[f]))
        p = MCP[f]; w = WID[f]
        if state == 'S':
            return [(1, w, [p, dirpt(p, ang, LEN[f])])]
        if state == 'B':  # hooked
            mid = dirpt(p, ang, LEN[f] * 0.5)
            return [(1, w, [p, mid, self.tip_of(f)])]
        if state == 'H':  # curved (C)
            mid = dirpt(p, ang, LEN[f] * 0.55)
            return [(1, w, [p, mid, self.tip_of(f)])]
        if state == 'O':
            mid = dirpt(p, ang, LEN[f] * 0.42)
            return [(1, w, [p, mid, self.tip_of(f)])]
        # curled
        return [(3, w + 1.2, [(p[0], p[1] - 4), (p[0] + 0.5, p[1] + 13)])]

    def thumb_pts(self, kind):
        b = (33, 104)
        if kind == 'out':
            return [b, (22, 88), (10, 74)]
        if kind == 'up':
            return [b, (27, 84), (25, 62)]
        if kind == 'across':
            return [b, (41, 92), (57, 88)]
        if kind == 'over':
            return [b, (40, 84), (57, 77)]
        if kind == 'under':
            return [b, (41, 94), (58, 92)]
        if kind == 'pinky':  # over to hold pinky (W)
            return [b, (44, 86), (64, 80)]
        if kind == 'touchI':
            t = self.tip_of('i')
            return [b, (24, 86), (t[0] - 2, t[1] + 3)]
        if kind == 'touchM':
            return [b, (34, 86), (46, 74)]
        if kind == 'bIM':
            return [b, (36, 80), (41, 46)]
        if kind == 'bMR':
            return [b, (44, 80), (52, 45)]
        if kind == 'bRP':
            return [b, (50, 80), (62, 49)]
        if kind == 'k':
            return [b, (34, 80), (40, 56)]
        if kind == 'c':
            return [b, (20, 90), (16, 78)]
        if kind == 'side':
            return [b, (26, 88), (22, 70)]
        raise ValueError(kind)

    def set_thumb(self, kind, layer='top'):
        self.thumb = kind; self.thumb_layer = layer
        return self

    def arrow(self, pts):
        self.arrows.append(pts); return self

    def build(self):
        prims = [(0, 0, PALM)]
        th = (1, THUMB_W, self.thumb_pts(self.thumb)) if self.thumb else None
        if th and self.thumb_layer == 'mid':
            prims.append(th)
        order = ['p', 'r', 'm', 'i']
        # extended/bent fingers drawn first (behind curled), curled last
        for f in order:
            st = self.fingers.get(f, ('C', 0))[0]
            if st != 'C':
                prims += self.finger_prims(f)
        if th and self.thumb_layer == 'low':
            prims.append(th)
        for f in order:
            st = self.fingers.get(f, ('C', 0))[0]
            if st == 'C':
                prims += self.finger_prims(f)
        if th and self.thumb_layer == 'top':
            prims.append(th)
        if self.rot:
            prims = [(t, w, rot_pts(pts, self.rot)) for (t, w, pts) in prims]
        for a in self.arrows:
            prims.append((2, 3.2, a))
        return prims


def rot_pts(pts, deg, c=(50, 82)):
    a = math.radians(deg); ca, sa = math.cos(a), math.sin(a)
    return [(c[0] + (x - c[0]) * ca - (y - c[1]) * sa, c[1] + (x - c[0]) * sa + (y - c[1]) * ca) for x, y in pts]


def arc(cx, cy, r, a0, a1, n=18):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * k / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * k / n))) for k in range(n + 1)]


def H():
    return Hand()


def letters():
    d = {}
    d['A'] = H().set_thumb('up')
    d['B'] = H().finger('i', 'S', -2).finger('m', 'S', 0).finger('r', 'S', 2).finger('p', 'S', 5).set_thumb('across', 'mid')
    d['C'] = H().finger('i', 'H', -2).finger('m', 'H', 0).finger('r', 'H', 2).finger('p', 'H', 5).set_thumb('c')
    d['D'] = H().finger('i', 'S', -3).set_thumb('touchM')
    d['E'] = H().finger('i', 'B', 0).finger('m', 'B', 0).finger('r', 'B', 0).finger('p', 'B', 3).set_thumb('under', 'mid')
    d['F'] = H().finger('i', 'O', -4).finger('m', 'S', 2).finger('r', 'S', 9).finger('p', 'S', 17).set_thumb('touchI')
    g = H().finger('i', 'S', -2).set_thumb('side'); g.rot = 90; d['G'] = g
    h = H().finger('i', 'S', -2).finger('m', 'S', 0).set_thumb('over'); h.rot = 90; d['H'] = h
    d['I'] = H().finger('p', 'S', 8).set_thumb('over')
    d['J'] = H().finger('p', 'S', 8).set_thumb('over').arrow(arc(70, 30, 12, -90, 120)[::-1])
    d['K'] = H().finger('i', 'S', -12).finger('m', 'S', 12).set_thumb('k')
    d['L'] = H().finger('i', 'S', -2).set_thumb('out')
    d['M'] = H().set_thumb('bRP', 'low')
    d['N'] = H().set_thumb('bMR', 'low')
    d['O'] = H().finger('i', 'O', 0).finger('m', 'O', 2).finger('r', 'O', 4).finger('p', 'O', 6).set_thumb('touchI')
    p = H().finger('i', 'S', -12).finger('m', 'S', 12).set_thumb('k'); p.rot = 150; d['P'] = p
    q = H().finger('i', 'S', -2).set_thumb('side'); q.rot = 160; d['Q'] = q
    d['R'] = H().finger('i', 'S', 5).finger('m', 'S', -9).set_thumb('over')
    d['S'] = H().set_thumb('over')
    d['T'] = H().set_thumb('bIM', 'low')
    d['U'] = H().finger('i', 'S', -1).finger('m', 'S', 1).set_thumb('over')
    d['V'] = H().finger('i', 'S', -14).finger('m', 'S', 12).set_thumb('over')
    d['W'] = H().finger('i', 'S', -16).finger('m', 'S', 0).finger('r', 'S', 16).set_thumb('pinky')
    d['X'] = H().finger('i', 'B', -2).set_thumb('over')
    d['Y'] = H().finger('p', 'S', 30).set_thumb('out')
    d['Z'] = H().finger('i', 'S', -3).set_thumb('touchM').arrow([(54, 6), (80, 6), (54, 26), (80, 26)])
    # words
    d['ILY'] = H().finger('i', 'S', -4).finger('p', 'S', 22).set_thumb('out')
    flat = lambda: H().finger('i', 'S', -2).finger('m', 'S', 0).finger('r', 'S', 2).finger('p', 'S', 5).set_thumb('across', 'mid')
    d['HELLO'] = flat().arrow([(80, 40), (96, 30)])
    d['THANK_YOU'] = flat().arrow([(82, 22), (94, 44)])
    d['PLEASE'] = flat().arrow(arc(86, 38, 10, -60, 230))
    d['SORRY'] = H().set_thumb('up').arrow(arc(84, 40, 11, -60, 230))
    d['YES'] = H().set_thumb('over').arrow([(86, 26), (86, 44)]).arrow([(92, 44), (92, 26)])
    d['NO'] = H().finger('i', 'S', -3).finger('m', 'S', 0).set_thumb('out').arrow(arc(30, 30, 10, -20, 140))
    d['HELP'] = H().set_thumb('up').arrow([(86, 50), (86, 22)])
    d['GOOD'] = flat().arrow([(84, 22), (84, 48)])
    d['EAT'] = H().finger('i', 'O', 0).finger('m', 'O', 2).finger('r', 'O', 4).finger('p', 'O', 6).set_thumb('touchI').arrow([(88, 52), (88, 26)])
    d['DRINK'] = H().finger('i', 'H', -2).finger('m', 'H', 0).finger('r', 'H', 2).finger('p', 'H', 5).set_thumb('c').arrow(arc(78, 40, 14, 60, -60))
    d['FRIEND'] = H().finger('i', 'B', -2).set_thumb('over').arrow(arc(80, 36, 10, 200, -40))
    d['WATER'] = H().finger('i', 'S', -16).finger('m', 'S', 0).finger('r', 'S', 16).set_thumb('pinky').arrow([(86, 24), (86, 36)]).arrow([(92, 36), (92, 24)])
    return d


# ---------------------------------------------------------------- preview
def preview(path, scale=3):
    from PIL import Image, ImageDraw, ImageFont
    d = letters()
    keys = list(d.keys())
    cols = 8
    rows = (len(keys) + cols - 1) // cols
    W, Ht = 100 * scale, 130 * scale
    img = Image.new('RGB', (cols * W, rows * Ht), 'white')
    dr = ImageDraw.Draw(img)
    for idx, k in enumerate(keys):
        ox, oy = (idx % cols) * W, (idx // cols) * Ht
        prims = d[k].build()
        xs=[x for (_,w,pts) in prims for x,_ in pts]; ys=[y for (_,w,pts) in prims for _,y in pts]
        x0,x1,y0,y1=min(xs)-8,max(xs)+8,min(ys)-8,max(ys)+8
        f=min(100/(x1-x0),120/(y1-y0)); cx=(100-(x1-x0)*f)/2; cy=(120-(y1-y0)*f)/2
        for (t, w, pts) in prims:
            w=w*f
            P = [(ox + (cx+(x-x0)*f) * scale, oy + (cy+(y-y0)*f) * scale) for x, y in pts]
            if t == 0:
                dr.polygon(P, fill=(255, 217, 184), outline=(156, 102, 68))
                dr.line(P + [P[0]], fill=(156, 102, 68), width=int(2.4 * scale))
            elif t in (1, 3):
                fill = (255, 217, 184) if t == 1 else (240, 190, 150)
                for col, ww in (((156, 102, 68), w + 2.6), (fill, w)):
                    dr.line(P, fill=col, width=int(ww * scale), joint='curve')
                    for p in (P[0], P[-1]):
                        r = ww * scale / 2
                        dr.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=col)
                    for p in P[1:-1]:
                        r = ww * scale / 2
                        dr.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=col)
            else:
                dr.line(P, fill=(124, 77, 255), width=int(w * scale), joint='curve')
                # head
                (x1, y1), (x2, y2) = P[-2], P[-1]
                a = math.atan2(y2 - y1, x2 - x1)
                L = 6 * scale
                dr.polygon([(x2, y2), (x2 - L * math.cos(a - 0.5), y2 - L * math.sin(a - 0.5)), (x2 - L * math.cos(a + 0.5), y2 - L * math.sin(a + 0.5))], fill=(124, 77, 255))
        dr.text((ox + 6, oy + 6), k, fill='black')
    img.save(path)


# ---------------------------------------------------------------- export
def export(path):
    d = letters()
    lines = ["package com.example.signlearn", "",
             "/**", " * AUTO-GENERATED by tools/hands.py — stylized hand drawings for each sign.",
             " * Each entry is a list of primitives: [type, width, pointCount, x1, y1, x2, y2, ...]",
             " * type 0 = palm polygon, 1 = finger/thumb capsule, 3 = curled (shaded) finger, 2 = motion arrow.",
             " * Drawing space is 100 x 120 units.", " */",
             "object HandShapes {", "    private val cache = HashMap<String, FloatArray>()", "",
             "    /** Returns the parsed primitive list for a sign key, or null if we have no drawing. */",
             "    fun get(key: String): FloatArray? {",
             "        cache[key]?.let { return it }",
             "        val raw = data[key] ?: return null",
             "        val arr = raw.split(' ').map { it.toFloat() }.toFloatArray()",
             "        cache[key] = arr",
             "        return arr",
             "    }", "",
             "    private val data: Map<String, String> = mapOf("]
    entries = []
    for k, hand in d.items():
        vals = []
        for (t, w, pts) in hand.build():
            vals += [t, round(w, 1), len(pts)]
            for x, y in pts:
                vals += [round(x, 1), round(y, 1)]
        body = " ".join(f"{v:g}" for v in vals)
        entries.append(f'        "{k}" to "{body}"')
    lines.append(",\n".join(entries))
    lines += ["    )", "}", ""]
    open(path, 'w').write("\n".join(lines))


if __name__ == '__main__':
    if sys.argv[1] == 'preview':
        preview(sys.argv[2])
    else:
        export(sys.argv[2])
