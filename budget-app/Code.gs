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

var APP_VERSION = '0.1.0';
var APP_STAGE = 'test';

var SH = {
  LIB: 'Libelles',
  REC: 'Recurrents',
  MVT: 'Mouvements',
  USERS: 'Utilisateurs',
  JOURNAL: 'Journal',
  ATTENTE: 'EnAttente',
  EPARGNE: 'Epargne',
  CONFIG: 'Config'
};

var HEADERS = {
  Libelles: ['id', 'nom', 'sens', 'groupe', 'budget', 'ordre', 'actif'],
  Recurrents: ['id', 'libelleId', 'description', 'montant', 'jour', 'debut', 'fin', 'actif',
    'creePar', 'creeLe', 'modifPar', 'modifLe'],
  Mouvements: ['id', 'mois', 'date', 'libelleId', 'description', 'montant', 'prevu', 'recurrentId',
    'pointe', 'modifie', 'statut', 'saisiPar', 'saisiLe', 'modifPar', 'modifLe'],
  Utilisateurs: ['email', 'nom', 'role', 'couleur', 'actif', 'ajouteLe'],
  Journal: ['horodatage', 'utilisateur', 'action', 'objet', 'detail'],
  EnAttente: ['id', 'description', 'montant', 'creeLe', 'saisiPar'],
  Epargne: ['id', 'compte', 'solde', 'majLe', 'saisiPar'],
  Config: ['cle', 'valeur']
};

var NUM_COLS = ['budget', 'ordre', 'montant', 'prevu', 'jour', 'solde'];
var BOOL_COLS = ['actif', 'pointe', 'modifie'];

// Feuilles incluses dans les sauvegardes.
var BACKUP_SHEETS = [SH.LIB, SH.REC, SH.MVT, SH.USERS, SH.ATTENTE, SH.EPARGNE, SH.CONFIG];
var BACKUP_KEEP = 100;
var BACKUP_CHUNK = 45000;

var ROLES = { lecteur: 0, editeur: 1, admin: 2 };
var COLORS = ['#1f6f5c', '#c2185b', '#1565c0', '#ef6c00', '#6a1b9a', '#2e7d32', '#00838f', '#5d4037'];

var GROUPES = {
  entree: 'Entrées',
  fixes: 'Charges fixes & crédits',
  courant: 'Vie courante'
};

var DEFAULT_LIBELLES = [
  ['Salaires', 'entree', GROUPES.entree],
  ['Entrées exceptionnelles', 'entree', GROUPES.entree],
  ['Remboursements médicaux', 'entree', GROUPES.entree],
  ['Remboursements divers', 'entree', GROUPES.entree],
  ['Carburant', 'sortie', GROUPES.fixes],
  ['Prêt auto', 'sortie', GROUPES.fixes],
  ['Prêt maison', 'sortie', GROUPES.fixes],
  ['Crédit conso', 'sortie', GROUPES.fixes],
  ['Alimentaire', 'sortie', GROUPES.courant],
  ['Divers', 'sortie', GROUPES.courant],
  ['Resto', 'sortie', GROUPES.courant],
  ['Loisirs', 'sortie', GROUPES.courant],
  ['Voyage / Vacances', 'sortie', GROUPES.courant],
  ['Electricité', 'sortie', GROUPES.courant],
  ['Eau', 'sortie', GROUPES.courant],
  ['Impôts', 'sortie', GROUPES.courant],
  ['Abonnements', 'sortie', GROUPES.courant],
  ['Epargne', 'sortie', GROUPES.courant],
  ['Scolarité', 'sortie', GROUPES.courant]
];

/* ================================================================== */
/* Web app, menu, installation                                         */
/* ================================================================== */

function doGet() {
  ensureSetup_();
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Budget v' + APP_VERSION)
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover')
    .setFaviconUrl('https://ssl.gstatic.com/docs/spreadsheets/favicon3.ico');
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
  ensureBackupFile_();
  var hasTrigger = ScriptApp.getProjectTriggers().some(function (t) {
    return t.getHandlerFunction() === 'dailyBackup';
  });
  if (!hasTrigger) ScriptApp.newTrigger('dailyBackup').timeBased().everyDays(1).atHour(3).create();
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
  if (ROLES[me.role] >= ROLES.editeur) {
    withLock_(function () { ensureMonth_(currentMonth_(), me); });
  }
  return data_(me);
}

