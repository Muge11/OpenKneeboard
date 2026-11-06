/**
 * Gestion de Budget — application web Google Apps Script.
 *
 * La Google Sheet à laquelle ce script est rattaché sert de base de données.
 * L'interface (Index.html) est servie comme web app (ordinateur + smartphone).
 *
 * Feuilles :
 *  - Libelles      : libellés (catégories) d'entrées / sorties, groupe, budget mensuel
 *  - Recurrents    : mouvements fixes (montant, jour, début, fin ou perpétuel)
 *  - Mouvements    : tous les mouvements réels, y compris les occurrences mensuelles des fixes
 *  - Utilisateurs  : accès, rôles (admin / editeur / lecteur) et couleur
 *  - Journal       : journal des actions
 *  - EnAttente     : dépenses à venir
 *  - Epargne       : soldes des comptes d'épargne
 *  - Config        : paramètres divers
 * Les sauvegardes sont stockées dans un fichier séparé « Budget — Sauvegardes ».
 */

var APP_VERSION = '1.2.0';
var APP_STAGE = '';

var SH = {
  LIB: 'Libelles',
  REC: 'Recurrents',
  MVT: 'Mouvements',
  USERS: 'Utilisateurs',
  JOURNAL: 'Journal',
  ATTENTE: 'EnAttente',
  EPARGNE: 'Epargne',
  ENV: 'Enveloppes',
  VEH: 'Vehicules',
  CPT: 'ComptesEpargne',
  CONFIG: 'Config'
};

var HEADERS = {
  Libelles: ['id', 'nom', 'sens', 'groupe', 'budget', 'ordre', 'actif', 'moisSuivant', 'emoji', 'type', 'parVehicule', 'parCompte'],
  Recurrents: ['id', 'libelleId', 'description', 'montant', 'jour', 'debut', 'fin', 'actif',
    'creePar', 'creeLe', 'modifPar', 'modifLe', 'decalage', 'vehiculeId'],
  Mouvements: ['id', 'mois', 'date', 'libelleId', 'description', 'montant', 'prevu', 'recurrentId',
    'pointe', 'modifie', 'statut', 'saisiPar', 'saisiLe', 'modifPar', 'modifLe', 'vehiculeId', 'source'],
  Utilisateurs: ['email', 'nom', 'role', 'couleur', 'actif', 'ajouteLe', 'prefs', 'versionVue'],
  Journal: ['horodatage', 'utilisateur', 'action', 'objet', 'detail'],
  // portee « suite » : montant par défaut à partir de `debut` ; « mois » : ajustement du seul mois `debut`.
  Enveloppes: ['id', 'libelleId', 'debut', 'montant', 'portee', 'modifPar', 'modifLe'],
  Vehicules: ['id', 'nom', 'emoji', 'actif', 'ordre'],
  // Registre des comptes d'épargne : seul l'identifiant du fichier privé est stocké ici (aucun nom, aucun montant).
  ComptesEpargne: ['id', 'ordre', 'actif', 'creeLe'],
  EnAttente: ['id', 'description', 'montant', 'creeLe', 'saisiPar'],
  Epargne: ['id', 'compte', 'solde', 'majLe', 'saisiPar'],
  Config: ['cle', 'valeur']
};

var NUM_COLS = ['budget', 'ordre', 'montant', 'prevu', 'jour', 'solde', 'decalage'];
var BOOL_COLS = ['actif', 'pointe', 'modifie', 'moisSuivant', 'parVehicule', 'parCompte'];

// Feuilles incluses dans les sauvegardes.
// Les comptes d'épargne (fichiers privés) ne sont pas copiés ici, pour rester confidentiels.
var BACKUP_SHEETS = [SH.LIB, SH.REC, SH.MVT, SH.ENV, SH.VEH, SH.CPT, SH.USERS, SH.ATTENTE, SH.CONFIG];

var DEFAULT_VEHICULES = [['Partner', '🚐'], ['Pajero', '🚙']];
var BACKUP_KEEP = 100;
var BACKUP_CHUNK = 45000;

var ROLES = { lecteur: 0, editeur: 1, admin: 2 };
var COLORS = ['#1f6f5c', '#c2185b', '#1565c0', '#ef6c00', '#6a1b9a', '#2e7d32', '#00838f', '#5d4037'];

var GROUPES = {
  entree: 'Entrées',
  fixes: 'Charges fixes & crédits',
  courant: 'Vie courante'
};

// [nom, sens, groupe, compté le mois suivant, emoji, type]
// type : revenu | besoin | envie | epargne | credit | impot (sert aux conseils budgétaires)
var DEFAULT_LIBELLES = [
  ['Salaires', 'entree', GROUPES.entree, true, '💼', 'revenu'],
  ['Entrées exceptionnelles', 'entree', GROUPES.entree, false, '🎁', 'revenu'],
  ['Remboursements médicaux', 'entree', GROUPES.entree, false, '🩺', 'revenu'],
  ['Remboursements divers', 'entree', GROUPES.entree, false, '↩️', 'revenu'],
  ['Retrait épargne', 'entree', GROUPES.entree, false, '🏦', 'revenu'],
  ['Carburant', 'sortie', GROUPES.fixes, false, '⛽', 'besoin'],
  ['Prêt auto', 'sortie', GROUPES.fixes, false, '🚗', 'credit'],
  ['Prêt maison', 'sortie', GROUPES.fixes, false, '🏠', 'credit'],
  ['Crédit conso', 'sortie', GROUPES.fixes, false, '💳', 'credit'],
  ['Véhicules', 'sortie', GROUPES.fixes, false, '🔧', 'besoin'],
  ['Alimentaire', 'sortie', GROUPES.courant, false, '🛒', 'besoin'],
  ['Divers', 'sortie', GROUPES.courant, false, '📦', 'envie'],
  ['Resto', 'sortie', GROUPES.courant, false, '🍽️', 'envie'],
  ['Loisirs', 'sortie', GROUPES.courant, false, '🎉', 'envie'],
  ['Voyage / Vacances', 'sortie', GROUPES.courant, false, '✈️', 'envie'],
  ['Electricité', 'sortie', GROUPES.courant, false, '⚡', 'besoin'],
  ['Eau', 'sortie', GROUPES.courant, false, '💧', 'besoin'],
  ['Impôts', 'sortie', GROUPES.courant, false, '🏛️', 'impot'],
  ['Abonnements', 'sortie', GROUPES.courant, false, '📱', 'envie'],
  ['Epargne', 'sortie', GROUPES.courant, false, '🐷', 'epargne'],
  ['Scolarité', 'sortie', GROUPES.courant, false, '🎒', 'besoin']
];

var LIB_TYPES = ['revenu', 'besoin', 'envie', 'epargne', 'credit', 'impot'];

// Libellés imputés à un véhicule par défaut.
var PAR_VEHICULE = ['carburant', 'pret auto', 'vehicules'];
// Libellés imputés à un compte d'épargne par défaut (en plus des libellés de type « épargne »).
var PAR_COMPTE = ['epargne', 'retrait epargne'];

/** Type par défaut d'un libellé (libellés existants ou créés à la volée). */
function defaultType_(nom, sens, groupe) {
  if (sens === 'entree') return 'revenu';
  var n = norm_(nom);
  for (var i = 0; i < DEFAULT_LIBELLES.length; i++) {
    if (norm_(DEFAULT_LIBELLES[i][0]) === n) return DEFAULT_LIBELLES[i][5];
  }
  if (/impot|taxe|tresor public|dgfip/.test(n)) return 'impot';
  if (/pret|credit|emprunt/.test(n)) return 'credit';
  if (/epargne|livret|assurance vie/.test(n)) return 'epargne';
  if (/assurance|mutuelle|loyer|sante|pharma|medec|ecole|creche|garde/.test(n)) return 'besoin';
  return groupe === GROUPES.fixes ? 'besoin' : 'envie';
}

function defaultEmoji_(nom) {
  var n = norm_(nom);
  for (var i = 0; i < DEFAULT_LIBELLES.length; i++) {
    if (norm_(DEFAULT_LIBELLES[i][0]) === n) return DEFAULT_LIBELLES[i][4];
  }
  return '';
}

/* ================================================================== */
/* Web app, menu, installation                                         */
/* ================================================================== */

function doGet() {
  ensureSetup_();
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Budget v' + APP_VERSION)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover')
    .setFaviconUrl('https://ssl.gstatic.com/docs/spreadsheets/favicon3.ico')
    // Autorise l'affichage dans l'appli installée (PWA « Budget », sans barre de navigateur).
    // Les données restent protégées : il faut être connecté avec un compte autorisé.
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
}

function onOpen() {
  SpreadsheetApp.getUi()
    .createMenu('💶 Budget')
    .addItem('Installer / mettre à jour', 'setup')
    .addItem('Sauvegarder maintenant', 'menuBackup')
    .addToUi();
}

/** À lancer une fois depuis l'éditeur par le propriétaire (devient administrateur). */
function setup() {
  CacheService.getScriptCache().remove('setup_' + APP_VERSION);
  ensureSetup_();
  var email = Session.getActiveUser().getEmail() || Session.getEffectiveUser().getEmail();
  withLock_(function () {
    var users = readTable_(SH.USERS);
    if (!users.some(function (u) { return u.email === email; })) {
      upsert_(SH.USERS, {
        email: email, nom: email.split('@')[0], role: 'admin', couleur: COLORS[0],
        actif: true, ajouteLe: nowIso_()
      }, 'email');
    }
  });
  CacheService.getScriptCache().remove('users');
  ensureBackupFile_();
  var hasTrigger = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'dailyBackup';
  });
  if (!hasTrigger) ScriptApp.newTrigger('dailyBackup').timeBased().everyDays(1).atHour(3).create();
  // Compte rendu mensuel : vérification toutes les heures (envoi une seule fois par mois).
  if (!ScriptApp.getProjectTriggers().some(function (t) { return t.getHandlerFunction() === 'reportCheck'; })) {
    ScriptApp.newTrigger('reportCheck').timeBased().everyHours(1).create();
  }
  migrateEpargne_(email);
  log_({ email: email }, 'Installation', 'Application', 'v' + APP_VERSION + ' (' + APP_STAGE + ')');
}

function menuBackup() {
  backup_('manuelle', Session.getActiveUser().getEmail());
  SpreadsheetApp.getActive().toast('Sauvegarde effectuée');
}

/* ================================================================== */
/* Lecture                                                              */
/* ================================================================== */

function getAll() {
  ensureSetup_();
  var me = ctx_(0);
  // Le mois suivant aussi : un salaire versé fin de mois pour le mois suivant doit pouvoir être pointé.
  // Le verrou n'est pris que s'il y a réellement des fixes à reporter (ouverture plus rapide, sans attente).
  var cur = currentMonth_(), nxt = shiftMonth_(cur, 1);
  if (ROLES[me.role] >= ROLES.editeur && (monthNeedsFixes_(cur) || monthNeedsFixes_(nxt))) {
    withLock_(function () {
      _memo = { tables: {}, headers: {}, sheets: {} };
      ensureMonth_(cur, me);
      ensureMonth_(nxt, me);
    });
  }
  return data_(me);
}

/** Vrai si un fixe actif sur `mois` n'a pas encore d'occurrence (lecture seule, sans verrou). */
function monthNeedsFixes_(mois) {
  var recs = readTable_(SH.REC).filter(function (r) { return inRange_(r, mois); });
  if (!recs.length) return false;
  var have = {};
  readTable_(SH.MVT).forEach(function (m) { if (m.mois === mois && m.recurrentId) have[m.recurrentId] = true; });
  return recs.some(function (r) { return !have[r.id]; });
}

/** Toutes les données (sans verrou : utilisable depuis une fonction déjà verrouillée). */
function data_(me) {
  var props = PropertiesService.getScriptProperties();
  if (me.role === 'admin' && !getConfig_('mig050_epargne')) {
    try { migrateEpargne_(me.email); } catch (e) { /* refait au prochain setup */ }
  }
  return {
    version: APP_VERSION,
    stage: APP_STAGE,
    me: me,
    users: readTable_(SH.USERS),
    libelles: readTable_(SH.LIB),
    recurrents: readTable_(SH.REC),
    mouvements: readTable_(SH.MVT),
    attente: readTable_(SH.ATTENTE),
    comptes: comptes_(me),
    releases: RELEASES,
    enveloppes: readTable_(SH.ENV),
    vehicules: readTable_(SH.VEH),
    reperes: getConfig_('reperes'),
    paie: getConfig_('paie'),
    sheetUrl: SpreadsheetApp.getActive().getUrl(),
    appUrl: (function () { try { return ScriptApp.getService().getUrl(); } catch (e) { return ''; } })(),
    lastBackup: props.getProperty('lastBackup') || ''
  };
}

