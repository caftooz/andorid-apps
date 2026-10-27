"""Позы пиктограмм для каждого упражнения: 1–2 кадра, снаряды и сустав для стрелки движения."""
import json, sys

ST = [0, 0]           # прямая нога
FL = 96               # пол


def stand(**kw):
    p = {"A": [50, 93], "l": ST}
    p.update(kw)
    return p


def lie_back(x=52, y=67, **kw):  # лёжа на спине на скамье, голова слева, стопы на полу
    p = {"H": [x, y], "t": -90, "l": [60, 0]}
    p.update(kw)
    return p


BENCH = {"k": "bench", "p": [12, 62, 72]}
E = {}

E["squat"] = dict(poses=[stand(a=[25, 165]), stand(l=[82, -32], t=38, a=[50, 172])],
                  eq=[{"k": "db", "at": "hand", "d": [2, 0], "r": 4}], k=["hip"])
E["bbsquat"] = dict(poses=[stand(a=[-55, 175], a2=[-55, 175]), stand(l=[85, -32], t=42, a=[-20, 165])],
                    eq=[{"k": "plate", "at": "sh", "d": [-2, -2]}], k=["hip"])
E["airsquat"] = dict(poses=[stand(a=[0, 0]), stand(l=[85, -30], t=35, a=[90, 90])], k=["hip"])
E["jumpsquat"] = dict(poses=[stand(l=[80, -30], t=35, a=[-30, -20]), {"A": [50, 86], "l": [0, 0], "t": 0, "a": [150, 150], "f": 20}], k=["hip"])
E["dbbench"] = dict(poses=[lie_back(a=[180, 180]), lie_back(a=[10, 180], sa=0.6)],
                    eq=[BENCH, {"k": "db", "at": "hand", "r": 4}], k=["hand"])
E["bench"] = dict(poses=[lie_back(a=[180, 180]), lie_back(a=[20, 172], sa=0.65)],
                  eq=[BENCH, {"k": "plate", "at": "hand"}], k=["hand"])
E["flyes"] = dict(poses=[lie_back(a=[180, 180]), lie_back(a=[175, 175], sa=0.35)],
                  eq=[BENCH, {"k": "db", "at": "hand", "r": 4}], k=["hand"])
INC = [{"k": "bench", "p": [36, 64, 74]}, {"k": "line", "p": [52, 72, 30, 38], "w": 4}]
E["incline"] = dict(poses=[{"H": [52, 69], "t": -52, "l": [72, -5], "a": [35, 175], "sa": 0.6}, {"H": [52, 69], "t": -52, "l": [72, -5], "a": [178, 178]}],
                    eq=INC + [{"k": "db", "at": "hand", "r": 4}], k=["hand"])
SEAT = [{"k": "bench", "p": [30, 62, 78]}, {"k": "line", "p": [36, 78, 33, 40], "w": 4}]
E["ohp"] = dict(poses=[{"H": [44, 74], "t": -4, "l": [85, 0], "a": [70, 180], "sa": 0.6}, {"H": [44, 74], "t": -4, "l": [85, 0], "a": [180, 180]}],
                eq=SEAT + [{"k": "db", "at": "hand", "r": 4}], k=["hand"])
E["bbohp"] = dict(poses=[stand(a=[40, 172]), stand(a=[180, 180])], eq=[{"k": "plate", "at": "hand"}], k=["hand"])
E["latraise"] = dict(poses=[{"v": "front", "A": [54, 93], "a": [8, 8]}, {"v": "front", "A": [54, 93], "a": [88, 92]}],
                     eq=[{"k": "db", "at": "hand", "r": 3.5}, {"k": "db", "at": "hand2", "r": 3.5}], k=["hand"])
E["shrug"] = dict(poses=[{"v": "front", "A": [54, 93], "a": [4, 0]}, {"v": "front", "A": [54, 93], "a": [4, 0], "sy": 4}],
                  eq=[{"k": "db", "at": "hand", "r": 3.6}, {"k": "db", "at": "hand2", "r": 3.6}], k=["shp"])
E["curl"] = dict(poses=[stand(a=[0, 0]), stand(a=[0, 165])], eq=[{"k": "db", "at": "hand", "r": 4}], k=["hand"])
E["hammer"] = dict(poses=[stand(a=[0, 0]), stand(a=[0, 160])], eq=[{"k": "handle", "at": "hand", "a": 90, "l": 8}], k=["hand"])
E["bbcurl"] = dict(poses=[stand(a=[0, 10]), stand(a=[5, 160])], eq=[{"k": "plate", "at": "hand"}], k=["hand"])
E["pushdown"] = dict(poses=[stand(a=[0, 150], t=8), stand(a=[0, 5], t=8)],
                     eq=[{"k": "line", "p": [84, 6, 84, 96], "w": 3}, {"k": "cable", "p": [80, 8], "to": "hand"}], k=["hand"])
