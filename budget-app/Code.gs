/**
 * Gestion de Budget — application web Google Apps Script.
 *
 * La Google Sheet à laquelle ce script est rattaché sert de base de données.
 * L'interface (Index.html) est servie comme web app, utilisable sur ordinateur
 * et smartphone.
 *
 * Feuilles utilisées :
 *  - Lignes      : lignes de budget mensuelles (prévu / réel / pointé)
 *  - Operations  : dépenses saisies au fil de l'eau (Alimentation, Carburant…)
 *  - EnAttente   : dépenses à venir / en attente
 *  - Epargne     : soldes des comptes d'épargne
 *  - Config      : paramètres (auteurs, catégories d'opérations)
 */

var SH = {
  LIGNES: 'Lignes',
  OPS: 'Operations',
  ATTENTE: 'EnAttente',
  EPARGNE: 'Epargne',
  CONFIG: 'Config'
};

var HEADERS = {
  Lignes: ['id', 'mois', 'categorie', 'libelle', 'prevu', 'reel', 'pointe', 'lien', 'ordre'],
  Operations: ['id', 'mois', 'date', 'categorie', 'description', 'montant', 'auteur', 'creeLe'],
  EnAttente: ['id', 'description', 'montant', 'creeLe'],
  Epargne: ['id', 'compte', 'solde', 'majLe'],
  Config: ['cle', 'valeur']
};

// Colonnes stockées en texte brut (évite que Sheets convertisse "2026-11" en date).
var TEXT_COLS = {
  Lignes: ['id', 'mois', 'categorie', 'libelle', 'lien'],
  Operations: ['id', 'mois', 'date', 'categorie', 'description', 'auteur', 'creeLe'],
  EnAttente: ['id', 'description', 'creeLe'],
  Epargne: ['id', 'compte', 'majLe'],
  Config: ['cle', 'valeur']
};

var CATEGORIES = ['Revenus', 'Besoins Fixes', 'Transports', 'Dépenses Variables', 'Épargne'];

var DEFAULT_CONFIG = {
  auteurs: 'Guillaume,Elise',
  categoriesOperations: 'Alimentation,Carburant,Loisirs,Divers'
};

// Modèle utilisé pour le tout premier mois (montants à compléter dans l'appli
// ou via « Importer des lignes »).
var TEMPLATE = [
  ['Revenus', 'Salaire Guillaume'],
  ['Revenus', 'Salaire Elise'],
  ['Revenus', 'CAF (Elia)'],
  ['Revenus', 'Revenus exceptionnels'],
  ['Besoins Fixes', 'Prêt maison'],
  ['Besoins Fixes', 'Prêt mur'],
  ['Besoins Fixes', 'Assurance Habitation'],
  ['Besoins Fixes', 'Assurance Prêt habitat'],
  ['Besoins Fixes', 'Taxe foncière'],
  ['Besoins Fixes', 'EDF'],
  ['Besoins Fixes', 'Mutuelle'],
  ['Besoins Fixes', 'Prevoyance'],
  ['Besoins Fixes', 'Predica 1'],
  ['Besoins Fixes', 'Predica 2'],
  ['Besoins Fixes', 'Assurance protection juridique'],
  ['Besoins Fixes', 'Assurance accident de la vie'],
  ['Besoins Fixes', 'Compte à composer'],
  ['Besoins Fixes', 'Assurance Pro Elise'],
  ['Besoins Fixes', 'Assurance Pro Guillaume'],
  ['Besoins Fixes', 'Orange (Elise)'],
  ['Besoins Fixes', 'SFR (Maison)'],
  ['Besoins Fixes', 'SFR (GG)'],
  ['Besoins Fixes', 'Orange (Thomas)'],
  ['Besoins Fixes', 'Spotify'],
  ['Besoins Fixes', 'Disney'],
  ['Besoins Fixes', 'Youtube'],
  ['Besoins Fixes', 'Google One'],
  ['Besoins Fixes', 'MGEN'],
  ['Besoins Fixes', 'MNT'],
  ['Transports', 'Prêt Partner'],
  ['Transports', 'Prêt Pajero'],
  ['Transports', 'Assurance Pajero'],
  ['Transports', 'Assurance Partner'],
  ['Transports', 'Assurance corporelle conducteur'],
  ['Transports', 'Carburant', 'Carburant'],
  ['Dépenses Variables', 'Alimentation', 'Alimentation'],
  ['Dépenses Variables', 'Loisirs', 'Loisirs'],
  ['Dépenses Variables', 'Divers', 'Divers'],
  ['Épargne', 'Assurance vie Elise'],
  ['Épargne', 'Epargne Elia'],
  ['Épargne', 'Epargne Thomas'],
  ['Épargne', 'Epargne E'],
  ['Épargne', 'Epargne G']
];

