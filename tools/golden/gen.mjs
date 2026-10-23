// Генератор эталонных данных из веб-прототипа trenirovki.html.
// Запуск: node tools/golden/gen.mjs <путь к trenirovki.html> <каталог app-training>
// Пишет:
//   <app>/src/main/resources/ru/diary/training/catalog.json  — встроенные данные прототипа (EX0, PROGS0, шаблоны планов)
//   <app>/src/test/resources/golden/training-golden.json     — входы и результаты JS-функций для сверки с Kotlin
// Время в прототипе замораживается на 2026-03-18 (среда), чтобы результаты были воспроизводимыми.
import fs from 'node:fs';
import path from 'node:path';

const [, , htmlPath, appDir] = process.argv;
if (!htmlPath || !appDir) { console.error('usage: node gen.mjs <trenirovki.html> <app-training dir>'); process.exit(2); }

const html = fs.readFileSync(htmlPath, 'utf8');
const start = html.indexOf('<script>') + '<script>'.length;
const end = html.indexOf('</script>', start);
let code = html.slice(start, end).split('\r\n').join('\n');

const tailMarker = "initTheme();\nloadLocal();\nrender();\ninitRemote();\n})();";
if (!code.includes(tailMarker)) { console.error('tail marker not found, prototype changed'); process.exit(3); }

const exposeNames = [
  'EX0', 'REST0', 'PER0', 'PROGS0', 'WARM_GYM', 'WARM_GEN', 'DEF_BY_KIND', 'BUILTIN_TPL', 'state', 'ui',
  'defaultPlan', 'blankPlan', 'girlPlan', 'girlPrograms', 'defaultPrograms', 'tplMake', 'catalog',
  'suggest', 'nextProg', 'weekCounts', 'streak', 'planWeek', 'blockOf', 'isDeload', 'isTest', 'planTotal', 'maxStatus',
  'cleanSession', 'cleanPlan', 'cleanProgram', 'cleanTemplate', 'cleanMax', 'cleanWlog', 'cleanCustomEx',
  'buildExport', 'parseImport', 'applyImport', 'fmt', 'parseNum', 'weekStart', 'addDays', 'plural', 'todayStr',
  'buildActiveEx', 'setReps', 'weekTotals', 'latestWeight', 'progBrief', 'sessionSummary', 'linesOf', 'joinLines',
  'migrateSession', 'saveTemplate', 'bestOf', 'volleyNote', 'deloadNow', 'resetAll', 'dateLabel',
];
code = code.replace(tailMarker,
  'globalThis.__T = {' + exposeNames.map(n => `${n}: (typeof ${n} === 'undefined' ? undefined : ${n})`).join(', ') + ', ' +
  'setState: function(o){ Object.keys(o).forEach(function(k){ state[k] = o[k]; }); }, ' +
  'setActive: function(a){ active = a; }};\n})();');

// Замороженное время: 2026-03-18 12:00 по локальному времени.
const RealDate = Date;
const FIXED = new RealDate(2026, 2, 18, 12, 0, 0).getTime();
globalThis.Date = class extends RealDate {
  constructor(...a) { if (a.length === 0) super(FIXED); else super(...a); }
  static now() { return FIXED; }
};

const store = {};
const el = () => ({ innerHTML: '', hidden: false, className: '', textContent: '', style: {}, focus() {}, select() {} });
globalThis.window = { scrollTo() {}, addEventListener() {} };
globalThis.localStorage = { getItem: k => (k in store ? store[k] : null), setItem: (k, v) => { store[k] = String(v); }, removeItem: k => { delete store[k]; } };
globalThis.document = {
  documentElement: { getAttribute: () => null, setAttribute() {}, removeAttribute() {} },
  getElementById: () => el(), addEventListener() {}, createElement: () => el(), body: { appendChild() {} },
};
Object.defineProperty(globalThis, 'navigator', { value: {}, configurable: true });

globalThis.setInterval = () => 0;
new Function(code)();
const T = globalThis.__T;
const clone = o => JSON.parse(JSON.stringify(o));
const out = (rel, data) => {
  const p = path.join(appDir, rel);
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, JSON.stringify(data, null, 1) + '\n');
  console.log('wrote', p);
};