/** Toutes les données (sans verrou : utilisable depuis une fonction déjà verrouillée). */
function data_(me) {
  var props = PropertiesService.getScriptProperties();
  return {
    version: APP_VERSION,
    stage: APP_STAGE,
    me: me,
    users: readTable_(SH.USERS),
    libelles: readTable_(SH.LIB),
    recurrents: readTable_(SH.REC),
    mouvements: readTable_(SH.MVT),
    attente: readTable_(SH.ATTENTE),
    epargne: readTable_(SH.EPARGNE),
    sheetUrl: SpreadsheetApp.getActive().getUrl(),
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
  if (!/^\d{4}-\d{2}-\d{2}$/.test(m.date)) throw new Error('Date invalide');
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var lib = findBy_(SH.LIB, 'id', m.libelleId);
    if (!lib) throw new Error('Libellé inconnu');
    var old = m.id ? findBy_(SH.MVT, 'id', m.id) : null;
    var rec = old ? clone_(old) : {
      id: newId_(), recurrentId: '', prevu: '', pointe: false, modifie: false, statut: '',
      saisiPar: me.email, saisiLe: nowIso_()
    };
    rec.mois = m.date.substring(0, 7);
    rec.date = m.date;
    rec.libelleId = m.libelleId;
    rec.description = String(m.description || '').trim();
    rec.montant = num_(m.montant);
    if (m.pointe !== undefined) rec.pointe = !!m.pointe;

    if (old) {
      if (old.recurrentId && (old.montant !== rec.montant || old.date !== rec.date ||
          old.description !== rec.description || old.libelleId !== rec.libelleId)) {
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
        actif: true, creePar: me.email, creeLe: nowIso_(), modifPar: '', modifLe: ''
      };
      if (created.fin && created.fin < created.debut) throw new Error('L\'échéance de fin est avant le début');
      upsert_(SH.REC, created);
      rec.recurrentId = created.id;
      rec.prevu = rec.montant;
      log_(me, 'Création', 'Mouvement fixe', describeRec_(created, lib));
    }

    upsert_(SH.MVT, rec);
    log_(me, old ? 'Modification' : 'Ajout', 'Mouvement',
      describeMvt_(rec, lib) + (old ? ' (avant : ' + fmtEur_(old.montant) + ', ' + old.date + ')' : ''));
    return { mouvement: rec, recurrent: created };
  });
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
    rec.debut = r.debut;
    rec.fin = r.fin || '';
    rec.actif = r.actif !== false;
    if (old) { rec.modifPar = me.email; rec.modifLe = nowIso_(); }
    upsert_(SH.REC, rec);
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
          budget: 0, ordre: libs.length, actif: true
        };
        upsert_(SH.LIB, lib);
        libs.push(lib);
        log_(me, 'Création', 'Libellé', lib.nom + ' (import)');
      }
      var fin = p[4] && /^\d{4}-\d{2}$/.test(p[4]) ? p[4] : '';
      var rec = {
        id: newId_(), libelleId: lib.id, description: p[2] || lib.nom, montant: num_(p[3]),
        jour: Number(p[5]) || 1, debut: mois, fin: fin, actif: true, creePar: me.email, creeLe: nowIso_()
      };
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
    rec.budget = num_(l.budget);
    if (l.actif !== undefined) rec.actif = !!l.actif;
    upsert_(SH.LIB, rec);
    log_(me, old ? 'Modification' : 'Création', 'Libellé',
      rec.nom + ' (' + rec.sens + ', ' + rec.groupe + ', budget ' + fmtEur_(rec.budget) + ')' +
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
      ajouteLe: old ? old.ajouteLe : nowIso_()
    };
    upsert_(SH.USERS, rec, 'email');
    var partage = shareWith_(rec);
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

function saveEpargne(item) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var old = item.id ? findBy_(SH.EPARGNE, 'id', item.id) : null;
    var rec = {
      id: item.id || newId_(),
      compte: String(item.compte || '').trim(),
      solde: num_(item.solde),
      majLe: nowIso_().substring(0, 10),
      saisiPar: me.email
    };
    upsert_(SH.EPARGNE, rec);
    log_(me, old ? 'Modification' : 'Ajout', 'Épargne',
      rec.compte + ' ' + fmtEur_(rec.solde) + (old ? ' (avant : ' + fmtEur_(old.solde) + ')' : ''));
    return rec;
  });
}

function deleteEpargne(id) {
  var me = ctx_(ROLES.editeur);
  return withLock_(function () {
    var old = findBy_(SH.EPARGNE, 'id', id);
    deleteBy_(SH.EPARGNE, 'id', id);
    if (old) log_(me, 'Suppression', 'Épargne', old.compte);
    return true;
  });
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
  if (Date.now() - lastBackupTs < 60 * 1000) return { done: false, reason: 'trop récent' };
  return withLock_(function () { return { done: true, backup: backup_('fermeture de l\'appli', me.email) }; });
}

/** Déclencheur quotidien (installé par setup) : filet de sécurité. */
function dailyBackup() {
  var p = PropertiesService.getScriptProperties();
  if (Number(p.getProperty('lastModif') || 0) > Number(p.getProperty('lastBackupTs') || 0)) {
    backup_('quotidienne', 'système');
  }
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

  var extra = sh.getLastRow() - 1 - BACKUP_KEEP;
  if (extra > 0) sh.deleteRows(2, extra);

  var p = PropertiesService.getScriptProperties();
  p.setProperty('lastBackupTs', String(Date.now()));
  p.setProperty('lastBackup', horodatage);
  appendJournal_(who || 'système', 'Sauvegarde', 'Données', raison + ' — ' + resume);
  return { id: id, horodatage: horodatage };
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
      id: newId_(), mois: mois, date: dateFor_(mois, r.jour), libelleId: r.libelleId,
      description: r.description, montant: r.montant, prevu: r.montant, recurrentId: r.id,
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
  var all = readTable_(SH.MVT);
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
      m.date = dateFor_(m.mois, rec.jour);
      sh.getRange(i + 2, 1, 1, HEADERS.Mouvements.length).setValues([toRow_(SH.MVT, m)]);
    }
    count++;
  });
  toDelete.reverse().forEach(function (row) { sh.deleteRow(row); });
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
  var users = readTable_(SH.USERS);
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
    '/mois, le ' + r.jour + ', de ' + r.debut + ' à ' + (r.fin || 'perpétuel') + (r.actif ? '' : ' (inactif)');
}