/** Génère les occurrences des mouvements fixes pour `mois` ; renvoie les lignes créées. */
function ensureMonth(mois) {
  checkMonth_(mois);
  var me = ctx_(ROLES.editeur);
  return withLock_(function () { return ensureMonth_(mois, me); });
}

function getJournal(limit) {
  ctx_(ROLES.admin);
  var sh = sheet_(SH.JOURNAL);
  var last = sh.getLastRow();
  if (last < 2) return [];
  var n = Math.min(limit || 300, last - 1);
  var values = sh.getRange(last - n + 1, 1, n, HEADERS.Journal.length).getDisplayValues();
  return values.reverse().map(function (r) {
    return { horodatage: r[0], utilisateur: r[1], action: r[2], objet: r[3], detail: r[4] };
  });
}

/* ================================================================== */
/* Mouvements                                                           */
/* ================================================================== */

function saveMouvement(m, repeter) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () { return saveMouvementAs_(me, m, repeter); });
}

/** Enregistre un mouvement pour `me` (appelé sous verrou). */
function saveMouvementAs_(me, m, repeter) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(m.date)) throw new Error('Date invalide');
  return (function () {
    var lib = findBy_(SH.LIB, 'id', m.libelleId);
    if (!lib) throw new Error('Libellé inconnu');
    var old = m.id ? findBy_(SH.MVT, 'id', m.id) : null;
    // L'identifiant peut être fourni par l'appli (enregistrement en arrière-plan).
    var rec = old ? clone_(old) : {
      id: /^[a-z0-9]{8,20}$/.test(m.id || '') ? m.id : newId_(),
      recurrentId: '', prevu: '', pointe: false, modifie: false, statut: '',
      saisiPar: me.email, saisiLe: nowIso_(), source: m.source === 'vocal' ? 'vocal' : ''
    };
    // Un mouvement dicté, revu et enregistré à la main, n'est plus « à vérifier ».
    if (old && old.source === 'vocal' && m.source !== 'vocal') rec.source = 'vocal-ok';
    // mois = mois de budget (peut différer du mois de la date, ex. salaire du 28/10 pour novembre)
    rec.mois = /^\d{4}-\d{2}$/.test(m.mois || '') ? m.mois : m.date.substring(0, 7);
    rec.date = m.date;
    rec.libelleId = m.libelleId;
    rec.description = String(m.description || '').trim();
    rec.montant = num_(m.montant);
    rec.vehiculeId = lib.parVehicule ? String(m.vehiculeId || '') : '';
    if (m.pointe !== undefined) rec.pointe = !!m.pointe;

    if (old) {
      if (old.recurrentId && (old.montant !== rec.montant || old.date !== rec.date ||
          old.description !== rec.description || old.libelleId !== rec.libelleId || old.vehiculeId !== rec.vehiculeId)) {
        rec.modifie = true;
      }
      rec.modifPar = me.email;
      rec.modifLe = nowIso_();
    }

    var created = null;
    if (!old && repeter) {
      created = {
        id: newId_(), libelleId: rec.libelleId, description: rec.description, montant: rec.montant,
        jour: Number(rec.date.substring(8, 10)), debut: rec.mois, fin: repeter.fin || '',
        decalage: rec.date.substring(0, 7) < rec.mois ? -1 : 0, vehiculeId: rec.vehiculeId,
        actif: true, creePar: me.email, creeLe: nowIso_(), modifPar: '', modifLe: ''
      };
      if (created.fin && created.fin < created.debut) throw new Error('L\'échéance de fin est avant le début');
      upsert_(SH.REC, created);
      rec.recurrentId = created.id;
      rec.prevu = rec.montant;
      log_(me, 'Création', 'Mouvement fixe', describeRec_(created, lib));
    }

    upsert_(SH.MVT, rec);
    if (lib.parCompte && m.compteId !== undefined) setImputation_(me, String(m.compteId || ''), 'mvt', rec.id);
    if (created && lib.parCompte && m.compteId) setImputation_(me, String(m.compteId), 'rec', created.id);
    log_(me, old ? 'Modification' : 'Ajout', 'Mouvement',
      describeMvt_(rec, lib) + (old ? ' (avant : ' + fmtEur_(old.montant) + ', ' + old.date + ')' : ''));
    return { mouvement: rec, recurrent: created };
  })();
}

function deleteMouvement(id) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var m = findBy_(SH.MVT, 'id', id);
    if (!m) return true;
    var lib = findBy_(SH.LIB, 'id', m.libelleId);
    if (m.recurrentId) {
      // On garde une trace pour que l'occurrence ne soit pas recréée ce mois-ci.
      m.statut = 'annule';
      m.modifPar = me.email;
      m.modifLe = nowIso_();
      upsert_(SH.MVT, m);
      log_(me, 'Retrait du mois', 'Mouvement fixe', describeMvt_(m, lib));
    } else {
      deleteBy_(SH.MVT, 'id', id);
      log_(me, 'Suppression', 'Mouvement', describeMvt_(m, lib));
    }
    return true;
  });
}

function restoreOccurrence(id) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var m = findBy_(SH.MVT, 'id', id);
    if (!m) throw new Error('Introuvable');
    m.statut = '';
    upsert_(SH.MVT, m);
    log_(me, 'Rétablissement', 'Mouvement fixe', describeMvt_(m, findBy_(SH.LIB, 'id', m.libelleId)));
    return m;
  });
}

/** Pointage groupé : [{ id, pointe }] en un seul envoi (pointage d'un relevé). */
function setPointes(list) {
  var me = ctx_(ROLES.editeur);
  if (!list || !list.length) return 0;
  return withLock_(function () {
    _memo = { tables: {}, headers: {}, sheets: {} };
    var sh = sheet_(SH.MVT);
    readTable_(SH.MVT); // remplit le repère des lignes
    var col = header_(SH.MVT).indexOf('pointe') + 1;
    var rows = _memo.rows[SH.MVT] || {};
    var n = 0, on = 0;
    list.forEach(function (it) {
      var row = rows[String(it.id).toLowerCase()];
      if (!row) return;
      sh.getRange(row, col).setValue(!!it.pointe);
      n++; if (it.pointe) on++;
    });
    invalidate_(SH.MVT);
    if (n) log_(me, 'Pointage', 'Mouvements', n === 1 ? (on ? '1 mouvement pointé' : '1 mouvement dépointé') :
      n + ' mouvements (' + on + ' pointé(s), ' + (n - on) + ' dépointé(s))');
    return n;
  });
}

function setPointe(id, value) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var m = findBy_(SH.MVT, 'id', id);
    if (!m) throw new Error('Mouvement introuvable (supprimé par quelqu\'un d\'autre ?)');
    m.pointe = !!value;
    upsert_(SH.MVT, m);
    log_(me, value ? 'Pointage' : 'Dépointage', 'Mouvement',
      describeMvt_(m, findBy_(SH.LIB, 'id', m.libelleId)));
    return true;
  });
}

/* ================================================================== */
/* Mouvements fixes                                                     */
/* ================================================================== */

/**
 * Crée ou modifie un mouvement fixe. Les occurrences à partir de `aPartirDe`
 * qui n'ont été ni pointées ni modifiées à la main sont mises à jour.
 */
function saveRecurrent(r, aPartirDe) {
  var me = ctx_(ROLES.editeur);
  checkMonth_(r.debut);
  if (r.fin) checkMonth_(r.fin);
  if (r.fin && r.fin < r.debut) throw new Error('L\'échéance de fin est avant le début');
  aPartirDe = aPartirDe || currentMonth_();
  checkMonth_(aPartirDe);
  return withLock_(function () {
    var lib = findBy_(SH.LIB, 'id', r.libelleId);
    if (!lib) throw new Error('Libellé inconnu');
    var old = r.id ? findBy_(SH.REC, 'id', r.id) : null;
    var rec = old ? clone_(old) : { id: newId_(), creePar: me.email, creeLe: nowIso_() };
    rec.libelleId = r.libelleId;
    rec.description = String(r.description || '').trim();
    rec.montant = num_(r.montant);
    rec.jour = Math.min(31, Math.max(1, Number(r.jour) || 1));
    rec.decalage = Number(r.decalage) < 0 ? -1 : 0;
    rec.vehiculeId = lib.parVehicule ? String(r.vehiculeId || '') : '';
    rec.debut = r.debut;
    rec.fin = r.fin || '';
    rec.actif = r.actif !== false;
    if (old) { rec.modifPar = me.email; rec.modifLe = nowIso_(); }
    upsert_(SH.REC, rec);
    if (lib.parCompte && r.compteId !== undefined) setImputation_(me, String(r.compteId || ''), 'rec', rec.id);
    var n = propagate_(rec, aPartirDe);
    ensureMonth_(currentMonth_(), me);
    log_(me, old ? 'Modification' : 'Création', 'Mouvement fixe',
      describeRec_(rec, lib) + (old ? ' (avant : ' + fmtEur_(old.montant) + ', fin ' + (old.fin || 'perpétuel') +
        ') — appliqué à partir de ' + aPartirDe + ', ' + n + ' occurrence(s) mise(s) à jour' : ''));
    return data_(me);
  });
}

/** Arrête un mouvement fixe : il ne sera plus reporté à partir de `aPartirDe`. */
function stopRecurrent(id, aPartirDe) {
  var me = ctx_(ROLES.editeur);
  checkMonth_(aPartirDe);
  return withLock_(function () {
    var rec = findBy_(SH.REC, 'id', id);
    if (!rec) throw new Error('Introuvable');
    var prev = shiftMonth_(aPartirDe, -1);
    if (prev < rec.debut) {
      rec.actif = false;
    } else {
      rec.fin = prev;
    }
    rec.modifPar = me.email;
    rec.modifLe = nowIso_();
    upsert_(SH.REC, rec);
    var n = propagate_(rec, aPartirDe);
    log_(me, 'Arrêt', 'Mouvement fixe', describeRec_(rec, findBy_(SH.LIB, 'id', rec.libelleId)) +
      ' — à partir de ' + aPartirDe + ', ' + n + ' occurrence(s) retirée(s)');
    return data_(me);
  });
}

/** Import : « E|S;Libellé;Description;Montant[;Fin AAAA-MM][;Jour] » par ligne. */
function importRecurrents(text) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var libs = readTable_(SH.LIB);
    var mois = currentMonth_();
    var skipped = [];
    var count = 0;
    String(text || '').split(/\r?\n/).forEach(function (raw) {
      if (!raw.trim()) return;
      var p = raw.split(/[;\t]/).map(function (s) { return s.trim(); });
      var sens = /^e/i.test(p[0]) ? 'entree' : /^s/i.test(p[0]) ? 'sortie' : null;
      if (!sens || !p[1] || p[3] === undefined) { skipped.push(raw); return; }
      var lib = libs.filter(function (l) { return norm_(l.nom) === norm_(p[1]) && l.sens === sens; })[0];
      if (!lib) {
        lib = {
          id: newId_(), nom: p[1], sens: sens, groupe: sens === 'entree' ? GROUPES.entree : GROUPES.fixes,
          budget: 0, ordre: libs.length, actif: true, emoji: defaultEmoji_(p[1]),
          type: defaultType_(p[1], sens, sens === 'entree' ? GROUPES.entree : GROUPES.fixes),
          parVehicule: sens === 'sortie' && PAR_VEHICULE.indexOf(norm_(p[1])) >= 0
        };
        upsert_(SH.LIB, lib);
        libs.push(lib);
        log_(me, 'Création', 'Libellé', lib.nom + ' (import)');
      }
      var fin = p[4] && /^\d{4}-\d{2}$/.test(p[4]) ? p[4] : '';
      var rec = {
        id: newId_(), libelleId: lib.id, description: p[2] || lib.nom, montant: num_(p[3]),
        jour: Number(p[5]) || 1, debut: mois, fin: fin, actif: true, creePar: me.email, creeLe: nowIso_(),
        decalage: lib.moisSuivant ? -1 : 0, vehiculeId: ''
      };
      if (lib.parVehicule) {
        var vs = readTable_(SH.VEH).filter(function (v) { return norm_(rec.description).indexOf(norm_(v.nom)) >= 0; });
        if (vs.length === 1) rec.vehiculeId = vs[0].id;
      }
      upsert_(SH.REC, rec);
      count++;
    });
    ensureMonth_(mois, me);
    log_(me, 'Import', 'Mouvements fixes', count + ' importé(s), ' + skipped.length + ' ignoré(s)');
    return { data: data_(me), count: count, skipped: skipped };
  });
}