E["french"] = dict(poses=[lie_back(a=[178, 178]), lie_back(a=[178, -65])],
                   eq=[BENCH, {"k": "plate", "at": "hand", "r": 5}], k=["hand"])
E["deadlift"] = dict(poses=[stand(l=[70, -22], t=62, a=[0, 0]), stand(a=[0, 0])],
                     eq=[{"k": "plate", "at": "hand", "d": [0, 0]}], k=["hand"])
E["rdl"] = dict(poses=[stand(a=[0, 0]), stand(l=[-12, 8], t=78, a=[2, 2])], eq=[{"k": "db", "at": "hand", "r": 4}], k=["hand"])
E["bbrow"] = dict(poses=[stand(l=[-10, 10], t=60, a=[0, 0]), stand(l=[-10, 10], t=60, a=[-60, 30])],
                  eq=[{"k": "plate", "at": "hand"}], k=["hand"])
E["dbrow"] = dict(poses=[{"A": [50, 93], "l": [0, 0], "l2": [-12, -90], "t": 82, "a": [0, 0], "a2": [6, 6], "f2": -90},
                         {"A": [50, 93], "l": [0, 0], "l2": [-12, -90], "t": 82, "a": [-75, 15], "a2": [6, 6], "f2": -90}],
                  eq=[{"k": "bench", "p": [18, 86, 77]}, {"k": "db", "at": "hand", "r": 4}], k=["hand"])
E["pulldown"] = dict(poses=[{"H": [46, 72], "t": -8, "l": [88, 0], "a": [178, 178]}, {"H": [46, 72], "t": -8, "l": [88, 0], "a": [-25, 155]}],
                     eq=[{"k": "box", "p": [32, 74, 24, 4]}, {"k": "line", "p": [44, 78, 44, 96]}, {"k": "pad", "at": "knee", "d": [0, -5]},
                         {"k": "cable", "p": [46, 6], "to": "hand"}, {"k": "handle", "at": "hand", "a": 0, "l": 14}], k=["hand"])
E["cablerow"] = dict(poses=[{"H": [34, 78], "t": 15, "l": [80, 50], "a": [88, 88]}, {"H": [34, 78], "t": -8, "l": [80, 50], "a": [-40, 80]}],
                     eq=[{"k": "box", "p": [20, 80, 26, 4]}, {"k": "line", "p": [90, 70, 90, 96]}, {"k": "cable", "p": [88, 74], "to": "hand"},
                         {"k": "handle", "at": "hand", "a": 90, "l": 6}], k=["hand"])
E["pullup"] = dict(poses=[{"H": [50, 66], "t": 0, "l": [20, -40], "a": [180, 180]}, {"H": [50, 46], "t": 0, "l": [20, -40], "a": [-20, 165]}],
                   eq=[{"k": "line", "p": [22, 11, 78, 11], "w": 3}], k=["head"])
E["legpress"] = dict(poses=[{"H": [30, 74], "t": -60, "l": [179, 78], "a": [30, 60]}, {"H": [30, 74], "t": -60, "l": [128, 128], "a": [30, 60]}],
                     eq=[{"k": "line", "p": [16, 82, 34, 78], "w": 4}, {"k": "line", "p": [34, 78, 14, 48], "w": 4}, {"k": "line", "p": [22, 82, 22, 96]},
                         {"k": "line", "p": [44, 69, 76, 44], "w": 2}, {"k": "line", "p": [46, 68, 46, 96]},
                         {"k": "handle", "at": "ankle", "d": [2, 2], "a": 52, "l": 24}], k=["ankle"])
E["legext"] = dict(poses=[{"H": [44, 70], "t": -6, "l": [88, 0], "a": [20, 20]}, {"H": [44, 70], "t": -6, "l": [88, 88], "a": [20, 20]}],
                   eq=[{"k": "box", "p": [30, 72, 26, 4]}, {"k": "line", "p": [36, 76, 36, 96]}, {"k": "line", "p": [32, 72, 30, 40], "w": 4},
                       {"k": "pad", "at": "ankle", "d": [2, 0]}], k=["ankle"])
E["legcurl"] = dict(poses=[stand(t=20, a=[40, 60]), stand(t=20, l=[0, 0], l2=[0, -110], a=[40, 60])],
                    eq=[{"k": "line", "p": [62, 30, 62, 96], "w": 3}, {"k": "pad", "at": "ankle2", "d": [-3, 0]}], k=["ankle2"])