/* ------------------------------------------------------------------ */
/* Web app                                                            */
/* ------------------------------------------------------------------ */

function doGet() {
  ensureSetup_();
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Budget')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover')
    .setFaviconUrl('https://ssl.gstatic.com/docs/spreadsheets/favicon3.ico');
}

/** Menu dans la Google Sheet. */
function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('💶 Budget')
    .addItem('Initialiser / réparer les feuilles', 'setup')
    .addToUi();
}

/** À lancer une fois depuis l'éditeur (ou le menu) pour créer les feuilles. */
function setup() {
  ensureSetup_();
}

/* ------------------------------------------------------------------ */
/* API appelée par l'interface (google.script.run)                     */
/* ------------------------------------------------------------------ */

/** Renvoie toutes les données. Le volume reste faible (quelques milliers de lignes). */
function getAll() {
  ensureSetup_();
  var cfg = readConfig_();
  return {
    email: Session.getActiveUser().getEmail() || '',
    sheetUrl: SpreadsheetApp.getActive().getUrl(),
    categories: CATEGORIES,
    auteurs: splitList_(cfg.auteurs),
    categoriesOperations: splitList_(cfg.categoriesOperations),
    lignes: readTable_(SH.LIGNES),
    operations: readTable_(SH.OPS),
    attente: readTable_(SH.ATTENTE),
    epargne: readTable_(SH.EPARGNE)
  };
}

/** Crée le mois `mois` (AAAA-MM) en recopiant le dernier mois existant, sinon le modèle. */
function createMonth(mois) {
  checkMonth_(mois);
  return withLock_(function () {
    var lignes = readTable_(SH.LIGNES);
    if (lignes.some(function (l) { return l.mois === mois; })) return getAll();

    var previous = lignes
      .map(function (l) { return l.mois; })
      .filter(function (m) { return m < mois; })
      .sort()
      .pop();

    var rows = [];
    if (previous) {
      lignes
        .filter(function (l) { return l.mois === previous; })
        .sort(function (a, b) { return a.ordre - b.ordre; })
        .forEach(function (l, i) {
          rows.push({
            id: newId_(), mois: mois, categorie: l.categorie, libelle: l.libelle,
            prevu: l.prevu,
            // Les montants fixes sont supposés identiques au prévu ; à ajuster puis pointer.
            reel: l.lien ? '' : l.prevu,
            pointe: false, lien: l.lien, ordre: i
          });
        });
    } else {
      TEMPLATE.forEach(function (t, i) {
        rows.push({
          id: newId_(), mois: mois, categorie: t[0], libelle: t[1],
          prevu: 0, reel: t[2] ? '' : 0, pointe: false, lien: t[2] || '', ordre: i
        });
      });
    }
    appendRows_(SH.LIGNES, rows);
    return getAll();
  });
}

function saveLine(line) {
  checkMonth_(line.mois);
  if (CATEGORIES.indexOf(line.categorie) < 0) throw new Error('Catégorie inconnue');
  return withLock_(function () {
    var rec = {
      id: line.id || newId_(),
      mois: line.mois,
      categorie: line.categorie,
      libelle: String(line.libelle || '').trim(),
      prevu: num_(line.prevu),
      reel: line.reel === '' || line.reel === null || line.reel === undefined ? '' : num_(line.reel),
      pointe: !!line.pointe,
      lien: String(line.lien || ''),
      ordre: line.ordre === undefined || line.ordre === '' ? nextOrder_(line.mois) : Number(line.ordre)
    };
    upsert_(SH.LIGNES, rec);
    return rec;
  });
}