/* ================================================================== */
/* Accès aux feuilles                                                   */
/* ================================================================== */

function ensureSetup_() {
  var cache = CacheService.getScriptCache();
  if (cache.get('setup_' + APP_VERSION)) return;
  var ss = SpreadsheetApp.getActive();
  Object.keys(HEADERS).forEach(function (name) {
    var headers = HEADERS[name];
    var sh = ss.getSheetByName(name);
    if (!sh) {
      sh = ss.insertSheet(name);
      sh.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
      sh.setFrozenRows(1);
      setTextFormats_(sh, headers);
      if (name === SH.LIB) {
        appendRows_(SH.LIB, DEFAULT_LIBELLES.map(function (d, i) {
          return { id: newId_(), nom: d[0], sens: d[1], groupe: d[2], budget: 0, ordre: i, actif: true };
        }));
      }
    } else {
      // Migration : ajoute les colonnes manquantes à droite.
      var current = sh.getRange(1, 1, 1, Math.max(1, sh.getLastColumn())).getValues()[0];
      var missing = headers.filter(function (h) { return current.indexOf(h) < 0; });
      if (missing.length) {
        sh.getRange(1, current.filter(String).length + 1, 1, missing.length).setValues([missing]).setFontWeight('bold');
        setTextFormats_(sh, sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0]);
      }
    }
  });
  var def = ss.getSheetByName('Feuille 1') || ss.getSheetByName('Sheet1');
  if (def && def.getLastRow() === 0 && ss.getSheets().length > 1) ss.deleteSheet(def);
  if (getConfig_('version') !== APP_VERSION) {
    var prev = getConfig_('version');
    setConfig_('version', APP_VERSION);
    appendJournal_('système', prev ? 'Mise à jour' : 'Installation', 'Application',
      (prev ? 'v' + prev + ' → ' : '') + 'v' + APP_VERSION + ' (' + APP_STAGE + ')');
  }
  cache.put('setup_' + APP_VERSION, '1', 21600);
}

/** Colonnes texte au format brut (évite que Sheets transforme « 2026-11 » en date). */
function setTextFormats_(sh, headers) {
  headers.forEach(function (h, i) {
    if (h && NUM_COLS.indexOf(h) < 0 && BOOL_COLS.indexOf(h) < 0) {
      sh.getRange(2, i + 1, Math.max(1, sh.getMaxRows() - 1), 1).setNumberFormat('@');
    }
  });
}

function sheet_(name) {
  return SpreadsheetApp.getActive().getSheetByName(name);
}

/** Lit une feuille en tenant compte de l'ordre réel des colonnes (migrations). */
function readTable_(name) {
  var sh = sheet_(name);
  var last = sh.getLastRow();
  if (last < 2) return [];
  var width = sh.getLastColumn();
  var header = sh.getRange(1, 1, 1, width).getValues()[0];
  var values = sh.getRange(2, 1, last - 1, width).getValues();
  var tz = Session.getScriptTimeZone();
  var idx = {};
  HEADERS[name].forEach(function (h) { idx[h] = header.indexOf(h); });
  return values
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
  var sh = sheet_(name);
  var header = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0];
  return header.map(function (h) {
    var v = rec[h];
    return v === undefined || v === null ? '' : v;
  });
}

function appendRows_(name, recs) {
  if (!recs.length) return;
  var sh = sheet_(name);
  var rows = recs.map(function (r) { return toRow_(name, r); });
  sh.getRange(sh.getLastRow() + 1, 1, rows.length, rows[0].length).setValues(rows);
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
  var sh = sheet_(name);
  return sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0].indexOf(key) + 1;
}

function findBy_(name, key, value) {
  return readTable_(name).filter(function (r) {
    return String(r[key]).toLowerCase() === String(value).toLowerCase();
  })[0] || null;
}

function upsert_(name, rec, key) {
  key = key || 'id';
  var sh = sheet_(name);
  var row = findRow_(sh, keyCol_(name, key), rec[key]);
  var values = toRow_(name, rec);
  if (row < 0) {
    sh.getRange(sh.getLastRow() + 1, 1, 1, values.length).setValues([values]);
  } else {
    sh.getRange(row, 1, 1, values.length).setValues([values]);
  }
}

function deleteBy_(name, key, value) {
  var sh = sheet_(name);
  var row = findRow_(sh, keyCol_(name, key), value);
  if (row > 0) sh.deleteRow(row);
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