/* ================================================================== */
/* Libellés                                                             */
/* ================================================================== */

function saveLibelle(l) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var nom = String(l.nom || '').trim();
    if (!nom) throw new Error('Nom manquant');
    var sens = l.sens === 'entree' ? 'entree' : 'sortie';
    var libs = readTable_(SH.LIB);
    var old = l.id ? libs.filter(function (x) { return x.id === l.id; })[0] : null;
    if (old && ROLES[me.role] < ROLES.admin) throw new Error('Seul un administrateur peut modifier un libellé');
    if (libs.some(function (x) { return x.id !== l.id && x.sens === sens && norm_(x.nom) === norm_(nom); })) {
      throw new Error('Ce libellé existe déjà');
    }
    var rec = old ? clone_(old) : { id: newId_(), ordre: libs.length, actif: true };
    rec.nom = nom;
    rec.sens = sens;
    rec.groupe = String(l.groupe || '').trim() || (sens === 'entree' ? GROUPES.entree : GROUPES.courant);
    rec.budget = old ? old.budget : 0; // remplacé par les enveloppes (v0.2.0)
    rec.moisSuivant = !!l.moisSuivant;
    rec.emoji = String(l.emoji || '').trim().substring(0, 8);
    rec.type = LIB_TYPES.indexOf(l.type) >= 0 ? l.type : defaultType_(rec.nom, rec.sens, rec.groupe);
    rec.parVehicule = l.parVehicule !== undefined ? !!l.parVehicule && rec.sens === 'sortie' : (old ? !!old.parVehicule : false);
    rec.parCompte = l.parCompte !== undefined ? !!l.parCompte : (old ? !!old.parCompte : rec.type === 'epargne');
    if (l.actif !== undefined) rec.actif = !!l.actif;
    upsert_(SH.LIB, rec);
    log_(me, old ? 'Modification' : 'Création', 'Libellé',
      rec.nom + ' (' + rec.sens + ', ' + rec.groupe +
      (rec.moisSuivant ? ', compté le mois suivant' : '') + ')' +
      (old && old.nom !== rec.nom ? ' — ancien nom : ' + old.nom : ''));
    return rec;
  });
}

function deleteLibelle(id) {
  var me = ctx_(ROLES.admin);
  return withLock_(function () {
    var lib = findBy_(SH.LIB, 'id', id);
    if (!lib) return { deleted: true };
    var used = readTable_(SH.MVT).some(function (m) { return m.libelleId === id; }) ||
      readTable_(SH.REC).some(function (r) { return r.libelleId === id; });
    if (used) {
      lib.actif = false;
      upsert_(SH.LIB, lib);
      log_(me, 'Archivage', 'Libellé', lib.nom + ' (utilisé, conservé pour l\'historique)');
      return { deleted: false, libelle: lib };
    }
    deleteBy_(SH.LIB, 'id', id);
    log_(me, 'Suppression', 'Libellé', lib.nom);
    return { deleted: true };
  });
}

/* ================================================================== */
/* Utilisateurs                                                         */
/* ================================================================== */

function saveUser(u) {
  var me = ctx_(ROLES.admin);
  var email = String(u.email || '').trim().toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw new Error('Adresse e-mail invalide');
  if (!(u.role in ROLES)) throw new Error('Rôle invalide');
  return withLock_(function () {
    var users = readTable_(SH.USERS);
    var old = users.filter(function (x) { return x.email === email; })[0];
    var admins = users.filter(function (x) { return x.role === 'admin' && x.actif && x.email !== email; });
    if (old && old.role === 'admin' && (u.role !== 'admin' || u.actif === false) && !admins.length) {
      throw new Error('Il doit rester au moins un administrateur');
    }
    var rec = {
      email: email,
      nom: String(u.nom || '').trim() || email.split('@')[0],
      role: u.role,
      couleur: /^#[0-9a-f]{6}$/i.test(u.couleur) ? u.couleur : COLORS[users.length % COLORS.length],
      actif: u.actif !== false,
      ajouteLe: old ? old.ajouteLe : nowIso_(),
      prefs: old ? old.prefs : '',
      versionVue: old ? old.versionVue : ''
    };
    if (u.rapport !== undefined) {
      var pr = parsePrefs_(rec.prefs); pr.rapport = !!u.rapport; rec.prefs = JSON.stringify(pr);
    }
    upsert_(SH.USERS, rec, 'email');
    CacheService.getScriptCache().remove('users');
    var partage = shareWith_(rec);
    if (old && (old.role !== rec.role || old.actif !== rec.actif)) resyncComptesSharing_();
    log_(me, old ? 'Modification' : 'Ajout', 'Utilisateur',
      rec.nom + ' <' + rec.email + '> — ' + rec.role + (rec.actif ? '' : ' (désactivé)') +
      (old && old.role !== rec.role ? ' (avant : ' + old.role + ')' : '') + (partage ? '' : ' — partage à faire manuellement'));
    return { user: rec, partage: partage };
  });
}

function shareWith_(u) {
  var ok = true;
  var files = [SpreadsheetApp.getActive()];
  var bid = getConfig_('backupSpreadsheetId');
  if (bid) { try { files.push(SpreadsheetApp.openById(bid)); } catch (e) { ok = false; } }
  files.forEach(function (f) {
    try {
      if (!u.actif) {
        f.removeEditor(u.email);
      } else if (u.role === 'lecteur') {
        f.addViewer(u.email);
      } else {
        f.addEditor(u.email);
      }
    } catch (e) {
      ok = false;
    }
  });
  return ok;
}

/* ================================================================== */
/* En attente / Épargne                                                 */
/* ================================================================== */

function saveAttente(item) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var old = item.id ? findBy_(SH.ATTENTE, 'id', item.id) : null;
    var rec = {
      id: item.id || newId_(),
      description: String(item.description || '').trim(),
      montant: num_(item.montant),
      creeLe: old ? old.creeLe : nowIso_(),
      saisiPar: old ? old.saisiPar : me.email
    };
    upsert_(SH.ATTENTE, rec);
    log_(me, old ? 'Modification' : 'Ajout', 'En attente', rec.description + ' ' + fmtEur_(rec.montant));
    return rec;
  });
}

function deleteAttente(id) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var old = findBy_(SH.ATTENTE, 'id', id);
    deleteBy_(SH.ATTENTE, 'id', id);
    if (old) log_(me, 'Suppression', 'En attente', old.description + ' ' + fmtEur_(old.montant));
    return true;
  });
}

/* ================================================================== */
/* Saisie vocale : supprimée en v0.7.0                                  */
/* ================================================================== */

/**
 * Ancien déclencheur de la saisie vocale (v0.6.0). S'il existe encore sur le compte d'un utilisateur,
 * il se supprime lui-même à sa prochaine exécution.
 */
function voiceCheck() {
  ScriptApp.getProjectTriggers().forEach(function (t) {
    if (t.getHandlerFunction() === 'voiceCheck') ScriptApp.deleteTrigger(t);
  });
}

/* ================================================================== */
/* Ponctuel <-> fixe                                                     */
/* ================================================================== */

/** Transforme un mouvement ponctuel en mouvement fixe (il en devient la 1re occurrence). */
function toFixe(id, fin) {
  var me = ctx_(ROLES.editeur);
  if (fin) checkMonth_(fin);
  return withLock_(function () {
    var m = findBy_(SH.MVT, 'id', id);
    if (!m) throw new Error('Mouvement introuvable');
    if (m.recurrentId) throw new Error('Ce mouvement est déjà fixe');
    if (fin && fin < m.mois) throw new Error('L\'échéance de fin est avant le mois du mouvement');
    var lib = findBy_(SH.LIB, 'id', m.libelleId);
    var rec = {
      id: newId_(), libelleId: m.libelleId, description: m.description, montant: m.montant,
      jour: Number(m.date.substring(8, 10)), debut: m.mois, fin: fin || '', actif: true,
      creePar: me.email, creeLe: nowIso_(), modifPar: '', modifLe: '',
      decalage: m.date.substring(0, 7) < m.mois ? -1 : 0, vehiculeId: m.vehiculeId || ''
    };
    upsert_(SH.REC, rec);
    var mm = clone_(m);
    mm.recurrentId = rec.id;
    mm.prevu = m.montant;
    mm.modifPar = me.email;
    mm.modifLe = nowIso_();
    upsert_(SH.MVT, mm);
    ensureMonth_(currentMonth_(), me);
    ensureMonth_(shiftMonth_(currentMonth_(), 1), me);
    log_(me, 'Ponctuel → fixe', 'Mouvement', describeRec_(rec, lib));
    return data_(me);
  });
}

/**
 * Transforme l'occurrence d'un fixe en mouvement ponctuel : le fixe n'est plus reporté
 * après ce mois ; les occurrences futures non pointées / non modifiées sont retirées.
 */
function toPonctuel(id) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var m = findBy_(SH.MVT, 'id', id);
    if (!m || !m.recurrentId) throw new Error('Ce mouvement n\'est pas un fixe');
    var rec = findBy_(SH.REC, 'id', m.recurrentId);
    var mm = clone_(m);
    mm.recurrentId = '';
    mm.prevu = '';
    mm.modifie = false;
    mm.modifPar = me.email;
    mm.modifLe = nowIso_();
    upsert_(SH.MVT, mm);
    var n = 0;
    if (rec) {
      if (m.mois < rec.debut) rec.actif = false; else rec.fin = m.mois;
      rec.modifPar = me.email;
      rec.modifLe = nowIso_();
      upsert_(SH.REC, rec);
      n = propagate_(rec, shiftMonth_(m.mois, 1));
    }
    log_(me, 'Fixe → ponctuel', 'Mouvement', describeMvt_(mm, findBy_(SH.LIB, 'id', mm.libelleId)) +
      ' — fixe arrêté après ' + m.mois + ', ' + n + ' occurrence(s) future(s) retirée(s)');
    return data_(me);
  });
}

/* ================================================================== */
/* Véhicules                                                             */
/* ================================================================== */

function saveVehicule(v) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var nom = String(v.nom || '').trim();
    if (!nom) throw new Error('Nom manquant');
    var vehs = readTable_(SH.VEH);
    var old = v.id ? vehs.filter(function (x) { return x.id === v.id; })[0] : null;
    if (vehs.some(function (x) { return x.id !== v.id && norm_(x.nom) === norm_(nom); })) throw new Error('Ce véhicule existe déjà');
    var rec = old ? clone_(old) : { id: newId_(), ordre: vehs.length };
    rec.nom = nom;
    rec.emoji = String(v.emoji || '').trim().substring(0, 8);
    rec.actif = v.actif !== false;
    upsert_(SH.VEH, rec);
    log_(me, old ? 'Modification' : 'Ajout', 'Véhicule', rec.nom + (rec.actif ? '' : ' (vendu / inactif)'));
    return rec;
  });
}

/* ================================================================== */
/* Repères budgétaires (conseils)                                        */
/* ================================================================== */

/**
 * Jour de paye du foyer : { jour: 1-31 (vide = mois calendaire), weekend: avancer au vendredi,
 * mois: { 'AAAA-MM' (mois de budget financé) : 'AAAA-MM-JJ' (date réelle de la paye) } }.
 */
function savePaie(paie) {
  var me = ctx_(ROLES.editeur);
  paie = paie || {};
  var jour = parseInt(paie.jour, 10);
  var out = { jour: jour >= 1 && jour <= 31 ? jour : '', weekend: !!paie.weekend, mois: {} };
  Object.keys(paie.mois || {}).forEach(function (m) {
    var d = String(paie.mois[m] || '');
    if (/^\d{4}-\d{2}$/.test(m) && /^\d{4}-\d{2}-\d{2}$/.test(d)) out.mois[m] = d;
  });
  var json = JSON.stringify(out);
  if (json.length > 20000) throw new Error('Trop de dates de paye');
  return withLock_(function () {
    setConfig_('paie', json);
    log_(me, 'Modification', 'Jour de paye', json.substring(0, 300));
    return json;
  });
}