function setPointe(id, value) {
  return withLock_(function () {
    updateField_(SH.LIGNES, id, 'pointe', !!value);
    return true;
  });
}

function deleteLine(id) {
  return withLock_(function () { deleteById_(SH.LIGNES, id); return true; });
}

/** Import en masse : une ligne par entrée « Catégorie;Libellé;Prévu[;Réel] ». */
function importLines(mois, text) {
  checkMonth_(mois);
  return withLock_(function () {
    var order = nextOrder_(mois);
    var existing = readTable_(SH.LIGNES).filter(function (l) { return l.mois === mois; });
    var rows = [];
    var skipped = [];
    String(text || '').split(/\r?\n/).forEach(function (raw) {
      if (!raw.trim()) return;
      var p = raw.split(/[;\t]/).map(function (s) { return s.trim(); });
      var cat = matchCategory_(p[0]);
      if (!cat || !p[1]) { skipped.push(raw); return; }
      var prevu = num_(p[2]);
      var reel = p[3] !== undefined && p[3] !== '' ? num_(p[3]) : null;
      var found = existing.filter(function (l) {
        return l.categorie === cat && l.libelle.toLowerCase() === p[1].toLowerCase();
      })[0];
      if (found) {
        found.prevu = prevu;
        if (!found.lien) found.reel = reel === null ? prevu : reel;
        upsert_(SH.LIGNES, found);
      } else {
        rows.push({
          id: newId_(), mois: mois, categorie: cat, libelle: p[1], prevu: prevu,
          reel: reel === null ? prevu : reel, pointe: false, lien: '', ordre: order++
        });
      }
    });
    appendRows_(SH.LIGNES, rows);
    return { data: getAll(), skipped: skipped };
  });
}

function saveOperation(op) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(op.date)) throw new Error('Date invalide');
  return withLock_(function () {
    var rec = {
      id: op.id || newId_(),
      mois: op.date.substring(0, 7),
      date: op.date,
      categorie: String(op.categorie || 'Divers'),
      description: String(op.description || '').trim(),
      montant: num_(op.montant),
      auteur: String(op.auteur || ''),
      creeLe: op.creeLe || new Date().toISOString()
    };
    upsert_(SH.OPS, rec);
    return rec;
  });
}

function deleteOperation(id) {
  return withLock_(function () { deleteById_(SH.OPS, id); return true; });
}

function saveAttente(item) {
  return withLock_(function () {
    var rec = {
      id: item.id || newId_(),
      description: String(item.description || '').trim(),
      montant: num_(item.montant),
      creeLe: item.creeLe || new Date().toISOString()
    };
    upsert_(SH.ATTENTE, rec);
    return rec;
  });
}

function deleteAttente(id) {
  return withLock_(function () { deleteById_(SH.ATTENTE, id); return true; });
}

function saveEpargne(item) {
  return withLock_(function () {
    var rec = {
      id: item.id || newId_(),
      compte: String(item.compte || '').trim(),
      solde: num_(item.solde),
      majLe: new Date().toISOString().substring(0, 10)
    };
    upsert_(SH.EPARGNE, rec);
    return rec;
  });
}

function deleteEpargne(id) {
  return withLock_(function () { deleteById_(SH.EPARGNE, id); return true; });
}

/* ------------------------------------------------------------------ */
/* Accès aux feuilles                                                  */
/* ------------------------------------------------------------------ */