// ---------- 1. Встроенные данные ----------
const exList = Object.keys(T.EX0).map(id => ({ id, ...clone(T.EX0[id]) }));
const progs0 = Object.keys(T.PROGS0).map(id => ({ id, ...clone(T.PROGS0[id]) }));
out('src/main/resources/ru/diary/training/catalog.json', {
  _comment: 'Сгенерировано tools/golden/gen.mjs из trenirovki.html. Не править вручную.',
  exercises: exList,
  rest0: clone(T.REST0), per0: clone(T.PER0),
  warmGym: clone(T.WARM_GYM), warmGen: clone(T.WARM_GEN),
  defByKind: clone(T.DEF_BY_KIND),
  programs0: progs0,
  defaultPrograms: clone(T.defaultPrograms()),
  defaultPlan: clone(T.defaultPlan()),
  blankPlan: clone(T.blankPlan()),
  girlPlan: clone(T.girlPlan()),
  girlPrograms: clone(T.girlPrograms()),
  builtinTemplates: clone(T.BUILTIN_TPL),
});

// ---------- 2. Эталонные сценарии ----------
const golden = { frozenToday: '2026-03-18' };

// Сессии для проверки suggest / nextProg / weekCounts / streak
const g = (id, date, start, prog, ex) => ({ id, date, type: 'gym', prog, title: '', start, min: 50, rpe: 3, pain: [], note: '', ex });
const S = (w, r, fb) => ({ w, r, fb: fb || null });
const sessions = [
  g('s1', '2026-02-23', 1000, 'A', [
    { id: 'bbsquat', name: 'Присед со штангой', kind: 'w', sets: [S(40, 5, 'ok'), S(40, 5, 'ok'), S(40, 5, 'ok'), S(40, 5, 'ok')] },
    { id: 'dips', kind: 'w', sets: [S(0, 10, 'ok'), S(0, 10, 'ok'), S(0, 10, 'ok'), S(0, 10, 'ok')] },
    { id: 'plank', kind: 'time', sets: [S(0, 40), S(0, 40), S(0, 40)] },
    { id: 'pushup', kind: 'bw', sets: [S(0, 9), S(0, 8), S(0, 8)] },
  ]),
  g('s2', '2026-03-02', 2000, 'B', [
    { id: 'pullup', kind: 'w', sets: [S(0, 8, 'ok'), S(0, 8, 'easy'), S(0, 8, 'ok'), S(0, 8, 'ok'), S(0, 8, 'ok')] },
    { id: 'dbrow', kind: 'w', sets: [S(20, 10, 'ok'), S(20, 10, 'hard'), S(20, 9, 'ok')] },
    { id: 'squat', kind: 'w', sets: [S(16, 10, 'ok'), S(16, 10, 'ok'), S(16, 10, 'ok')] },
  ]),
  g('s3', '2026-03-09', 3000, 'C', [
    { id: 'bbsquat', kind: 'w', sets: [S(42.5, 5, 'hard'), S(42.5, 4, 'hard')] },
    { id: 'squat', kind: 'w', sets: [S(16, 10, 'ok'), S(16, 10, 'ok'), S(16, 10, 'ok')] },
    { id: 'rope', kind: 'time', sets: [S(0, 45), S(0, 45), S(0, 45)] },
  ]),
  { id: 'v1', date: '2026-03-10', type: 'volley', prog: null, title: '', start: 3500, min: 90, rpe: 3, pain: [], note: '', ex: [], kcal: 500 },
  { id: 'o1', date: '2026-03-16', type: 'other', prog: null, title: 'Бег', start: 4000, min: 30, rpe: 2, pain: ['Колено'], note: 'легко', ex: [], kcal: 300 },
  g('s4', '2026-03-16', 4500, 'A', [{ id: 'dips', kind: 'w', sets: [S(0, 12, 'easy'), S(0, 12, 'ok'), S(0, 12, 'ok'), S(0, 12, 'ok')] }]),
  { id: 't1', date: '2026-03-17', type: 'track', tid: 'stretch', prog: null, title: '', start: 5000, min: 25, rpe: 0, pain: [], note: '', ex: [] },
  { id: 't2', date: '2026-03-18', type: 'track', tid: 'pullday', prog: null, title: '', start: 6000, min: 0, rpe: 0, pain: [], note: '', ex: [], sl: [4, 4, 5] },
];
const plan = T.defaultPlan();
plan.start = '2026-02-02'; // неделя 1 = 2026-02-02, сегодня (03-18) = неделя 7
T.setState({ sessions: clone(sessions), plan: clone(plan), programs: T.defaultPrograms(), customEx: [], maxes: [], wlog: {}, templates: [] });