function saveReperes(reperes) {
  var me = ctx_(ROLES.admin);
  var json = JSON.stringify(reperes || {});
  if (json.length > 20000) throw new Error('Repères trop volumineux');
  return withLock_(function () {
    setConfig_('reperes', json);
    log_(me, 'Modification', 'Repères budgétaires', json.substring(0, 300));
    return json;
  });
}

/* ================================================================== */
/* Enveloppes                                                           */
/* ================================================================== */

/** Montant de l'enveloppe d'un libellé pour un mois (0 = pas d'enveloppe). */
function envelopeFor_(envs, libelleId, mois) {
  var override = null, def = null;
  envs.forEach(function (e) {
    if (e.libelleId !== libelleId) return;
    if (e.portee === 'mois' && e.debut === mois) override = e;
    if (e.portee !== 'mois' && e.debut <= mois && (!def || e.debut > def.debut)) def = e;
  });
  return override ? Number(override.montant) : def ? Number(def.montant) : 0;
}

/**
 * Fixe l'enveloppe d'un libellé.
 *  - portee « mois »  : uniquement pour `mois`.
 *  - portee « suite » : pour `mois` et les mois suivants (les ajustements ultérieurs sont remplacés).
 * Les mois passés ne changent pas.
 */
function saveEnveloppe(e) {
  var me = ctx_(ROLES.editeur);
  checkMonth_(e.mois);
  var portee = e.portee === 'mois' ? 'mois' : 'suite';
  return withLock_(function () {
    var lib = findBy_(SH.LIB, 'id', e.libelleId);
    if (!lib) throw new Error('Libellé inconnu');
    var envs = readTable_(SH.ENV);
    var avant = envelopeFor_(envs, e.libelleId, e.mois);
    var montant = num_(e.montant);
    envs.forEach(function (x) {
      if (x.libelleId !== e.libelleId) return;
      var remove = portee === 'mois'
        ? (x.portee === 'mois' && x.debut === e.mois)
        : (x.debut >= e.mois);
      if (remove) deleteBy_(SH.ENV, 'id', x.id);
    });
    appendRows_(SH.ENV, [{
      id: newId_(), libelleId: e.libelleId, debut: e.mois, montant: montant, portee: portee,
      modifPar: me.email, modifLe: nowIso_()
    }]);
    log_(me, 'Enveloppe', lib.nom, fmtEur_(avant) + ' → ' + fmtEur_(montant) + ' — ' +
      (portee === 'mois' ? e.mois + ' uniquement' : 'à partir de ' + e.mois));
    return readTable_(SH.ENV);
  });
}

/* ================================================================== */
/* Comptes d'épargne — confidentialité réelle                           */
/* ================================================================== */
/*
 * Chaque compte est un fichier Google Sheets séparé (« Budget — Épargne · <nom> »), partagé
 * UNIQUEMENT avec l'administrateur et les personnes autorisées. Il contient :
 *  - feuille « Compte »      : nom, emoji, solde de référence + date, personnes autorisées ;
 *  - feuille « Imputations » : mouvements (mvt) ou fixes (rec) rattachés au compte.
 * Le fichier principal ne contient que l'identifiant de chaque fichier : une personne non autorisée
 * ne peut lire ni le nom, ni le solde, ni les imputations (Drive refuse l'accès).
 */
var _comptes = null;

function compteFiles_() {
  return readTable_(SH.CPT).filter(function (c) { return c.actif; });
}

function readCompte_(id) {
  var f;
  try { f = SpreadsheetApp.openById(id); } catch (e) { return null; } // pas d'accès = pas autorisé
  var meta = {};
  var cs = f.getSheetByName('Compte');
  if (!cs) return null;
  cs.getDataRange().getValues().forEach(function (r) { if (r[0]) meta[r[0]] = r[1]; });
  var imp = [];
  var is = f.getSheetByName('Imputations');
  if (is && is.getLastRow() > 1) {
    is.getRange(2, 1, is.getLastRow() - 1, 2).getValues().forEach(function (r) {
      if (r[0]) imp.push({ kind: String(r[0]), refId: String(r[1]) });
    });
  }
  var visibles = [];
  try { visibles = JSON.parse(meta.visibles || '[]'); } catch (e) {}
  var dateRef = meta.dateRef instanceof Date ? Utilities.formatDate(meta.dateRef, Session.getScriptTimeZone(), 'yyyy-MM-dd') : String(meta.dateRef || '');
  return {
    id: id, nom: String(meta.nom || '?'), emoji: String(meta.emoji || ''), soldeRef: Number(meta.soldeRef) || 0,
    dateRef: dateRef, visibles: visibles, ordre: Number(meta.ordre) || 0, imputations: imp, file: f
  };
}

/**
 * Lecture d'un compte avec cache serveur (10 min), vidé à chaque modification.
 * Le cache n'est jamais renvoyé sans le contrôle de visibilité de publicCompte_.
 */
function compteCached_(id) {
  var cache = CacheService.getScriptCache(), k = 'cpt_' + id;
  var hit = cache.get(k);
  if (hit) { try { return JSON.parse(hit); } catch (e) {} }
  var x = readCompte_(id);
  if (!x) return null;
  var o = { id: x.id, nom: x.nom, emoji: x.emoji, soldeRef: x.soldeRef, dateRef: x.dateRef, visibles: x.visibles,
    ordre: x.ordre, imputations: x.imputations, url: x.file.getUrl() };
  try { cache.put(k, JSON.stringify(o), 600); } catch (e) { /* trop volumineux : pas de cache */ }
  return o;
}

function uncacheComptes_() {
  try { CacheService.getScriptCache().removeAll(compteFiles_().map(function (c) { return 'cpt_' + c.id; })); } catch (e) {}
}

/** Comptes visibles par `me` (l'administrateur voit tout). */
function comptes_(me) {
  if (!_comptes) {
    _comptes = [];
    compteFiles_().forEach(function (c) {
      var x = compteCached_(c.id);
      if (x) _comptes.push(x);
    });
  }
  return _comptes.map(function (c) { return publicCompte_(c, me); }).filter(Boolean);
}

function canSeeCompte_(c, email) {
  var u = findBy_(SH.USERS, 'email', email);
  if (u && u.role === 'admin') return true;
  return c.visibles.map(function (e) { return String(e).toLowerCase(); }).indexOf(String(email).toLowerCase()) >= 0;
}

function publicCompte_(c, me) {
  if (!canSeeCompte_(c, me.email)) return null;
  var o = { id: c.id, nom: c.nom, emoji: c.emoji, soldeRef: c.soldeRef, dateRef: c.dateRef, ordre: c.ordre, imputations: c.imputations };
  if (me.role === 'admin') { o.visibles = c.visibles; o.url = c.url || (c.file ? c.file.getUrl() : ''); }
  return o;
}

function writeCompteMeta_(f, meta) {
  var cs = f.getSheetByName('Compte');
  var rows = Object.keys(meta).map(function (k) { return [k, typeof meta[k] === 'object' ? JSON.stringify(meta[k]) : meta[k]]; });
  cs.clearContents();
  cs.getRange(1, 1, rows.length, 2).setNumberFormat('@');
  cs.getRange(1, 1, rows.length, 2).setValues(rows.map(function (r) { return [r[0], String(r[1])]; }));
}

function newCompteFile_(nom) {
  var f = SpreadsheetApp.create('Budget — Épargne · ' + nom);
  f.getSheets()[0].setName('Compte');
  var is = f.insertSheet('Imputations');
  is.getRange(1, 1, 1, 4).setValues([['type', 'refId', 'parQui', 'le']]);
  is.getRange(2, 1, 999, 2).setNumberFormat('@');
  return f;
}

/** Partage du fichier d'un compte : administrateurs + personnes autorisées ; retrait des autres. */
function shareCompte_(f, visibles) {
  var allowed = visibles.map(function (e) { return String(e).toLowerCase(); });
  var owner = String(Session.getEffectiveUser().getEmail()).toLowerCase();
  readTable_(SH.USERS).forEach(function (u) {
    var em = u.email.toLowerCase();
    if (em === owner) return;
    var ok = u.actif && (u.role === 'admin' || allowed.indexOf(em) >= 0);
    try {
      if (ok && u.role === 'lecteur') f.addViewer(em);
      else if (ok) f.addEditor(em);
      else { try { f.removeEditor(em); } catch (e) {} try { f.removeViewer(em); } catch (e) {} }
    } catch (e) { /* adresse non Google, etc. */ }
  });
}

function resyncComptesSharing_() {
  _comptes = null;
  compteFiles_().forEach(function (c) {
    var x = readCompte_(c.id);
    if (x) shareCompte_(x.file, x.visibles);
  });
}

/** Création / modification d'un compte : administrateur uniquement. */
function saveCompte(c) {
  var me = ctx_(ROLES.admin);
  var nom = String(c.nom || '').trim();
  if (!nom) throw new Error('Nom du compte manquant');
  if (c.dateRef && !/^\d{4}-\d{2}-\d{2}$/.test(c.dateRef)) throw new Error('Date de référence invalide');
  var visibles = (c.visibles || []).map(function (e) { return String(e).toLowerCase(); }).filter(function (e) { return /@/.test(e); });
  return withLock_(function () {
    var f, ordre;
    if (c.id) {
      var x = readCompte_(c.id);
      if (!x) throw new Error('Compte introuvable');
      f = x.file; ordre = x.ordre;
    } else {
      f = newCompteFile_(nom);
      ordre = compteFiles_().length;
      appendRows_(SH.CPT, [{ id: f.getId(), ordre: ordre, actif: true, creeLe: nowIso_() }]);
    }
    writeCompteMeta_(f, {
      nom: nom, emoji: String(c.emoji || '🐷').substring(0, 8), soldeRef: num_(c.soldeRef),
      dateRef: c.dateRef || nowIso_().substring(0, 10), visibles: visibles, ordre: ordre
    });
    if (c.id) f.rename('Budget — Épargne · ' + nom);
    shareCompte_(f, visibles);
    uncacheComptes_();
    _comptes = null;
    // Journal volontairement sans nom ni montant (il est lisible par tous les utilisateurs).
    log_(me, c.id ? 'Modification' : 'Création', 'Compte d\'épargne', 'détails confidentiels');
    return comptes_(me);
  });
}

function deleteCompte(id) {
  var me = ctx_(ROLES.admin);
  return withLock_(function () {
    var reg = findBy_(SH.CPT, 'id', id);
    if (!reg) throw new Error('Compte introuvable');
    var x = readCompte_(id);
    if (x) { x.file.rename('[Archivé] Budget — Épargne · ' + x.nom); shareCompte_(x.file, []); }
    reg.actif = false;
    upsert_(SH.CPT, reg);
    uncacheComptes_();
    _comptes = null;
    log_(me, 'Archivage', 'Compte d\'épargne', 'détails confidentiels');
    return comptes_(me);
  });
}

/**
 * Rattache un mouvement ('mvt') ou un fixe ('rec') à un compte (compteId vide = aucun).
 * Retire l'ancien rattachement dans les comptes accessibles à l'utilisateur.
 */
function setImputation_(me, compteId, kind, refId) {
  _comptes = null;
  var target = null;
  compteFiles_().forEach(function (c) {
    var x = readCompte_(c.id);
    if (!x || !canSeeCompte_(x, me.email)) return;
    if (c.id === compteId) target = x;
    var is = x.file.getSheetByName('Imputations');
    var last = is.getLastRow();
    if (last < 2) return;
    var vals = is.getRange(2, 1, last - 1, 2).getValues();
    for (var i = vals.length - 1; i >= 0; i--) {
      if (String(vals[i][0]) === kind && String(vals[i][1]) === refId) is.deleteRow(i + 2);
    }
  });
  if (compteId) {
    if (!target) throw new Error('Compte d\'épargne inaccessible');
    target.file.getSheetByName('Imputations').appendRow([kind, refId, me.email, nowIso_()]);
  }
  uncacheComptes_();
  _comptes = null;
}