E["hipthrust"] = dict(poses=[{"H": [45, 87], "t": -56, "l": [125, 28], "a": [60, 100]}, {"H": [50, 71], "t": -90, "l": [85, 0], "a": [60, 100]}],
                      eq=[{"k": "box", "p": [4, 74, 20, 22]}, {"k": "plate", "at": "hip", "d": [0, -7]}], k=["hip"])
E["bridge"] = dict(poses=[{"H": [44, 89], "t": -90, "l": [140, 27], "a": [95, 95]}, {"H": [44, 77], "t": -114, "l": [100, 4], "a": [95, 95]}],
                   eq=[{"k": "db", "at": "hip", "d": [0, -6], "r": 4.5}], k=["hip"])
E["gluteb"] = dict(poses=[{"H": [44, 89], "t": -90, "l": [140, 27], "a": [95, 95]}, {"H": [44, 77], "t": -114, "l": [100, 4], "a": [95, 95]}], k=["hip"])
E["lunge"] = dict(poses=[stand(a=[0, 0]), {"A": [64, 93], "l": [80, 0], "l2": [-10, -85], "t": 0, "a": [0, 0], "f2": -20}],
                  eq=[{"k": "db", "at": "hand", "r": 4}], k=["hip"])
E["bulgar"] = dict(poses=[{"A": [62, 93], "l": [10, 0], "l2": [-60, -100], "t": 4, "a": [0, 0], "f2": -100},
                          {"A": [62, 93], "l": [80, 0], "l2": [-20, -90], "t": 8, "a": [0, 0], "f2": -100}],
                   eq=[{"k": "box", "p": [10, 66, 22, 30]}, {"k": "db", "at": "hand", "r": 4}], k=["hip"])
E["pistol"] = dict(poses=[{"A": [56, 93], "l": [0, 0], "l2": [40, 40], "a": [90, 90]}, {"A": [56, 93], "l": [95, -30], "l2": [90, 90], "t": 30, "a": [90, 90]}],
                   eq=[{"k": "box", "p": [18, 70, 24, 26]}], k=["hip"])
E["calf"] = dict(poses=[stand(a=[0, 0]), {"A": [50, 88], "l": ST, "a": [0, 0], "f": 20}], k=["head"])
E["calf1"] = dict(poses=[stand(l2=[30, -60], a=[0, 0]), {"A": [50, 88], "l": ST, "l2": [30, -60], "a": [0, 0], "f": 20}], k=["head"])
E["boxjump"] = dict(poses=[{"A": [30, 93], "l": [75, -30], "t": 35, "a": [-40, -30]}, {"A": [62, 63], "l": [70, -30], "t": 30, "a": [90, 90]}],
                    eq=[{"k": "box", "p": [48, 66, 34, 30]}], k=["hip"])
E["runjump"] = dict(poses=[{"A": [44, 93], "l": [70, -30], "l2": [-40, -70], "t": 38, "a": [-60, -40], "f2": -20}, {"A": [58, 80], "l": [10, -20], "l2": [0, -30], "t": 0, "a": [150, 150], "f": 20}],
                    k=["hip"])
E["rope"] = dict(poses=[{"A": [50, 93], "l": ST, "a": [20, 70], "f": 20}, {"A": [50, 88], "l": ST, "a": [20, 70], "f": 20}],
                 eq=[{"k": "loop", "at": "hand", "b": [46, 99], "c1": [64, 96], "c2": [28, 96]}],
                 eq2=[{"k": "loop", "at": "hand", "b": [46, -6], "c1": [66, 0], "c2": [26, 0]}], k=[])
E["plank"] = dict(poses=[{"H": [46, 83], "t": 78, "l": [-78, -78], "a": [0, 90], "f": 0}], k=[])
E["sideplank"] = dict(poses=[{"v": "front", "A": [14, 93], "r": 70, "l": [3, 0], "a2": [-15, 75], "a": [90, 160]}], k=[])
E["pushup"] = dict(poses=[{"H": [48, 77.6], "t": 70, "l": [-70, -70], "a": [0, 0], "f": -20}, {"H": [48, 85], "t": 80, "l": [-80, -80], "a": [-60, 52], "f": -40}],
                   k=["sh"])
E["clappush"] = dict(poses=[{"H": [48, 85], "t": 80, "l": [-80, -80], "a": [-60, 52], "f": -40}, {"H": [48, 70], "t": 62, "l": [-70, -70], "a": [60, 60], "f": -20}],
                     k=["sh"])