golden.sessions = sessions;
golden.planStart = plan.start;
golden.suggest = [
  ['bbsquat', 4, 5, 5], ['dips', 4, 6, 10], ['dips', 4, 8, 12], ['plank', 3, 30, 40], ['pushup', 3, 8, 12],
  ['pullup', 5, 5, 8], ['dbrow', 3, 10, 10], ['squat', 3, 8, 10], ['rope', 3, 30, 45], ['rdl', 3, 8, 8], ['hollow', 3, 20, 40],
].map(([id, tg, lo, hi]) => ({ id, tg, lo, hi, result: clone(T.suggest(id, tg, lo, hi)) }));
golden.nextProg = T.nextProg();
golden.weekCounts = ['2026-02-23', '2026-03-02', '2026-03-09', '2026-03-16'].map(ws => ({ ws, result: clone(T.weekCounts(ws)) }));
golden.streak = T.streak();
golden.weekTotals = ['2026-03-09', '2026-03-16'].map(ws => ({ ws, result: clone(T.weekTotals(ws)) }));
golden.planWeek = ['2026-01-26', '2026-02-02', '2026-02-08', '2026-02-09', '2026-03-18', '2026-08-03', '2026-08-04', '2027-03-01']
  .map(d => ({ d, week: T.planWeek(d) }));
golden.planTotal = T.planTotal();
golden.weekFlags = Array.from({ length: 30 }, (_, i) => i - 1).map(w => ({ w, deload: T.isDeload(w), test: T.isTest(w), block: w >= 1 ? T.blockOf(w).name : null }));
golden.deloadNow = T.deloadNow();
golden.maxStatusEmpty = clone(T.maxStatus());
T.setState({ maxes: [{ id: 'm1', date: '2026-02-20', m: 'push', v: 25 }, { id: 'm2', date: '2026-03-17', m: 'push', v: 30 }, { id: 'm3', date: '2026-01-10', m: 'fs', v: 40 }] });
golden.maxStatus = clone(T.maxStatus());
golden.bestPush = T.bestOf('push');
golden.bestFs = T.bestOf('fs');
golden.volleyNote = T.volleyNote();
golden.fmt = [0, 1, 2.5, 22.25, 22.35, 100, 0.05, 1234.56].map(n => ({ n, s: T.fmt(n) }));
golden.parseNum = ['12', '12,5', ' 7.26', 'abc', '-3', '', null, '1e2', '5kg', '2000', '.5', '0,04'].map(v => ({ v, n: T.parseNum(v) }));
golden.plural = [0, 1, 2, 4, 5, 11, 12, 21, 22, 25, 111].map(n => ({ n, s: T.plural(n, 'подход', 'подхода', 'подходов') }));
golden.setReps = [{ base: 10, pctLo: 40, pctHi: 50 }, { base: 15, pctLo: 40, pctHi: 50 }, { base: 3, pctLo: 40, pctHi: 50 }, { base: 100, pctLo: 5, pctHi: 100 }]
  .map(tr => ({ tr, result: clone(T.setReps(tr)) }));
golden.progBrief = T.defaultPrograms().map(p => ({ id: p.id, text: T.progBrief(p) }));
golden.weights = { latest: T.latestWeight() };

// buildActiveEx в неделю разгрузки и обычную
golden.buildActiveEx = ['bbsquat', 'dips', 'plank'].map(id => {
  const prog = T.defaultPrograms()[0];
  const item = { id, sets: 4, lo: 5, hi: 10 };
  return { item, deload: T.deloadNow(), result: clone(T.buildActiveEx(item)) };
});
const deloadPlan = clone(plan); deloadPlan.start = '2026-02-16'; // 03-18 = неделя 5 → разгрузка (deloadEvery 5)
T.setState({ plan: deloadPlan });
golden.deloadWeek5 = { planWeek: T.planWeek(), deloadNow: T.deloadNow(),
  active: clone(T.buildActiveEx({ id: 'bbsquat', sets: 4, lo: 5, hi: 5 })), active3: clone(T.buildActiveEx({ id: 'dips', sets: 3, lo: 6, hi: 10 })) };