function ensureSetup_() {
  var ss = SpreadsheetApp.getActive();
  Object.keys(HEADERS).forEach(function (name) {
    var sh = ss.getSheetByName(name);
    if (!sh) {
      sh = ss.insertSheet(name);
      var headers = HEADERS[name];
      sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
      sh.setFrozenRows(1);
      (TEXT_COLS[name] || []).forEach(function (col) {
        var c = headers.indexOf(col) + 1;
        sh.getRange(1, c, sh.getMaxRows(), 1).setNumberFormat('@');
      });
      if (name === SH.CONFIG) {
        sh.getRange(2, 1, Object.keys(DEFAULT_CONFIG).length, 2).setValues(
          Object.keys(DEFAULT_CONFIG).map(function (k) { return [k, DEFAULT_CONFIG[k]]; })
        );
      }
    }
  });
  var def = ss.getSheetByName('Feuille 1') || ss.getSheetByName('Sheet1');
  if (def && def.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(def);
}

function readConfig_() {
  var cfg = {};
  Object.keys(DEFAULT_CONFIG).forEach(function (k) { cfg[k] = DEFAULT_CONFIG[k]; });
  readTable_(SH.CONFIG).forEach(function (r) { if (r.cle) cfg[r.cle] = r.valeur; });
  return cfg;
}

function readTable_(name) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  var headers = HEADERS[name];
  var last = sh.getLastRow();
  if (last < 2) return [];
  var values = sh.getRange(2, 1, last - 1, headers.length).getValues();
  var tz = Session.getScriptTimeZone();
  return values
    .filter(function (row) { return row[0] !== ''; })
    .map(function (row) {
      var o = {};
      headers.forEach(function (h, i) {
        var v = row[i];
        if (v instanceof Date) {
          v = Utilities.formatDate(v, tz, h === 'mois' ? 'yyyy-MM' : 'yyyy-MM-dd');
        }
        o[h] = v;
      });
      if ('pointe' in o) o.pointe = o.pointe === true || o.pointe === 'TRUE' || o.pointe === 'VRAI';
      if ('ordre' in o) o.ordre = Number(o.ordre) || 0;
      return o;
    });
}

function toRow_(name, rec) {
  return HEADERS[name].map(function (h) {
    var v = rec[h];
    return v === undefined || v === null ? '' : v;
  });
}

function appendRows_(name, recs) {
  if (!recs.length) return;
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  var rows = recs.map(function (r) { return toRow_(name, r); });
  sh.getRange(sh.getLastRow() + 1, 1, rows.length, rows[0].length).setValues(rows);
}

function findRow_(sh, id) {
  var last = sh.getLastRow();
  if (last < 2) return -1;
  var ids = sh.getRange(2, 1, last - 1, 1).getValues();
  for (var i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === String(id)) return i + 2;
  }
  return -1;
}

function upsert_(name, rec) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  var row = findRow_(sh, rec.id);
  if (row < 0) {
    appendRows_(name, [rec]);
  } else {
    sh.getRange(row, 1, 1, HEADERS[name].length).setValues([toRow_(name, rec)]);
  }
}

function updateField_(name, id, field, value) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  var row = findRow_(sh, id);
  if (row < 0) throw new Error('Élément introuvable (supprimé par quelqu\'un d\'autre ?)');
  sh.getRange(row, HEADERS[name].indexOf(field) + 1).setValue(value);
}

function deleteById_(name, id) {
  var sh = SpreadsheetApp.getActive().getSheetByName(name);
  var row = findRow_(sh, id);
  if (row > 0) sh.deleteRow(row);
}

function nextOrder_(mois) {
  var max = -1;
  readTable_(SH.LIGNES).forEach(function (l) {
    if (l.mois === mois && l.ordre > max) max = l.ordre;
  });
  return max + 1;
}

/* ------------------------------------------------------------------ */
/* Utilitaires                                                         */
/* ------------------------------------------------------------------ */

function withLock_(fn) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

function newId_() {
  return Utilities.getUuid().replace(/-/g, '').substring(0, 12);
}

function num_(v) {
  if (typeof v === 'number') return Math.round(v * 100) / 100;
  var s = String(v === undefined || v === null ? '' : v)
    .replace(/[\s  €]/g, '')
    .replace(',', '.');
  var n = parseFloat(s);
  return isNaN(n) ? 0 : Math.round(n * 100) / 100;
}

function splitList_(s) {
  return String(s || '').split(',').map(function (x) { return x.trim(); }).filter(String);
}

function checkMonth_(mois) {
  if (!/^\d{4}-\d{2}$/.test(String(mois))) throw new Error('Mois invalide : ' + mois);
}

function matchCategory_(s) {
  var norm = function (x) {
    return String(x || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
  };
  var target = norm(s);
  for (var i = 0; i < CATEGORIES.length; i++) {
    if (norm(CATEGORIES[i]) === target) return CATEGORIES[i];
  }
  return null;
}