/** v0.5.0 : les anciens soldes d'épargne (feuille commune) deviennent des comptes privés (admin seul). */
function migrateEpargne_(adminEmail) {
  if (getConfig_('mig050_epargne')) return;
  var u = findBy_(SH.USERS, 'email', adminEmail);
  if (!u || u.role !== 'admin') return;
  var old = readTable_(SH.EPARGNE);
  old.forEach(function (e, i) {
    var f = newCompteFile_(e.compte);
    writeCompteMeta_(f, { nom: e.compte, emoji: '🐷', soldeRef: Number(e.solde) || 0,
      dateRef: e.majLe || nowIso_().substring(0, 10), visibles: [], ordre: i });
    shareCompte_(f, []);
    appendRows_(SH.CPT, [{ id: f.getId(), ordre: i, actif: true, creeLe: nowIso_() }]);
  });
  // Les soldes ne restent pas dans le fichier commun.
  var sh = sheet_(SH.EPARGNE);
  if (sh.getLastRow() > 1) sh.getRange(2, 1, sh.getLastRow() - 1, sh.getLastColumn()).clearContent();
  invalidate_(SH.EPARGNE);
  setConfig_('mig050_epargne', nowIso_());
  uncacheComptes_();
  _comptes = null;
  appendJournal_(adminEmail, 'Migration v0.5.0', 'Épargne', old.length + ' compte(s) passés en fichiers privés (visibles par l\'administrateur uniquement)');
}

/* ================================================================== */
/* Préférences & nouveautés (par utilisateur)                           */
/* ================================================================== */

function parsePrefs_(s) { try { return JSON.parse(s || '{}') || {}; } catch (e) { return {}; } }

function savePrefs(prefs) {
  var me = ctx_(ROLES.editeur);
  var json = JSON.stringify(prefs || {});
  if (json.length > 20000) throw new Error('Préférences trop volumineuses');
  return withLock_(function () {
    var u = findBy_(SH.USERS, 'email', me.email);
    u.prefs = json;
    upsert_(SH.USERS, u, 'email');
    CacheService.getScriptCache().remove('users');
    return true;
  });
}

function markSeen() {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var u = findBy_(SH.USERS, 'email', me.email);
    u.versionVue = APP_VERSION;
    upsert_(SH.USERS, u, 'email');
    CacheService.getScriptCache().remove('users');
    return APP_VERSION;
  });
}

/** Nouveautés affichées dans l'appli (rédigées pour l'utilisateur). */
var RELEASES = [
  { v: '1.2.0', titre: 'Plus clair, plus personnel', items: [
    'Écran d\'accueil « Bonjour » à l\'ouverture, aux couleurs de votre thème.',
    'Nouvelle barre : Synthèse, Mouvements, Budget (enveloppes et fixes), Épargne, Plus — et le bouton + au centre.',
    'L\'épargne a son propre onglet.',
    'Jour de paye réglable (et modifiable pour un mois donné) : le décompte des jours restants suit vos payes.',
    'Conseil du jour calé sur vos habitudes, uniquement sur les dépenses ponctuelles.',
    'À pointer : la liste reprend là où vous vous êtes arrêté.',
    'Pages allégées, tuiles mieux équilibrées, textes mieux alignés.',
    'Glisser d\'une page à l\'autre plus fiable.',
    'Couleur du thème au choix dans Mes préférences.'
  ] },
  { v: '1.1.1', titre: 'Animations et effet 3D', items: [
    'Sur le téléphone, glisser vers la gauche ou la droite change maintenant de page (Synthèse, Mouvements, Enveloppes, Fixes, Plus). Le mois se change avec les flèches ou en touchant son nom.',
    'Changements de page animés, avec un léger effet de perspective, et tuiles qui apparaissent une à une.',
    'Effet 3D : les tuiles de la Synthèse s\'inclinent vers la souris, ou vers le doigt quand on appuie, avec un reflet de lumière.',
    'Survol à la souris plus vivant sur PC : boutons, cartes, lignes et icônes réagissent.'
  ] },
  { v: '1.1.0', titre: 'Nouvelle présentation', items: [
    'Toute l\'appli change de look : tuiles colorées, nouvelle police, chiffres plus lisibles (centimes en petit).',
    'Synthèse en tuiles : reste à vivre avec sa courbe du mois et l\'avancement jour par jour, entrées, sorties, répartition et enveloppes en un coup d\'œil.',
    'Nouveau « Conseil du jour » : dépassement d\'enveloppe, ou combien il reste à dépenser par jour.',
    'Barre de navigation flottante avec le nom de l\'onglet actif, et bouton + toujours à portée de pouce.',
    'Les icônes de libellés sont colorées selon leur type (besoins, envies, crédits…) et l\'initiale de la personne s\'affiche à côté de chaque saisie.',
    'Thèmes clair (crème) et sombre retravaillés, version PC avec barre latérale repensée.'
  ] },
  { v: '1.0.0', titre: 'Version 1.0', items: [
    'Ouverture plus rapide : l\'appli n\'attend plus quand quelqu\'un d\'autre enregistre au même moment.',
    'Pointage d\'un relevé plus fluide : les cases cochées à la suite partent en un seul envoi.',
    'Graphiques et conseils plus réactifs.',
    'Hors connexion : un bandeau le signale et la saisie est mise en pause jusqu\'au retour du réseau.',
    'Sauvegardes mieux étalées : environ 3 mois d\'historique de sauvegardes de nuit.'
  ] },
  { v: '0.7.0', titre: 'Une vraie appli sur votre Pixel', items: [
    'Appli installable : icône sur l\'écran d\'accueil, plein écran, sans barre de navigateur (voir Plus › Mes préférences › Installer l\'appli).',
    'Ouverture instantanée : les dernières données s\'affichent tout de suite, puis se mettent à jour.',
    'Gestes : tirez vers le bas pour actualiser, glissez à gauche / à droite pour changer de mois, glissez une fiche vers le bas pour la fermer.',
    'Le bouton retour du téléphone ferme les fiches et les pages ouvertes.',
    'Présentation retravaillée pour le smartphone : navigation plus lisible, zones tactiles plus grandes.',
    'Commande vocale « Hey Google » retirée (reconnaissance pas assez fiable). La saisie rapide au clavier reste disponible.'
  ] },
  { v: '0.6.0', titre: 'Commande vocale « Hey Google »', items: [
    'Dites « Hey Google, ajoute une tâche : budget 45 euros Leclerc » : le mouvement est ajouté au budget automatiquement (en quelques minutes).',
    'À activer une fois par chacun : Plus › Mes préférences › Saisie vocale.',
    'Les mouvements dictés sont marqués 🎤 « à vérifier » jusqu\'à ce que vous les ouvriez et les enregistriez.',
    'Raccourci « Saisie rapide » à poser sur l\'écran d\'accueil, et bouton micro dans la fiche de saisie.'
  ] },
  { v: '0.5.0', titre: 'Épargne par compte, e-mail mensuel, thème clair', items: [
    'Épargne : chaque versement (ou retrait) se rattache à un compte ; le solde se met à jour tout seul.',
    'Confidentialité : chacun ne voit que les comptes d\'épargne que l\'administrateur lui autorise, y compris dans Google Drive.',
    'Compte rendu du mois précédent envoyé par e-mail dès que le nouveau salaire est saisi.',
    'Détail des coûts de chaque véhicule : postes, mois par mois, comparaison, prêt restant.',
    'Sections repliables pour alléger les écrans ; filtres dans un menu « Filtrer ».',
    'Thème clair ou sombre, et préférences personnelles (Plus › Mes préférences) qui vous suivent sur tous vos appareils.',
    'Cette fenêtre : les nouveautés s\'affichent après chaque mise à jour (historique dans Plus › Nouveautés).'
  ] },
  { v: '0.4.0', titre: 'Véhicules, recherche, impôts', items: [
    'Dépenses imputées au Partner ou au Pajero, coût par véhicule.',
    'Barre de recherche dans Mouvements et Fixes.',
    'Synthèse : touchez un libellé pour voir ses mouvements.',
    'Conseils : catégorie Impôts & taxes, règle 50/30/20 sur le revenu disponible.'
  ] },
  { v: '0.3.0', titre: 'Nouveau design et conseils', items: [
    'Thème noir, graphiques de répartition et d\'évolution.',
    'Conseils budgétaires (50/30/20, endettement, repères).',
    'Saisie dictée, duplication, ponctuel ↔ fixe, 0 € pour un fixe, emoji des libellés.',
    'Enregistrements beaucoup plus rapides.'
  ] },
  { v: '0.2.0', titre: 'Enveloppes', items: ['Enveloppes de budget mensuelles par libellé, reportées automatiquement.'] },
  { v: '0.1.1', titre: 'Mois de budget', items: ['Le salaire versé fin de mois compte pour le mois suivant.'] },
  { v: '0.1.0', titre: 'Première version', items: ['Mouvements fixes, utilisateurs, sauvegardes, journal.'] }
];

/* ================================================================== */
/* Compte rendu mensuel par e-mail                                      */
/* ================================================================== */

/** Déclencheur horaire : envoie le compte rendu de M-1 dès que le salaire du mois M est saisi ou pointé. */
function reportCheck() {
  var tz = Session.getScriptTimeZone();
  var hour = Number(Utilities.formatDate(new Date(), tz, 'H'));
  if (hour < 7 || hour > 22) return;
  _memo = { tables: {}, headers: {}, sheets: {} };
  var cur = currentMonth_();
  var day = Number(Utilities.formatDate(new Date(), tz, 'd'));
  var sal = readTable_(SH.LIB).filter(function (l) { return norm_(l.nom) === 'salaires'; }).map(function (l) { return l.id; });
  var hasSalary = function (m) {
    return readTable_(SH.MVT).some(function (x) {
      return x.mois === m && x.statut !== 'annule' && sal.indexOf(x.libelleId) >= 0 && (x.saisiPar !== 'auto' || x.pointe || x.modifie);
    });
  };
  [shiftMonth_(cur, 1), cur].forEach(function (m) {
    var prev = shiftMonth_(m, -1);
    if (getConfig_('rapport_' + prev)) return;
    if (hasSalary(m)) sendReport_(prev, 'salaire saisi');
    else if (m === cur && day >= 5) sendReport_(prev, 'le 5 du mois');
  });
}

function reportRecipients_() {
  return readTable_(SH.USERS).filter(function (u) {
    if (!u.actif) return false;
    var p = parsePrefs_(u.prefs);
    return p.rapport === undefined ? u.role === 'admin' : !!p.rapport;
  });
}

function sendReport_(mois, raison) {
  var sent = [];
  reportRecipients_().forEach(function (u) {
    var r = buildReport_(mois, u);
    try { MailApp.sendEmail({ to: u.email, subject: r.subject, htmlBody: r.html, name: 'Budget' }); sent.push(u.nom); } catch (e) {}
  });
  setConfig_('rapport_' + mois, nowIso_());
  appendJournal_('système', 'Compte rendu', 'E-mail', mois + ' envoyé à ' + (sent.join(', ') || 'personne') + ' (' + raison + ')');
  return sent;
}

function previewReport(mois) {
  var me = ctx_(ROLES.admin);
  checkMonth_(mois);
  return buildReport_(mois, me).html;
}

function sendReportNow(mois) {
  var me = ctx_(ROLES.admin);
  checkMonth_(mois);
  return withLock_(function () { return sendReport_(mois, 'envoi manuel par ' + me.nom); });
}

function reportStatus() {
  ctx_(ROLES.admin);
  return readTable_(SH.CONFIG).filter(function (c) { return /^rapport_\d{4}-\d{2}$/.test(c.cle); })
    .map(function (c) { return { mois: c.cle.substring(8), le: c.valeur }; })
    .sort(function (a, b) { return a.mois < b.mois ? 1 : -1; });
}

/** Calcul serveur d'un mois (mêmes règles que l'appli). */
function computeMonth_(m) {
  var libs = readTable_(SH.LIB), envs = readTable_(SH.ENV);
  var mv = readTable_(SH.MVT).filter(function (x) { return x.mois === m && x.statut !== 'annule'; });
  var per = {};
  libs.forEach(function (l) { per[l.id] = { lib: l, env: envelopeFor_(envs, l.id, m), fixes: 0, reel: 0 }; });
  mv.forEach(function (x) {
    var p = per[x.libelleId]; if (!p) return;
    p.reel += Number(x.montant) || 0;
    if (x.recurrentId) p.fixes += Number(x.prevu) || 0;
  });
  var t = { eP: 0, eR: 0, sP: 0, sR: 0, types: {}, veh: {} };
  Object.keys(per).forEach(function (k) {
    var p = per[k]; p.prevu = p.env > 0 ? p.env : p.fixes;
    if (p.lib.sens === 'entree') { t.eP += p.prevu; t.eR += p.reel; }
    else { t.sP += p.prevu; t.sR += p.reel; var ty = p.lib.type || 'envie'; t.types[ty] = (t.types[ty] || 0) + p.reel; }
  });
  mv.forEach(function (x) { if (x.vehiculeId) t.veh[x.vehiculeId] = (t.veh[x.vehiculeId] || 0) + (Number(x.montant) || 0); });
  t.per = per;
  return t;
}

