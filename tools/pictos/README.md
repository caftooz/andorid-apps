# Пиктограммы упражнений

`poses.py` — позы для каждого упражнения (1–2 кадра, снаряды, сустав для стрелки), `geom.py` — расчёт суставов
(та же математика, что `app-training/.../domain/Pictos.kt`), `render.py` — превью сеткой.

```
python3 poses.py ../../app-training/src/main/resources/ru/diary/training/pictos.json
python3 render.py ../../app-training/src/main/resources/ru/diary/training/pictos.json sheet.png   # нужен Pillow
```