E["pikepu"] = dict(poses=[{"A": [18, 70], "l": [-55, -55], "t": 150, "a": [25, 25], "f": -100}, {"A": [18, 70], "l": [-48, -48], "t": 165, "a": [-40, 70], "f": -100}],
                   eq=[{"k": "box", "p": [6, 72, 24, 24]}], k=["head"])
E["dips"] = dict(poses=[{"H": [50, 60], "t": 0, "l": [10, -60], "a": [0, 0]}, {"H": [44, 70], "t": 20, "l": [25, -75], "a": [-95, 20]}],
                 eq=[{"k": "handle", "at": "hand", "a": 0, "l": 16}, {"k": "postj", "at": "hand", "d": [6, 0]}], k=["sh"])
E["lsit"] = dict(poses=[{"H": [46, 62], "t": 0, "l": [90, 90], "a": [0, 0], "f": 180}],
                 eq=[{"k": "handle", "at": "hand", "a": 0, "l": 16}, {"k": "postj", "at": "hand", "d": [-5, 0]}], k=[])
E["legraise"] = dict(poses=[{"H": [50, 54], "t": 0, "l": [5, 0], "a": [180, 180]}, {"H": [50, 54], "t": 0, "l": [90, 90], "a": [180, 180], "f": 180}],
                     eq=[{"k": "line", "p": [22, -1, 78, -1], "w": 3}], k=["ankle"])
E["hollow"] = dict(poses=[{"H": [52, 88], "t": -78, "l": [80, 80], "a": [-110, -110], "f": 170}], k=[])
E["crunch"] = dict(poses=[{"H": [52, 89], "t": -90, "l": [140, 27], "a": [-150, 60]}, {"H": [52, 89], "t": -58, "l": [140, 27], "a": [-150, 60]}], k=["sh"])
E["superman"] = dict(poses=[{"H": [40, 91], "t": 90, "l": [-90, -90], "a": [90, 90], "sa": 0.75, "f": -90},
                            {"H": [40, 91], "t": 74, "l": [-104, -104], "a": [106, 106], "sa": 0.75, "f": -104}], k=["hand"])
E["medball"] = dict(poses=[{"A": [50, 93], "l": ST, "t": -8, "a": [178, 178]}, stand(l=[40, -20], t=45, a=[60, 40])],
                    eq=[{"k": "ball", "at": "hand", "d": [0, -3]}], k=["hand"])
E["medrot"] = dict(poses=[{"v": "front", "A": [56, 93], "a": [60, 140], "a2": [-40, 30], "r": 4}, {"v": "front", "A": [56, 93], "a": [-40, 30], "a2": [60, 140], "r": -4}],
                   eq=[{"k": "ball", "at": "hand", "d": [0, -3]}], eq2=[{"k": "ball", "at": "hand2", "d": [0, -3]}], k=["hand"])
E["pullover"] = dict(poses=[lie_back(a=[178, 178]), lie_back(a=[-75, -75])], eq=[BENCH, {"k": "db", "at": "hand", "r": 4.5}], k=["hand"])
E["facepull"] = dict(poses=[stand(a=[88, 88], t=-4), stand(a=[100, -120], t=-4)],
                     eq=[{"k": "line", "p": [92, 6, 92, 96], "w": 3}, {"k": "cable", "p": [90, 22], "to": "hand"}], k=["hand"])
E["cuff"] = dict(poses=[{"v": "front", "A": [54, 93], "a": [4, 70], "sa": 1.0}, {"v": "front", "A": [54, 93], "a": [4, 160]}],
                 eq=[{"k": "line", "p": [90, 40, 90, 96], "w": 3}, {"k": "cable", "p": [90, 54], "to": "hand2"}], k=["hand"])
E["reardelt"] = dict(poses=[stand(l=[-10, 10], t=78, a=[0, 0]), stand(l=[-10, 10], t=78, a=[-80, -80], sa=0.6)],
                     eq=[{"k": "db", "at": "hand", "r": 3.5}], k=["hand"])
E["nordic"] = dict(poses=[{"A": [22, 93], "l": [0, -90], "t": 0, "a": [30, 160], "f": -90}, {"A": [22, 93], "l": [-55, -90], "t": 55, "a": [100, 100], "f": -90}],
                   eq=[{"k": "pad", "at": "ankle", "d": [0, -5]}], k=["head"])


if __name__ == "__main__":
    out = []
    for k, v in E.items():
        v = dict(v); v["id"] = k
        v["poses"] = [{kk: vv for kk, vv in p.items() if vv is not None} for p in v["poses"]]
        out.append(v)
    json.dump(out, open(sys.argv[1], "w"), ensure_ascii=False, indent=None)
    print(len(out))