/** Solde et versements du mois des comptes visibles par `user`. */
function comptesResume_(user, mois) {
  var cps = comptes_(user);
  var libs = {}; readTable_(SH.LIB).forEach(function (l) { libs[l.id] = l; });
  var recMap = {}, mvMap = {};
  cps.forEach(function (cp) { cp.imputations.forEach(function (i) { (i.kind === 'rec' ? recMap : mvMap)[i.refId] = cp.id; }); });
  var today = nowIso_().substring(0, 10);
  return cps.map(function (cp) {
    var vers = 0, solde = cp.soldeRef;
    readTable_(SH.MVT).forEach(function (x) {
      if (x.statut === 'annule') return;
      var cid = mvMap[x.id] || (x.recurrentId && recMap[x.recurrentId]) || '';
      if (cid !== cp.id) return;
      var sign = libs[x.libelleId] && libs[x.libelleId].sens === 'entree' ? -1 : 1;
      if (x.date >= cp.dateRef && x.date <= today) solde += sign * (Number(x.montant) || 0);
      if (x.mois === mois) vers += sign * (Number(x.montant) || 0);
    });
    return { nom: cp.nom, emoji: cp.emoji, vers: vers, solde: solde };
  });
}

function buildReport_(mois, user) {
  var mNames = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
  var label = mNames[Number(mois.substring(5, 7)) - 1] + ' ' + mois.substring(0, 4);
  var c = computeMonth_(mois), p = computeMonth_(shiftMonth_(mois, -1));
  var bilan = c.eR - c.sR, bilanP = c.eP - c.sP, bilanPrev = p.eR - p.sR;
  var eur = function (n) {
    var v = Math.round((Number(n) || 0) * 100) / 100, neg = v < 0;
    var parts = Math.abs(v).toFixed(2).split('.');
    return (neg ? '−' : '') + parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + ',' + parts[1] + ' €';
  };
  var col = function (n) { return n < 0 ? '#f2706b' : '#3fbf7f'; };
  var row = function (a, b, color) {
    return '<tr><td style="padding:6px 0;color:#b4bac4">' + a + '</td><td style="padding:6px 0;text-align:right;font-weight:600;color:' + (color || '#e9ebee') + '">' + b + '</td></tr>';
  };
  var table = function (rows) { return '<table style="width:100%;border-collapse:collapse;font-size:14px">' + rows + '</table>'; };
  var card = function (title, body) {
    return '<div style="background:#15181d;border:1px solid #262b33;border-radius:12px;padding:14px 16px;margin:0 0 12px">' +
      '<div style="font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:#7d8592;margin-bottom:8px">' + title + '</div>' + body + '</div>';
  };
  var esc = function (x) { return String(x == null ? '' : x).replace(/[&<>"]/g, function (ch) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[ch]; }); };
  var html = '<div style="background:#0b0d10;padding:18px 12px;font-family:Inter,Segoe UI,Roboto,Arial,sans-serif;color:#e9ebee">' +
    '<div style="max-width:560px;margin:0 auto">' +
    '<div style="font-size:13px;color:#7d8592">Compte rendu mensuel · Budget</div>' +
    '<div style="font-size:22px;font-weight:700;margin:2px 0 14px">' + label.charAt(0).toUpperCase() + label.slice(1) + '</div>';
  html += card('Reste à vivre', '<div style="font-size:30px;font-weight:700;color:' + col(bilan) + '">' + eur(bilan) + '</div>' +
    table(row('Prévu', eur(bilanP)) + row('Entrées', eur(c.eR), '#3fbf7f') + row('Sorties', eur(c.sR)) +
      row('Mois précédent', eur(bilanPrev) + ' (' + (bilan - bilanPrev >= 0 ? '+' : '') + eur(bilan - bilanPrev) + ')')));
  var envs = Object.keys(c.per).map(function (k) { return c.per[k]; })
    .filter(function (x) { return x.lib.sens === 'sortie' && x.prevu > 0; })
    .sort(function (a, b) { return (b.reel - b.prevu) - (a.reel - a.prevu); });
  var over = envs.filter(function (x) { return x.reel > x.prevu + 0.005; });
  var ok = envs.filter(function (x) { return x.reel <= x.prevu + 0.005; });
  html += card('Enveloppes', (over.length
    ? '<div style="color:#f2706b;font-size:13px;margin-bottom:4px">Dépassées</div>' +
      table(over.map(function (x) { return row(esc((x.lib.emoji || '') + ' ' + x.lib.nom), eur(x.reel) + ' / ' + eur(x.prevu), '#f2706b'); }).join(''))
    : '<div style="color:#3fbf7f;font-size:14px">Aucune enveloppe dépassée 👍</div>') +
    (ok.length ? '<div style="color:#7d8592;font-size:13px;margin:10px 0 4px">Bien tenues</div><div style="font-size:13px;color:#b4bac4">' +
      ok.map(function (x) { return esc((x.lib.emoji || '') + ' ' + x.lib.nom) + ' (reste ' + eur(x.prevu - x.reel) + ')'; }).join(' · ') + '</div>' : ''));
  var TL = { besoin: 'Besoins', envie: 'Envies', credit: 'Crédits', epargne: 'Épargne', impot: 'Impôts & taxes' };
  html += card('Répartition des sorties', table(Object.keys(TL).map(function (k) {
    var v = c.types[k] || 0;
    return row(TL[k], eur(v) + (c.eR ? ' · ' + (Math.round(v / c.eR * 1000) / 10).toString().replace('.', ',') + ' %' : ''));
  }).join('')));
  var rev = c.eR, imp = c.types.impot || 0, dispo = Math.max(rev - imp, 1);
  if (rev) {
    var pc = function (v, base) { return Math.round(v / base * 1000) / 10; };
    var b = pc((c.types.besoin || 0) + (c.types.credit || 0), dispo), e = pc(c.types.envie || 0, dispo);
    var s = pc(c.types.epargne || 0, dispo), dt = pc(c.types.credit || 0, rev);
    var flag = function (v, lim, min) { return (min ? v >= lim : v <= lim) ? '✓' : '⚠️'; };
    var fr = function (v) { return String(v).replace('.', ',') + ' %'; };
    html += card('Repères', table(row(flag(b, 50) + ' Besoins (≤ 50 %)', fr(b)) + row(flag(e, 30) + ' Envies (≤ 30 %)', fr(e)) +
      row(flag(s, 20, true) + ' Épargne (≥ 20 %)', fr(s)) + row(flag(dt, 35) + ' Endettement (≤ 35 %)', fr(dt))) +
      '<div style="font-size:11px;color:#7d8592;margin-top:6px">50/30/20 calculé sur le revenu disponible (après impôts & taxes). Repères indicatifs.</div>');
  }
  var vehs = readTable_(SH.VEH).filter(function (v) { return v.actif || c.veh[v.id]; });
  if (vehs.length) html += card('Véhicules', table(vehs.map(function (v) { return row(esc((v.emoji || '') + ' ' + v.nom), eur(c.veh[v.id] || 0)); }).join('')));
  // Épargne : uniquement les comptes visibles par ce destinataire.
  var cr = comptesResume_(user, mois);
  if (cr.length) html += card('Épargne', table(cr.map(function (x) {
    return row(esc((x.emoji || '') + ' ' + x.nom), (x.vers ? (x.vers > 0 ? '+' : '') + eur(x.vers) + ' · ' : '') + 'solde ' + eur(x.solde));
  }).join('')));
  var url = '';
  try { url = ScriptApp.getService().getUrl(); } catch (e) {}
  if (url) html += '<div style="text-align:center;margin:16px 0 4px"><a href="' + url + '" style="display:inline-block;background:#4c8dff;color:#fff;text-decoration:none;font-weight:600;padding:11px 20px;border-radius:12px">Ouvrir l\'appli Budget</a></div>';
  html += '<div style="text-align:center;font-size:11px;color:#7d8592;margin-top:10px">Envoyé automatiquement par votre appli Budget (v' + APP_VERSION + '). ' +
    'Pour ne plus le recevoir : Plus › Mes préférences.</div></div></div>';
  return { subject: 'Budget — compte rendu de ' + label + ' : reste à vivre ' + eur(bilan), html: html };
}

/* ================================================================== */
/* Sauvegardes                                                          */
/* ================================================================== */

/** Appelée par l'interface quand on quitte / ferme l'appli. */
function autoBackup() {
  var me = ctx_(ROLES.editeur);
  var p = PropertiesService.getScriptProperties();
  var lastModif = Number(p.getProperty('lastModif') || 0);
  var lastBackupTs = Number(p.getProperty('lastBackupTs') || 0);
  if (lastModif <= lastBackupTs) return { done: false, reason: 'aucune modification' };
  if (Date.now() - lastBackupTs < 10 * 60 * 1000) return { done: false, reason: 'trop récent' }; // au plus une toutes les 10 min
  return withLock_(function () { return { done: true, backup: backup_('fermeture de l\'appli', me.email) }; });
}

/** Déclencheur quotidien (installé par setup) : filet de sécurité. */
function dailyBackup() {
  var p = PropertiesService.getScriptProperties();
  if (Number(p.getProperty('lastModif') || 0) > Number(p.getProperty('lastBackupTs') || 0)) {
    backup_('quotidienne', 'système');
  }
  trimJournal_();
}

/** Le journal est plafonné (les 15 000 dernières actions sont gardées) pour que la Sheet reste rapide. */
function trimJournal_() {
  var sh = sheet_(SH.JOURNAL);
  var last = sh.getLastRow();
  if (last > 20001) sh.deleteRows(2, last - 15001);
}

function backupNow() {
  var me = ctx_(ROLES.admin);
  return backup_('manuelle', me.email);
}

function listBackups() {
  ctx_(ROLES.admin);
  var sh = backupSheet_();
  var last = sh.getLastRow();
  if (last < 2) return [];
  return sh.getRange(2, 1, last - 1, 7).getDisplayValues().reverse().map(function (r) {
    return { id: r[0], horodatage: r[1], utilisateur: r[2], raison: r[3], version: r[4], taille: r[5], resume: r[6] };
  });
}

function restoreBackup(id) {
  var me = ctx_(ROLES.admin);
  return withLock_(function () {
    var sh = backupSheet_();
    var row = findRow_(sh, 1, id);
    if (row < 0) throw new Error('Sauvegarde introuvable');
    var values = sh.getRange(row, 1, 1, sh.getLastColumn()).getValues()[0];
    var b64 = values.slice(7).join('');
    var json = Utilities.ungzip(Utilities.newBlob(Utilities.base64Decode(b64), 'application/x-gzip')).getDataAsString();
    var snap = JSON.parse(json);

    backup_('avant restauration', me.email);

    var ss = SpreadsheetApp.getActive();
    BACKUP_SHEETS.forEach(function (name) {
      var data = snap.sheets[name];
      if (!data) return;
      var s = ss.getSheetByName(name);
      if (s.getLastRow() > 1) s.getRange(2, 1, s.getLastRow() - 1, s.getLastColumn()).clearContent();
      if (data.length) {
        var width = HEADERS[name].length;
        var rows = data.map(function (r) {
          var out = [];
          for (var i = 0; i < width; i++) out.push(r[i] === undefined || r[i] === null ? '' : r[i]);
          return out;
        });
        s.getRange(2, 1, rows.length, width).setValues(rows);
      }
    });
    _memo = { tables: {}, headers: {}, sheets: {} };
    CacheService.getScriptCache().remove('users');
    log_(me, 'Restauration', 'Sauvegarde', values[1] + ' (' + values[3] + ')');
    return data_(me);
  });
}

function backup_(raison, who) {
  var ss = SpreadsheetApp.getActive();
  var snap = { version: APP_VERSION, date: nowIso_(), sheets: {} };
  BACKUP_SHEETS.forEach(function (name) {
    var s = ss.getSheetByName(name);
    var last = s.getLastRow();
    snap.sheets[name] = last < 2 ? [] : s.getRange(2, 1, last - 1, HEADERS[name].length).getValues();
  });
  var json = JSON.stringify(snap);
  var b64 = Utilities.base64Encode(Utilities.gzip(Utilities.newBlob(json, 'application/json')).getBytes());
  var chunks = [];
  for (var i = 0; i < b64.length; i += BACKUP_CHUNK) chunks.push(b64.substring(i, i + BACKUP_CHUNK));

  var sh = backupSheet_();
  var width = 7 + chunks.length;
  if (sh.getMaxColumns() < width) sh.insertColumnsAfter(sh.getMaxColumns(), width - sh.getMaxColumns());
  var id = newId_();
  var horodatage = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm:ss');
  var resume = snap.sheets[SH.MVT].length + ' mouvements, ' + snap.sheets[SH.REC].length + ' fixes';
  sh.appendRow([id, horodatage, who || '', raison, APP_VERSION, Math.round(json.length / 1024) + ' Ko', resume].concat(chunks));

  pruneBackups_(sh);

  var p = PropertiesService.getScriptProperties();
  p.setProperty('lastBackupTs', String(Date.now()));
  p.setProperty('lastBackup', horodatage);
  appendJournal_(who || 'système', 'Sauvegarde', 'Données', raison + ' — ' + resume);
  return { id: id, horodatage: horodatage };
}

/**
 * Conservation des sauvegardes : les 90 dernières sauvegardes de nuit (≈ 3 mois d'activité)
 * et les 40 dernières autres (fermeture de l'appli, manuelles, avant restauration).
 */
function pruneBackups_(sh) {
  var last = sh.getLastRow();
  if (last < 2) return;
  var raisons = sh.getRange(2, 4, last - 1, 1).getValues().map(function (r) { return String(r[0]); });
  var keepDaily = 90, keepOther = 40, del = [];
  for (var i = raisons.length - 1; i >= 0; i--) {
    if (raisons[i] === 'quotidienne') { if (keepDaily-- <= 0) del.push(i + 2); }
    else if (keepOther-- <= 0) del.push(i + 2);
  }
  del.forEach(function (row) { sh.deleteRow(row); }); // du bas vers le haut
}

function ensureBackupFile_() {
  var id = getConfig_('backupSpreadsheetId');
  if (id) {
    try { SpreadsheetApp.openById(id); return id; } catch (e) { /* recréé ci-dessous */ }
  }
  var file = SpreadsheetApp.create(SpreadsheetApp.getActive().getName() + ' — Sauvegardes');
  var sh = file.getSheets()[0].setName('Sauvegardes');
  sh.getRange(1, 1, 1, 8).setValues([['id', 'horodatage', 'utilisateur', 'raison', 'version', 'taille', 'resume', 'donnees (gzip+base64)']])
    .setFontWeight('bold');
  sh.setFrozenRows(1);
  setConfig_('backupSpreadsheetId', file.getId());
  readTable_(SH.USERS).forEach(function (u) {
    if (u.actif && u.email !== Session.getEffectiveUser().getEmail()) {
      try { u.role === 'lecteur' ? file.addViewer(u.email) : file.addEditor(u.email); } catch (e) { /* ignoré */ }
    }
  });
  return file.getId();
}

function backupSheet_() {
  var id = getConfig_('backupSpreadsheetId');
  if (!id) throw new Error('Fichier de sauvegarde absent : lancez « setup » depuis l\'éditeur');
  return SpreadsheetApp.openById(id).getSheetByName('Sauvegardes');
}

/* ================================================================== */
/* Logique métier interne                                               */
/* ================================================================== */

function ensureMonth_(mois, me) {
  var recs = readTable_(SH.REC).filter(function (r) { return inRange_(r, mois); });
  if (!recs.length) return [];
  var existing = {};
  readTable_(SH.MVT).forEach(function (m) {
    if (m.mois === mois && m.recurrentId) existing[m.recurrentId] = true;
  });
  var rows = recs.filter(function (r) { return !existing[r.id]; }).map(function (r) {
    return {
      id: newId_(), mois: mois, date: dateFor_(shiftMonth_(mois, Number(r.decalage) || 0), r.jour), libelleId: r.libelleId,
      description: r.description, montant: r.montant, prevu: r.montant, recurrentId: r.id, vehiculeId: r.vehiculeId || '',
      pointe: false, modifie: false, statut: '', saisiPar: 'auto', saisiLe: nowIso_()
    };
  });
  if (rows.length) {
    appendRows_(SH.MVT, rows);
    log_(me, 'Report automatique', 'Mouvements fixes', rows.length + ' mouvement(s) reporté(s) sur ' + mois);
  }
  return rows;
}

/** Met à jour les occurrences non pointées et non modifiées à partir de `fromMonth`. */
function propagate_(rec, fromMonth) {
  var sh = sheet_(SH.MVT);
  var all = readTableRaw_(SH.MVT); // lecture fraîche : les objets sont modifiés ci-dessous
  var count = 0;
  var toDelete = [];
  all.forEach(function (m, i) {
    if (m.recurrentId !== rec.id || m.mois < fromMonth || m.statut === 'annule' || m.pointe || m.modifie) return;
    if (!inRange_(rec, m.mois)) {
      toDelete.push(i + 2);
    } else {
      m.montant = rec.montant;
      m.prevu = rec.montant;
      m.libelleId = rec.libelleId;
      m.description = rec.description;
      m.vehiculeId = rec.vehiculeId || '';
      m.date = dateFor_(shiftMonth_(m.mois, Number(rec.decalage) || 0), rec.jour);
      sh.getRange(i + 2, 1, 1, HEADERS.Mouvements.length).setValues([toRow_(SH.MVT, m)]);
    }
    count++;
  });
  toDelete.reverse().forEach(function (row) { sh.deleteRow(row); });
  invalidate_(SH.MVT);
  return count;
}

function inRange_(r, mois) {
  return r.actif && r.debut <= mois && (!r.fin || r.fin >= mois);
}

function dateFor_(mois, jour) {
  var y = Number(mois.substring(0, 4));
  var m = Number(mois.substring(5, 7));
  var days = new Date(y, m, 0).getDate();
  var d = Math.min(Math.max(1, Number(jour) || 1), days);
  return mois + '-' + (d < 10 ? '0' : '') + d;
}

/* ================================================================== */
/* Accès / journal                                                      */
/* ================================================================== */

/** Renvoie l'utilisateur courant ; lève une erreur s'il n'a pas le rôle requis. */
function ctx_(minRole) {
  var email = (Session.getActiveUser().getEmail() || '').toLowerCase();
  // Les utilisateurs sont gardés 10 min en cache (vidé à chaque modification d'un utilisateur).
  var cache = CacheService.getScriptCache();
  var cached = cache.get('users');
  var users = cached ? JSON.parse(cached) : readTable_(SH.USERS);
  if (!cached && users.length) cache.put('users', JSON.stringify(users), 600);
  if (!users.length && email) {
    // Premier lancement : le premier utilisateur devient administrateur.
    var first = { email: email, nom: email.split('@')[0], role: 'admin', couleur: COLORS[0], actif: true, ajouteLe: nowIso_() };
    upsert_(SH.USERS, first, 'email');
    appendJournal_(email, 'Installation', 'Utilisateur', 'Premier administrateur : ' + email);
    return first;
  }
  var u = users.filter(function (x) { return x.email.toLowerCase() === email; })[0];
  if (!u || !u.actif) {
    throw new Error('Accès refusé pour ' + (email || 'cet utilisateur') + '. Demandez à l\'administrateur de vous ajouter.');
  }
  if (ROLES[u.role] < minRole) {
    throw new Error('Action réservée ' + (minRole === ROLES.admin ? 'aux administrateurs' : 'aux éditeurs') + '.');
  }
  return u;
}

function log_(me, action, objet, detail) {
  appendJournal_(me && me.email ? me.email : 'système', action, objet, detail);
  PropertiesService.getScriptProperties().setProperty('lastModif', String(Date.now()));
}

function appendJournal_(who, action, objet, detail) {
  var ts = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm:ss');
  sheet_(SH.JOURNAL).appendRow([ts, who, action, objet, detail || '']);
}

function describeMvt_(m, lib) {
  return (lib ? lib.nom : '?') + ' — ' + (m.description || '') + ' — ' + fmtEur_(m.montant) + ' — ' + m.date;
}

function describeRec_(r, lib) {
  return (lib ? lib.nom : '?') + ' — ' + (r.description || '') + ' — ' + fmtEur_(r.montant) +
    '/mois, le ' + r.jour + (Number(r.decalage) < 0 ? ' du mois précédent' : '') + ', de ' + r.debut + ' à ' + (r.fin || 'perpétuel') + (r.actif ? '' : ' (inactif)');
}

/* ================================================================== */
/* Accès aux feuilles                                                   */
/* ================================================================== */

function ensureSetup_() {
  var cache = CacheService.getScriptCache();
  if (cache.get('setup_' + APP_VERSION)) return;
  var ss = SpreadsheetApp.getActive();
  var added = {};
  Object.keys(HEADERS).forEach(function (name) {
    var headers = HEADERS[name];
    var sh = ss.getSheetByName(name);
    if (!sh) {
      sh = ss.insertSheet(name);
      added[name] = ['*'];
      sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
      sh.setFrozenRows(1);
      setTextFormats_(sh, headers);
      if (name === SH.VEH) {
        appendRows_(SH.VEH, DEFAULT_VEHICULES.map(function (v, i) {
          return { id: newId_(), nom: v[0], emoji: v[1], actif: true, ordre: i };
        }));
      }
      if (name === SH.LIB) {
        appendRows_(SH.LIB, DEFAULT_LIBELLES.map(function (d, i) {
          return { id: newId_(), nom: d[0], sens: d[1], groupe: d[2], budget: 0, ordre: i, actif: true, moisSuivant: !!d[3], emoji: d[4], type: d[5],
            parVehicule: PAR_VEHICULE.indexOf(norm_(d[0])) >= 0, parCompte: PAR_COMPTE.indexOf(norm_(d[0])) >= 0 || d[5] === 'epargne' };
        }));
      }
    } else {
      // Migration : ajoute les colonnes manquantes à droite.
      var current = sh.getRange(1, 1, 1, Math.max(1, sh.getLastColumn())).getValues()[0];
      var missing = headers.filter(function (h) { return current.indexOf(h) < 0; });
      if (missing.length) {
        sh.getRange(1, current.filter(String).length + 1, 1, missing.length).setValues([missing]).setFontWeight('bold');
        delete _memo.headers[name];
        setTextFormats_(sh, sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0]);
        added[name] = missing;
      }
    }
  });
  if (added[SH.LIB] && added[SH.LIB].indexOf('moisSuivant') >= 0) migrateMoisSuivant_();
  if (added[SH.ENV] && !(added[SH.LIB] && added[SH.LIB][0] === '*')) migrateBudgets_();
  if (added[SH.LIB] && added[SH.LIB].indexOf('type') >= 0) migrateTypes_();
  var def = ss.getSheetByName('Feuille 1') || ss.getSheetByName('Sheet1');
  if (def && def.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(def);
  if (getConfig_('version') !== APP_VERSION) {
    var prev = getConfig_('version');
    if (prev && verLt_(prev, '0.4.0')) migrate040_();
    if (prev && verLt_(prev, '0.5.0')) migrate050Libs_();
    setConfig_('version', APP_VERSION);
    appendJournal_('système', prev ? 'Mise à jour' : 'Installation', 'Application',
      (prev ? 'v' + prev + ' → ' : '') + 'v' + APP_VERSION + ' (' + APP_STAGE + ')');
  }
  cache.put('setup_' + APP_VERSION, '1', 21600);
  _memo = { tables: {}, headers: {}, sheets: {} };
}

function verLt_(a, b) {
  var x = String(a).split('.').map(Number), y = String(b).split('.').map(Number);
  for (var i = 0; i < 3; i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) < (y[i] || 0); }
  return false;
}

/**
 * v0.4.0 :
 *  - libellé « Véhicules » ajouté ; Carburant / Prêt auto / Véhicules imputables à un véhicule ;
 *  - fixes et mouvements dont la description cite un véhicule (« Prêt Pajero ») rattachés à celui-ci ;
 *  - le libellé « Impôts » passe dans le type « Impôts & taxes ».
 */
function migrate040_() {
  _memo = { tables: {}, headers: {}, sheets: {} };
  var libs = readTable_(SH.LIB);
  if (!libs.some(function (l) { return norm_(l.nom) === 'vehicules'; })) {
    appendRows_(SH.LIB, [{ id: newId_(), nom: 'Véhicules', sens: 'sortie', groupe: GROUPES.fixes, budget: 0,
      ordre: libs.length, actif: true, moisSuivant: false, emoji: '🔧', type: 'besoin', parVehicule: true }]);
  }
  readTable_(SH.LIB).forEach(function (l) {
    var changed = false;
    if (PAR_VEHICULE.indexOf(norm_(l.nom)) >= 0 && !l.parVehicule) { l.parVehicule = true; changed = true; }
    if (/^(impots?|taxe fonciere)$/.test(norm_(l.nom)) && l.type !== 'impot') { l.type = 'impot'; changed = true; }
    if (changed) upsert_(SH.LIB, l);
  });
  var vehs = readTable_(SH.VEH);
  var libVeh = {};
  readTable_(SH.LIB).forEach(function (l) { if (l.parVehicule) libVeh[l.id] = true; });
  var guess = function (txt) {
    var t = norm_(txt);
    var v = vehs.filter(function (x) { return t.indexOf(norm_(x.nom)) >= 0; });
    return v.length === 1 ? v[0].id : '';
  };
  var nR = 0, nM = 0;
  readTable_(SH.REC).forEach(function (r) {
    if (r.vehiculeId || !libVeh[r.libelleId]) return;
    var v = guess(r.description);
    if (v) { r.vehiculeId = v; upsert_(SH.REC, r); nR++; }
  });
  var recVeh = {};
  readTable_(SH.REC).forEach(function (r) { if (r.vehiculeId) recVeh[r.id] = r.vehiculeId; });
  readTable_(SH.MVT).forEach(function (m) {
    if (m.vehiculeId || !libVeh[m.libelleId]) return;
    var v = recVeh[m.recurrentId] || guess(m.description);
    if (v) { m.vehiculeId = v; upsert_(SH.MVT, m); nM++; }
  });
  appendJournal_('système', 'Migration v0.4.0', 'Véhicules & impôts',
    'Libellé Véhicules ajouté ; ' + nR + ' fixe(s) et ' + nM + ' mouvement(s) rattachés à un véhicule ; Impôts → type Impôts & taxes');
}

/** v0.5.0 : libellés « à imputer à un compte d'épargne » + libellé « Retrait épargne ». */
function migrate050Libs_() {
  _memo = { tables: {}, headers: {}, sheets: {} };
  var libs = readTable_(SH.LIB);
  if (!libs.some(function (l) { return norm_(l.nom) === 'retrait epargne'; })) {
    appendRows_(SH.LIB, [{ id: newId_(), nom: 'Retrait épargne', sens: 'entree', groupe: GROUPES.entree, budget: 0,
      ordre: libs.length, actif: true, moisSuivant: false, emoji: '🏦', type: 'revenu', parVehicule: false, parCompte: true }]);
  }
  readTable_(SH.LIB).forEach(function (l) {
    if (!l.parCompte && (l.type === 'epargne' || PAR_COMPTE.indexOf(norm_(l.nom)) >= 0)) { l.parCompte = true; upsert_(SH.LIB, l); }
  });
  appendJournal_('système', 'Migration v0.5.0', 'Libellés', 'Libellés d\'épargne imputables à un compte ; libellé « Retrait épargne » ajouté');
}

/** v0.3.0 : emoji et type (besoin / envie / épargne / crédit) proposés pour les libellés existants. */
function migrateTypes_() {
  _memo = { tables: {}, headers: {}, sheets: {} };
  readTable_(SH.LIB).forEach(function (l) {
    var changed = false;
    if (!l.type) { l.type = defaultType_(l.nom, l.sens, l.groupe); changed = true; }
    if (!l.emoji && defaultEmoji_(l.nom)) { l.emoji = defaultEmoji_(l.nom); changed = true; }
    if (changed) upsert_(SH.LIB, l);
  });
  appendJournal_('système', 'Migration v0.3.0', 'Libellés', 'Emoji et types budgétaires proposés');
}

/** v0.2.0 : l'ancien « budget » unique des libellés devient une enveloppe par défaut. */
function migrateBudgets_() {
  var rows = readTable_(SH.LIB).filter(function (l) { return Number(l.budget) > 0; }).map(function (l) {
    return { id: newId_(), libelleId: l.id, debut: '2000-01', montant: Number(l.budget), portee: 'suite',
      modifPar: 'système', modifLe: nowIso_() };
  });
  appendRows_(SH.ENV, rows);
  if (rows.length) appendJournal_('système', 'Migration v0.2.0', 'Enveloppes', rows.length + ' budget(s) de libellé convertis en enveloppes');
}

/**
 * v0.1.1 : les salaires (versés en fin de mois) comptent pour le mois suivant.
 * Les mouvements fixes « Salaires » sont décalés et les occurrences non pointées
 * à partir du mois en cours sont redatées au mois précédent.
 */
function migrateMoisSuivant_() {
  var libs = readTable_(SH.LIB).filter(function (l) { return norm_(l.nom) === 'salaires'; });
  libs.forEach(function (l) { l.moisSuivant = true; upsert_(SH.LIB, l); });
  var ids = libs.map(function (l) { return l.id; });
  var n = 0;
  readTable_(SH.REC).forEach(function (r) {
    if (ids.indexOf(r.libelleId) < 0) return;
    r.decalage = -1;
    upsert_(SH.REC, r);
    propagate_(r, currentMonth_());
    n++;
  });
  if (libs.length) {
    appendJournal_('système', 'Migration v0.1.1', 'Libellé',
      'Salaires : comptés pour le mois suivant ; ' + n + ' mouvement(s) fixe(s) décalé(s)');
  }
}

/** Colonnes texte au format brut (évite que Sheets transforme « 2026-11 » en date). */
function setTextFormats_(sh, headers) {
  headers.forEach(function (h, i) {
    if (h && NUM_COLS.indexOf(h) < 0 && BOOL_COLS.indexOf(h) < 0) {
      sh.getRange(2, i + 1, Math.max(1, sh.getMaxRows() - 1), 1).setNumberFormat('@');
    }
  });
}

/*
 * Mémorisation pendant une exécution : chaque feuille n'est lue qu'une fois par action,
 * les en-têtes ne sont relus que si la structure change. Toute écriture invalide la feuille.
 */
var _memo = { tables: {}, headers: {}, sheets: {} };

function sheet_(name) {
  if (!_memo.ss) _memo.ss = SpreadsheetApp.getActive();
  return _memo.sheets[name] || (_memo.sheets[name] = _memo.ss.getSheetByName(name));
}

function header_(name) {
  if (!_memo.headers[name]) {
    var sh = sheet_(name);
    _memo.headers[name] = sh.getRange(1, 1, 1, Math.max(1, sh.getLastColumn())).getValues()[0];
  }
  return _memo.headers[name];
}

function invalidate_(name) {
  delete _memo.tables[name];
  if (_memo.rows) delete _memo.rows[name];
}

/** N° de ligne d'une clé : depuis la lecture mémorisée si possible, sinon recherche dans la feuille. */
function rowOf_(name, key, value) {
  if (_memo.tables[name] && _memo.rows && _memo.rows[name] && key === (KEY_OF[name] || 'id')) {
    var r = _memo.rows[name][String(value).toLowerCase()];
    return r || -1;
  }
  return findRow_(sheet_(name), keyCol_(name, key), value);
}

/** Lit une feuille en tenant compte de l'ordre réel des colonnes (migrations). */
function readTable_(name) {
  if (_memo.tables[name]) return _memo.tables[name];
  _memo.tables[name] = readTableRaw_(name);
  return _memo.tables[name];
}

var KEY_OF = { Utilisateurs: 'email', Config: 'cle' };

/** Lit en-tête + données en un seul accès ; mémorise le n° de ligne de chaque clé. */
function readTableRaw_(name) {
  var sh = sheet_(name);
  var last = sh.getLastRow();
  var width = sh.getLastColumn();
  if (last < 1 || width < 1) return [];
  var all = sh.getRange(1, 1, last, width).getValues();
  var header = all[0];
  _memo.headers[name] = header;
  var rows = {};
  _memo.rows = _memo.rows || {};
  _memo.rows[name] = rows;
  if (last < 2) return [];
  var keyIdx = header.indexOf(KEY_OF[name] || 'id');
  var tz = Session.getScriptTimeZone();
  var idx = {};
  HEADERS[name].forEach(function (h) { idx[h] = header.indexOf(h); });
  return all.slice(1)
    .map(function (row, i) {
      if (keyIdx >= 0 && row[keyIdx] !== '') rows[String(row[keyIdx]).toLowerCase()] = i + 2;
      return row;
    })
    .filter(function (row) { return row[0] !== ''; })
    .map(function (row) {
      var o = {};
      HEADERS[name].forEach(function (h) {
        var v = idx[h] >= 0 ? row[idx[h]] : '';
        if (v instanceof Date) {
          v = Utilities.formatDate(v, tz, (h === 'mois' || h === 'debut' || h === 'fin') ? 'yyyy-MM' : 'yyyy-MM-dd');
        }
        if (BOOL_COLS.indexOf(h) >= 0) v = v === true || v === 'TRUE' || v === 'VRAI';
        else if (NUM_COLS.indexOf(h) >= 0) v = v === '' ? '' : Number(v);
        else v = String(v);
        o[h] = v;
      });
      return o;
    });
}

function toRow_(name, rec) {
  return header_(name).map(function (h) {
    var v = rec[h];
    return v === undefined || v === null ? '' : v;
  });
}

function appendRows_(name, recs) {
  if (!recs.length) return;
  var sh = sheet_(name);
  var rows = recs.map(function (r) { return toRow_(name, r); });
  sh.getRange(sh.getLastRow() + 1, 1, rows.length, rows[0].length).setValues(rows);
  invalidate_(name);
}

function findRow_(sh, col, value) {
  var last = sh.getLastRow();
  if (last < 2) return -1;
  var vals = sh.getRange(2, col, last - 1, 1).getValues();
  for (var i = 0; i < vals.length; i++) {
    if (String(vals[i][0]).toLowerCase() === String(value).toLowerCase()) return i + 2;
  }
  return -1;
}

function keyCol_(name, key) {
  return header_(name).indexOf(key) + 1;
}

function findBy_(name, key, value) {
  return readTable_(name).filter(function (r) {
    return String(r[key]).toLowerCase() === String(value).toLowerCase();
  })[0] || null;
}

function upsert_(name, rec, key) {
  key = key || 'id';
  var sh = sheet_(name);
  var row = rowOf_(name, key, rec[key]);
  var values = toRow_(name, rec);
  if (row < 0) {
    sh.getRange(sh.getLastRow() + 1, 1, 1, values.length).setValues([values]);
  } else {
    sh.getRange(row, 1, 1, values.length).setValues([values]);
  }
  invalidate_(name);
}

function deleteBy_(name, key, value) {
  var sh = sheet_(name);
  var row = rowOf_(name, key, value);
  if (row > 0) sh.deleteRow(row);
  invalidate_(name);
}

function getConfig_(key) {
  var r = readTable_(SH.CONFIG).filter(function (x) { return x.cle === key; })[0];
  return r ? r.valeur : '';
}

function setConfig_(key, value) {
  upsert_(SH.CONFIG, { cle: key, valeur: String(value) }, 'cle');
}

/* ================================================================== */
/* Utilitaires                                                          */
/* ================================================================== */

function withLock_(fn) {
  var lock = LockService.getScriptLock();
  lock.waitLock(25000);
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

function newId_() {
  return Utilities.getUuid().replace(/-/g, '').substring(0, 12);
}

function nowIso_() {
  return Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd'T'HH:mm:ss");
}

function currentMonth_() {
  return Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM');
}

function shiftMonth_(mois, k) {
  var d = new Date(Number(mois.substring(0, 4)), Number(mois.substring(5, 7)) - 1 + k, 1);
  return d.getFullYear() + '-' + (d.getMonth() < 9 ? '0' : '') + (d.getMonth() + 1);
}

function num_(v) {
  if (typeof v === 'number') return Math.round(v * 100) / 100;
  var s = String(v === undefined || v === null ? '' : v).replace(/[\s  €]/g, '').replace(',', '.');
  var n = parseFloat(s);
  return isNaN(n) ? 0 : Math.round(n * 100) / 100;
}

function fmtEur_(n) {
  return (Number(n) || 0).toFixed(2).replace('.', ',') + ' €';
}

function clone_(o) {
  return JSON.parse(JSON.stringify(o));
}

function norm_(x) {
  return String(x || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
}

function checkMonth_(mois) {
  if (!/^\d{4}-\d{2}$/.test(String(mois))) throw new Error('Mois invalide : ' + mois);
}