T.setState({ plan: clone(plan) });

// ---------- 3. Очистка и импорт ----------
const dirty = {
  app: 'dnevnik-trenirovok', version: 3,
  sessions: [
    { id: 'a1', date: '2026-03-01', type: 'gym', prog: 'A', title: 'A · ноги и жимы', start: 1700000000000, min: 55.4, rpe: 9, pain: ['Плечо', 'x'.repeat(50), 'a', 'b', 'c', 'd', 'e', 'f'],
      note: 'ok', ex: [{ id: 'dips', name: 'Брусья', kind: 'w', sets: [{ w: '12,5', r: '8', fb: 'hard' }, { w: -4, r: 'abc', fb: 'weird' }] }, { id: 'nosets' }, null, { sets: [] }], kcal: '420.6' },
    { id: 'a2', date: '2026-03-02', type: 'handstand', min: 15 },
    { id: 'a3', date: '2026-03-03', type: 'stretch', min: 25, kcal: 100 },
    { id: 'a4', date: '2026-03-04', type: 'pullday', sl: [5, 5, '6', 5000, -1] },
    { id: 'a5', date: '2026-03-05', type: 'unknown-type', title: 'Что-то' },
    { id: 'bad1', date: '05.03.2026', type: 'gym' },
    { id: 'bad2', type: 'gym' },
    'garbage', 42, null,
    { id: 'a6', date: '2026-03-06', type: 'other', title: 'Плавание', min: 40, rpe: 2, kcal: 250, note: 'n'.repeat(2000) },
    { date: '2026-03-07', type: 'volley', min: 90 },
    { id: 'a7', date: '2026-03-08', type: 'track', tid: 'handstand', min: 15, sl: 'notarray' },
  ],
  programs: [
    { id: 'A', title: 'Моя A', rot: true, warm: ['Раз', '', 'Два', 3], items: [{ id: 'dips', sets: 99, lo: 0, hi: -5 }, { id: 'rdl' }, { sets: 3 }, null] },
    { id: 'p-x', title: '', items: 'bad' },
    { title: 'без id' },
    null,
  ],
  customExercises: [
    { id: 'x-1', name: 'Мой жим', kind: 'w', step: 99, tip: 'Совет' },
    { id: 'x-2', name: 'Секундомер', kind: 'weird', step: -3 },
    { id: 'x-3' }, null,
  ],
  plan: {
    name: 'Чужой план', start: '2026-01-05', protein: 1, volleyHints: 0, deloadEvery: 50, testEvery: -3,
    blocks: [{ id: 'b1', name: 'Раз', weeks: 500, text: 't' }, { weeks: 'x' }],
    days: [{ k: 'gym', prog: 'A', note: 'n' }, { k: 'swim' }, null],
    rules: [{ t: 'Правило', d: 'Текст' }, {}, null],
    metrics: [{ id: 'push', name: 'Отжимания', unit: 'раз', goal: '40', dir: -5 }, { name: 'Без id', goal: '' }, { id: 'z', goal: null }],
    tracks: [{ id: 'handstand', name: 'Стойка', kind: 'timed', goal: 99, ic: 'nope', steps: [{ n: 'Шаг', t: 'x' }, { t: 'без названия' }], items: [{ n: 'a', d: 'b', t: 'c' }], cur: 99 },
      { id: 'pullday', name: 'Подтяг', kind: 'sets', metric: 'push', pctLo: 1, pctHi: 500, dayTarget: 0, base: 0 }, null],
  },
  maxes: [{ id: 'm1', date: '2026-03-01', m: 'push', v: '31,5' }, { id: 'bad', date: 'x', m: 'push', v: 1 }, { date: '2026-03-02', m: 'pull', v: 5 }, { date: '2026-03-02', v: 5 }],
  weekLog: { '2026-03-02': { kg: '66,4', note: 'хорошо' }, '2026-03-09': { kg: 0, note: '' }, 'bad': { kg: 70 }, '2026-03-16': { note: 'только заметка' } },
  templates: [{ id: 't1', name: 'Шаблон', plan: { name: 'P', start: '2026-02-02', blocks: [{ name: 'Б', weeks: 3 }], days: [] }, programs: [{ id: 'A', title: 'A', items: [{ id: 'dips', sets: 3, lo: 6, hi: 10 }] }] }, { name: 'без плана' }, null],
};
golden.importDirty = dirty;

