"""Геометрия пиктограмм: та же математика, что в Kotlin (Pictos.kt). Кадр 100×100, пол y=96."""
import math

TOR, NECK, HEADR, UA, FA, TH, SH, FT = 28, 3, 5.5, 14, 13, 19, 19, 6
SHW, HIPW = 7, 4  # полуширина плеч и таза во фронтальном виде


def down(a, l=1.0):
    r = math.radians(a)
    return (math.sin(r) * l, math.cos(r) * l)


def up(a, l=1.0):
    r = math.radians(a)
    return (math.sin(r) * l, -math.cos(r) * l)


def add(p, v):
    return (p[0] + v[0], p[1] + v[1])


def sub(p, v):
    return (p[0] - v[0], p[1] - v[1])


def rot(v, deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return (v[0] * c - v[1] * s, v[0] * s + v[1] * c)


def side(p):
    """Боковой вид: углы рук и ног от «вниз», + вперёд (вправо); корпус от «вверх», + вперёд."""
    T = p.get("t", 0)
    a = p.get("a", [0, 0]); a2 = p.get("a2", a)
    l = p.get("l", [0, 0]); l2 = p.get("l2", l)
    sa = p.get("sa", 1.0); sa2 = p.get("sa2", sa)
    if "A" in p:  # опора на стопу ближней ноги
        H = sub(sub(tuple(p["A"]), down(l[1], SH)), down(l[0], TH))
    else:
        H = tuple(p["H"])
    S = add(H, up(T, TOR + p.get("sy", 0)))
    head = add(S, up(T + p.get("hd", 0), NECK + HEADR))
    J = {"hip": H, "sh": S, "head": head}
    J["el"] = add(S, down(a[0], UA * sa)); J["hand"] = add(J["el"], down(a[1], FA * sa))
    J["el2"] = add(S, down(a2[0], UA * sa2)); J["hand2"] = add(J["el2"], down(a2[1], FA * sa2))
    for suf, leg, f in (("", l, p.get("f")), ("2", l2, p.get("f2", p.get("f")))):
        k = add(H, down(leg[0], TH)); an = add(k, down(leg[1], SH))
        J["knee" + suf] = k; J["ankle" + suf] = an
        J["toe" + suf] = add(an, down(f if f is not None else leg[1] + 90, FT))
    limbs_far = [("sh", "el2"), ("el2", "hand2"), ("hip", "knee2"), ("knee2", "ankle2"), ("ankle2", "toe2")]
    limbs_near = [("hip", "knee"), ("knee", "ankle"), ("ankle", "toe"), ("sh", "el"), ("el", "hand")]
    return J, limbs_far, [("hip", "sh")], limbs_near


def front(p):
    """Фронтальный вид: углы от «вниз», + наружу; `r` — поворот всей фигуры вокруг таза (по часовой)."""
    R = p.get("r", 0)
    a = p.get("a", [0, 0]); a2 = p.get("a2", a)
    l = p.get("l", [5, 0]); l2 = p.get("l2", l)
    sa = p.get("sa", 1.0); sa2 = p.get("sa2", sa)
    loc = {}
    loc["hip"] = (0, 0)
    sy = p.get("sy", 0)
    loc["sh"] = (0, -TOR - sy)
    loc["head"] = (0, -TOR - NECK - HEADR)
    for suf, sgn, arm, s, leg in (("", 1, a, sa, l), ("2", -1, a2, sa2, l2)):
        shp = (sgn * SHW, -TOR + 1.5 - sy)
        el = add(shp, (sgn * down(arm[0], UA * s)[0], down(arm[0], UA * s)[1]))
        hd = add(el, (sgn * down(arm[1], FA * s)[0], down(arm[1], FA * s)[1]))
        hp = (sgn * HIPW, 0)
        kn = add(hp, (sgn * down(leg[0], TH)[0], down(leg[0], TH)[1]))
        an = add(kn, (sgn * down(leg[1], SH)[0], down(leg[1], SH)[1]))
        loc["shp" + suf] = shp; loc["el" + suf] = el; loc["hand" + suf] = hd
        loc["hp" + suf] = hp; loc["knee" + suf] = kn; loc["ankle" + suf] = an
    if "A" in p:
        H = sub(tuple(p["A"]), rot(loc["ankle"], R))
    else:
        H = tuple(p["H"])
    J = {k: add(H, rot(v, R)) for k, v in loc.items()}
    J["toe"] = J["ankle"]; J["toe2"] = J["ankle2"]
    limbs_far = []
    torso = [("shp", "shp2"), ("hp", "hp2"), ("hip", "sh")]
    limbs_near = [("hp", "knee"), ("knee", "ankle"), ("hp2", "knee2"), ("knee2", "ankle2"),
                  ("shp", "el"), ("el", "hand"), ("shp2", "el2"), ("el2", "hand2")]
    return J, limbs_far, torso, limbs_near


def joints(p):
    return front(p) if p.get("v") == "front" else side(p)
