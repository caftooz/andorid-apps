// Генератор встроенного каталога блюд из raciona.html.
// Запуск: node tools/golden/gen-nutrition.mjs <путь к raciona.html> <каталог app-nutrition>
import fs from 'node:fs';
import path from 'node:path';

const [, , htmlPath, appDir] = process.argv;
if (!htmlPath || !appDir) { console.error('usage: node gen-nutrition.mjs <raciona.html> <app-nutrition dir>'); process.exit(2); }
const html = fs.readFileSync(htmlPath, 'utf8').split('\r\n').join('\n');
const a = html.indexOf('var DISHES = [');
const b = html.indexOf('\n];', a);
if (a < 0 || b < 0) { console.error('DISHES not found'); process.exit(3); }
const dishes = new Function(html.slice(a, b + 3).replace('var DISHES =', 'return') )();
const pick = (re) => { const m = html.match(re); return m ? m[1] : null; };
const out = {
  _comment: 'Сгенерировано tools/golden/gen-nutrition.mjs из raciona.html. Не править вручную.',
  goalProteinMin: +pick(/GOAL_P = (\d+)/), goalProteinMax: +pick(/GOAL_MAX = (\d+)/), goalKcal: +pick(/GOAL_K = (\d+)/),
  dishes,
};
const p = path.join(appDir, 'src/main/resources/ru/diary/nutrition/catalog.json');
fs.writeFileSync(p, JSON.stringify(out, null, 1) + '\n');
console.log('wrote', p, dishes.length, 'dishes');
process.exit(0);
