"""
Сборка базы продуктов для «Взвесить» (app-nutrition/src/main/resources/ru/diary/nutrition/foods.json).

Пищевая ценность на 100 г берётся из USDA FoodData Central, SR Legacy (общественное достояние):
  https://fdc.nal.usda.gov/download-datasets  →  FoodData_Central_sr_legacy_food_csv_2018-04.zip
Несколько типично российских продуктов (творог, кефир, сметана, ряженка) заметно отличаются от американских аналогов,
для них взяты типичные значения с российской этикетки (ГОСТ), они помечены источником «label».

Запуск:  python3 tools/foods/gen.py <путь к распакованной папке SR Legacy>
"""
import csv, json, sys, os

# id, название, категория, fdc_id
USDA = [
    ("chicken-breast", "Куриная грудка запечённая", "meat", 171477),
    ("chicken-breast-raw", "Куриная грудка сырая", "meat", 171077),
    ("chicken-thigh", "Куриное бедро без кожи, запечённое", "meat", 172388),
    ("chicken-leg", "Куриная голень с кожей, запечённая", "meat", 173612),
    ("turkey", "Индейка, грудка запечённая", "meat", 171496),
    ("beef-stew", "Говядина тушёная, постная", "meat", 170215),
    ("beef-ground", "Фарш говяжий 15%, обжаренный", "meat", 174034),
    ("pork-loin", "Свинина, вырезка запечённая", "meat", 168250),
    ("chicken-liver", "Куриная печень варёная", "meat", 171061),
    ("sausage", "Сосиски говяжьи", "meat", 174614),
    ("ham", "Ветчина", "meat", 173864),
    ("pollock", "Минтай запечённый", "fish", 173726),
    ("whiting", "Хек / путассу запечённые", "fish", 175161),
    ("salmon", "Горбуша запечённая", "fish", 172001),
    ("cod", "Треска запечённая", "fish", 171956),
    ("tuna-can", "Тунец в собственном соку", "fish", 173709),
    ("shrimp", "Креветки варёные", "fish", 175180),
    ("herring", "Сельдь маринованная", "fish", 175118),
    ("egg", "Яйцо варёное", "dairy", 173424),
    ("egg-raw", "Яйцо сырое", "dairy", 171287),
    ("omelet", "Омлет", "dairy", 172185),
    ("milk", "Молоко 2,5%", "dairy", 171267),
    ("yogurt", "Йогурт натуральный", "dairy", 171284),
    ("greek-yogurt", "Греческий йогурт 0%", "dairy", 170894),
    ("cheese", "Сыр твёрдый (гауда)", "dairy", 171241),
    ("mozzarella", "Моцарелла", "dairy", 170847),
    ("butter", "Масло сливочное", "fats", 173410),
    ("buckwheat", "Гречка варёная", "grains", 170686),
    ("rice", "Рис белый варёный", "grains", 168878),
    ("rice-brown", "Рис бурый варёный", "grains", 169704),
    ("oats-dry", "Овсяные хлопья (сухие)", "grains", 173904),
    ("oatmeal-water", "Овсянка на воде", "grains", 173905),
    ("pasta", "Макароны варёные", "grains", 169737),
    ("bulgur", "Булгур варёный", "grains", 170287),
    ("millet", "Пшено варёное", "grains", 168871),
    ("semolina", "Манная каша на воде", "grains", 171659),
    ("potato-boiled", "Картофель варёный", "grains", 170440),
    ("potato-baked", "Картофель запечённый с кожурой", "grains", 170093),
    ("mashed", "Картофельное пюре с молоком и маслом", "grains", 168555),
    ("lentils", "Чечевица варёная", "grains", 172421),
    ("beans-can", "Фасоль красная консервированная", "grains", 174285),
    ("chickpeas", "Нут варёный", "grains", 173757),
    ("peas-green", "Горошек зелёный", "veg", 170017),
    ("bread-white", "Хлеб белый", "grains", 174924),
    ("bread-rye", "Хлеб ржаной", "grains", 172684),
    ("bread-wholewheat", "Хлеб цельнозерновой", "grains", 172688),
    ("pancake", "Блины / оладьи", "grains", 175009),
    ("muesli", "Гранола / мюсли", "grains", 171646),
    ("tomato", "Помидор", "veg", 170457),
    ("cucumber", "Огурец", "veg", 168409),
    ("carrot", "Морковь сырая", "veg", 170393),
    ("carrot-cooked", "Морковь варёная", "veg", 170394),
    ("cabbage", "Капуста белокочанная", "veg", 169975),
    ("cabbage-red", "Капуста краснокочанная", "veg", 169977),
    ("broccoli", "Брокколи варёная", "veg", 169967),
    ("cauliflower", "Цветная капуста варёная", "veg", 170397),
    ("zucchini", "Кабачок тушёный", "veg", 169292),
    ("pepper-bell", "Перец болгарский", "veg", 170108),
    ("onion", "Лук репчатый", "veg", 170000),
    ("beet", "Свёкла варёная", "veg", 169146),
    ("spinach", "Шпинат", "veg", 168462),
    ("lettuce", "Салат листовой", "veg", 169249),
    ("eggplant", "Баклажан тушёный", "veg", 169229),
    ("corn-can", "Кукуруза консервированная", "veg", 169214),
    ("mushrooms", "Шампиньоны варёные", "veg", 169252),
    ("garlic", "Чеснок", "veg", 169230),
    ("veg-mix", "Овощная смесь варёная", "veg", 170472),
    ("tomato-can", "Томаты в собственном соку", "veg", 170051),
    ("apple", "Яблоко", "fruit", 171688),
    ("banana", "Банан", "fruit", 173944),
    ("orange", "Апельсин", "fruit", 169097),
    ("pear", "Груша", "fruit", 169118),
    ("grapes", "Виноград", "fruit", 174683),
    ("strawberry", "Клубника", "fruit", 167762),
    ("blueberry", "Черника / голубика", "fruit", 171711),
    ("kiwi", "Киви", "fruit", 168153),
    ("mandarin", "Мандарин", "fruit", 169105),
    ("watermelon", "Арбуз", "fruit", 167765),
    ("dried-apricot", "Курага", "fruit", 173941),
    ("raisins", "Изюм", "fruit", 168165),
    ("walnuts", "Грецкий орех", "nuts", 170187),
    ("almonds", "Миндаль", "nuts", 170567),
    ("peanuts", "Арахис жареный", "nuts", 173806),
    ("cashews", "Кешью", "nuts", 170571),
    ("pb", "Арахисовая паста", "nuts", 172470),
    ("sunflower-seeds", "Семечки подсолнечника", "nuts", 170562),
    ("oil-sunflower", "Масло подсолнечное", "fats", 171025),
    ("oil-olive", "Масло оливковое", "fats", 171413),
    ("mayo", "Майонез", "fats", 171009),
    ("ketchup", "Кетчуп", "fats", 168556),
    ("hummus", "Хумус", "fats", 174289),
    ("honey", "Мёд", "sweet", 169640),
    ("sugar", "Сахар", "sweet", 169655),
    ("jam", "Варенье / джем", "sweet", 169641),
    ("dark-chocolate", "Шоколад горький 70–85%", "sweet", 170273),
    ("protein-powder", "Протеин (сывороточный)", "sweet", 173180),
]

