"""Превью пиктограмм: рисует все упражнения сеткой, чтобы проверить позы глазами."""
import json, math, sys
from PIL import Image, ImageDraw, ImageFont
from geom import joints, add, sub

S = 4  # масштаб
BG, FIG, FAR, EQ, ARROW = (32, 33, 38), (236, 236, 240), (150, 152, 160), (110, 112, 122), (245, 180, 40)


TOP = 10  # запас сверху: руки над головой и прыжки


def P(pt, ox):
    return ((pt[0] + ox) * S, (pt[1] + TOP) * S)


def line(d, a, b, w, c, ox):
    d.line([P(a, ox), P(b, ox)], fill=c, width=int(w * S))
    for q in (a, b):
        x, y = P(q, ox); r = w * S / 2
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)


def circ(d, c0, r, col, ox, outline=None):
    x, y = P(c0, ox)
    d.ellipse([x - r * S, y - r * S, x + r * S, y + r * S], fill=col, outline=outline, width=int(1.5 * S) if outline else 0)


def jpt(J, ref):
    if isinstance(ref, str):
        return J[ref]
    return tuple(ref)


def equip(d, items, J, ox, top):
    for it in items:
        k = it["k"]
        on_top = k in ("plate", "db", "ball", "pad", "handle")
        if on_top != top:
            continue
        if k == "bench":
            x1, x2, y = it["p"]
            line(d, (x1, y), (x2, y), 4, EQ, ox)
            line(d, (x1 + 4, y), (x1 + 4, 96), 2.5, EQ, ox); line(d, (x2 - 4, y), (x2 - 4, 96), 2.5, EQ, ox)
        elif k == "line":
            x1, y1, x2, y2 = it["p"]; line(d, (x1, y1), (x2, y2), it.get("w", 3), EQ, ox)
        elif k == "box":
            x, y, w, h = it["p"]
            d.rounded_rectangle([P((x, y), ox), P((x + w, y + h), ox)], radius=2 * S, fill=EQ)
        elif k == "circ":
            x, y, r = it["p"]; circ(d, (x, y), r, EQ, ox)
        elif k == "cable":
            px, py = it["p"]; line(d, (px, py), jpt(J, it["to"]), 1.4, EQ, ox); circ(d, (px, py), 3, EQ, ox)
        elif k == "curve":
            a = jpt(J, it["a"]); b = jpt(J, it["b"]); cx, cy = it["c"]
            pts = []
            for i in range(21):
                t = i / 20
                pts.append(((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * cx + t * t * b[0], (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * cy + t * t * b[1]))
            d.line([P(q, ox) for q in pts], fill=EQ, width=int(1.6 * S))
        elif k == "loop":
            a = jpt(J, it["at"]); bx, by = it["b"]; c1 = it["c1"]; c2 = it["c2"]
            for c in (c1, c2):
                pts = []
                for i in range(21):
                    t = i / 20
                    pts.append(((1 - t) ** 2 * a[0] + 2 * (1 - t) * t * c[0] + t * t * bx, (1 - t) ** 2 * a[1] + 2 * (1 - t) * t * c[1] + t * t * by))
                d.line([P(q, ox) for q in pts], fill=EQ, width=int(1.6 * S))
        elif k == "postj":
            a = add(jpt(J, it["at"]), tuple(it.get("d", (0, 0)))); line(d, a, (a[0], 96), 2.6, EQ, ox)
        elif k == "plate":
            c0 = add(jpt(J, it["at"]), tuple(it.get("d", (0, 0)))); circ(d, c0, it.get("r", 7), EQ, ox, outline=BG)
        elif k == "db":
            c0 = add(jpt(J, it["at"]), tuple(it.get("d", (0, 0)))); circ(d, c0, it.get("r", 3.6), EQ, ox, outline=BG)
        elif k == "ball":
            c0 = add(jpt(J, it["at"]), tuple(it.get("d", (0, 0)))); circ(d, c0, it.get("r", 5.5), EQ, ox, outline=BG)
        elif k == "pad":
            c0 = add(jpt(J, it["at"]), tuple(it.get("d", (0, 0)))); circ(d, c0, it.get("r", 3.4), EQ, ox, outline=BG)
        elif k == "handle":
            a = add(jpt(J, it["at"]), tuple(it.get("d", (0, 0))))
            ang = math.radians(it.get("a", 0)); L = it.get("l", 8)
            dv = (math.cos(ang) * L / 2, math.sin(ang) * L / 2)
            line(d, sub(a, dv), add(a, dv), 2.6, EQ, ox)


def figure(d, pose, items, ox):
    J, far, torso, near = joints(pose)
    line(d, (2, 96), (98, 96), 1.5, EQ, ox)
    equip(d, items, J, ox, False)
    for a, b in far:
        line(d, J[a], J[b], 6, FAR, ox)
    for a, b in torso:
        w = 9 if (a, b) == ("hip", "sh") else 6
        line(d, J[a], J[b], w + 2.4, BG, ox); line(d, J[a], J[b], w, FIG, ox)
    circ(d, J["head"], 6.7, BG, ox); circ(d, J["head"], 5.5, FIG, ox)
    # Ближние конечности с тёмной обводкой: рука поверх корпуса не сливается с ним.
    # Обводку рисуем сразу для всей группы (ноги, затем руки), чтобы она не резала суставы.
    legs = [sg for sg in near if sg[0] in ("hip", "knee", "ankle", "hp", "hp2", "knee2")]
    arms = [sg for sg in near if sg not in legs]
    for grp in (legs, arms):
        for a2, b2 in grp:
            line(d, J[a2], J[b2], 8.4, BG, ox)
        for a2, b2 in grp:
            line(d, J[a2], J[b2], 6, FIG, ox)
    equip(d, items, J, ox, True)
    return J


def arrow(d, a, b, ox, off=7):
    v = sub(b, a); L = math.hypot(*v)
    if L < 3:
        return
    n = (-v[1] / L * off, v[0] / L * off)
    s = add(add(a, (v[0] * 0.2, v[1] * 0.2)), n); e = add(add(a, (v[0] * 0.8, v[1] * 0.8)), n)
    line(d, s, e, 2.2, ARROW, ox)
    u = (v[0] / L, v[1] / L)
    h1 = add(e, add((-u[0] * 4, -u[1] * 4), (-u[1] * 3, u[0] * 3)))
    h2 = add(e, add((-u[0] * 4, -u[1] * 4), (u[1] * 3, -u[0] * 3)))
    d.polygon([P(e, ox), P(h1, ox), P(h2, ox)], fill=ARROW)


def tile(ex):
    im = Image.new("RGB", (200 * S, 118 * S), BG)
    d = ImageDraw.Draw(im)
    items = ex.get("eq", [])
    items2 = ex.get("eq2", items)
    if len(ex["poses"]) == 1:
        figure(d, ex["poses"][0], items, 50)
    else:
        p1, p2 = ex["poses"]
        J1 = figure(d, p1, items, 0)
        J2 = figure(d, p2, items2, 100)
        for k in ex.get("k", ["hand"]):
            arrow(d, J1[k], J2[k], 0, ex.get("off", 7))
    f = ImageFont.load_default()
    d.text((4 * S, 110 * S), ex["id"], fill=(200, 200, 200), font=f)
    return im.resize((400, 236), Image.LANCZOS)


if __name__ == "__main__":
    data = json.load(open(sys.argv[1]))
    only = sys.argv[3].split(",") if len(sys.argv) > 3 else None
    exs = [e for e in data if not only or e["id"] in only]
    cols = 4
    rows = (len(exs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 404, rows * 240), (255, 255, 255))
    for i, e in enumerate(exs):
        sheet.paste(tile(e), ((i % cols) * 404, (i // cols) * 240))
    sheet.save(sys.argv[2])
    print(len(exs), sheet.size)