function snapshotDraft(d) { return clone(d); }
// parseImport пишет результат в ui.importDraft
T.setState({ sessions: [], programs: T.defaultPrograms(), customEx: [], plan: T.defaultPlan(), maxes: [], wlog: {}, templates: [] });
T.parseImport(JSON.stringify(dirty));
golden.importDraft = snapshotDraft(T.ui.importDraft);

// Применение replace к «пустому» состоянию и merge к состоянию с данными
const base = () => ({
  sessions: clone(sessions.slice(0, 4)), programs: T.defaultPrograms(), customEx: [{ id: 'x-1', name: 'Старое', kind: 'w', step: 1, tip: '' }],
  plan: (() => { const p = T.cleanPlan(T.defaultPlan()); p.start = '2026-02-02'; return p; })(), maxes: [{ id: 'm1', date: '2026-02-01', m: 'push', v: 20 }],
  wlog: { '2026-03-02': { kg: 70, note: 'старая' } }, templates: [],
});
T.setState(base()); T.parseImport(JSON.stringify(dirty)); T.applyImport('replace');
golden.afterReplace = { sessions: clone(T.state.sessions), programs: clone(T.state.programs), customEx: clone(T.state.customEx), plan: clone(T.state.plan),
  maxes: clone(T.state.maxes), wlog: clone(T.state.wlog), templates: clone(T.state.templates) };
T.setState(base()); T.parseImport(JSON.stringify(dirty)); T.applyImport('merge');
golden.afterMerge = { sessions: clone(T.state.sessions), programs: clone(T.state.programs), customEx: clone(T.state.customEx), plan: clone(T.state.plan),
  maxes: clone(T.state.maxes), wlog: clone(T.state.wlog), templates: clone(T.state.templates) };
golden.baseForMerge = base();

// Экспорт: полное состояние -> buildExport (без exportedAt)
T.setState(base());
T.state.customEx.push({ id: 'x-9', name: 'Своё', kind: 'time', step: 5, tip: 'т' });
T.state.templates.push(T.cleanTemplate({ id: 'tt', name: 'Мой', plan: base().plan, programs: [T.defaultPrograms()[0]] }));
golden.exportInput = { sessions: clone(T.state.sessions), programs: clone(T.state.programs), customEx: clone(T.state.customEx), plan: clone(T.state.plan),
  maxes: clone(T.state.maxes), wlog: clone(T.state.wlog), templates: clone(T.state.templates) };
const ex = T.buildExport(); delete ex.exportedAt;
golden.exportOutput = clone(ex);

// Возврат к чистому состоянию и round-trip: экспорт -> импорт replace -> экспорт
T.parseImport(JSON.stringify(T.buildExport()));
golden.roundTripDraft = snapshotDraft(T.ui.importDraft);

// Шаблоны
golden.templates = ['vb', 'girl', 'blank'].map(id => ({ id, result: clone(T.tplMake(id)) }));

// linesOf / joinLines
golden.lines = [
  { text: 'А | б\n\n В | г | д \nпусто |', parts: ['t', 'd'], result: clone(T.linesOf('А | б\n\n В | г | д \nпусто |', ['t', 'd'])) },
  { text: 'Шаг | описание\nБез описания', parts: ['n', 't'], result: clone(T.linesOf('Шаг | описание\nБез описания', ['n', 't'])) },
];
golden.joinLines = T.joinLines([{ n: 'a', d: 'b', t: 'c' }, { n: 'x', d: '', t: '' }, { n: 'y', d: 'z', t: '' }], ['n', 'd', 't']);

out('src/test/resources/golden/training-golden.json', golden);
process.exit(0);