# Типичные значения с российской этикетки: ккал, белки, жиры, углеводы, клетчатка, сахар.
LABEL = [
    ("tvorog-0", "Творог 0%", "dairy", (71, 16.5, 0.0, 1.3, 0, 1.3)),
    ("tvorog-2", "Творог 2%", "dairy", (103, 18.0, 2.0, 3.3, 0, 3.3)),
    ("tvorog-5", "Творог 5%", "dairy", (121, 17.2, 5.0, 1.8, 0, 1.8)),
    ("tvorog-9", "Творог 9%", "dairy", (159, 16.7, 9.0, 2.0, 0, 2.0)),
    ("kefir", "Кефир 2,5%", "dairy", (53, 2.9, 2.5, 4.0, 0, 4.0)),
    ("ryazhenka", "Ряженка 4%", "dairy", (84, 2.8, 4.0, 4.2, 0, 4.2)),
    ("sour-cream", "Сметана 15%", "dairy", (158, 2.6, 15.0, 3.0, 0, 3.0)),
    ("semolina-dry", "Манная крупа (сухая)", "grains", (333, 10.3, 1.0, 70.6, 3.6, 0.3)),
    ("water", "Вода", "other", (0, 0, 0, 0, 0, 0)),
]

N = {"1008": "kcal", "1003": "p", "1004": "f", "1005": "c", "1079": "fiber", "2000": "sugar"}

def main(base):
    want = {str(x[3]) for x in USDA}
    vals = {}
    with open(os.path.join(base, "food_nutrient.csv")) as fh:
        for r in csv.DictReader(fh):
            if r["fdc_id"] in want and r["nutrient_id"] in N:
                vals.setdefault(r["fdc_id"], {})[N[r["nutrient_id"]]] = float(r["amount"])
    out = []
    for fid, name, cat, fdc in USDA:
        v = vals.get(str(fdc), {})
        if "kcal" not in v: raise SystemExit(f"нет калорийности для {fid} ({fdc})")
        out.append({"id": fid, "name": name, "cat": cat, **{k: round(v.get(k, 0.0), 1) for k in ("kcal", "p", "f", "c", "fiber", "sugar")}, "src": f"usda:{fdc}"})
    for fid, name, cat, t in LABEL:
        out.append({"id": fid, "name": name, "cat": cat, **dict(zip(("kcal", "p", "f", "c", "fiber", "sugar"), t)), "src": "label"})
    path = os.path.join(os.path.dirname(__file__), "..", "..", "app-nutrition/src/main/resources/ru/diary/nutrition/foods.json")
    with open(path, "w") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
    print(len(out), "продуктов →", os.path.normpath(path))

if __name__ == "__main__":
    main(sys.argv[1])
