// Service worker de l'appli installée : met en cache la coquille (lancement instantané, écran hors connexion).
// Les données ne passent pas par ici : elles restent chez Google (Apps Script / Google Sheets).
var CACHE = 'budget-shell-v3';
var FILES = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './icon-maskable-512.png'];
self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(FILES); }).then(function () { return self.skipWaiting(); }));
});
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});
self.addEventListener('fetch', function (e) {
  var url = new URL(e.request.url);
  if (url.origin !== location.origin || e.request.method !== 'GET') return;
  // Réseau d'abord (mises à jour de la coquille), cache si hors connexion.
  e.respondWith(fetch(e.request).then(function (r) {
    var copy = r.clone();
    caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
    return r;
  }).catch(function () { return caches.match(e.request, { ignoreSearch: true }).then(function (r) { return r || caches.match('./index.html'); }); }));
});

// Notifications envoyées par Apps Script (contenu chiffré, déchiffré par le navigateur).
self.addEventListener('push', function (e) {
  var d = {};
  try { d = e.data ? e.data.json() : {}; } catch (x) { d = { body: e.data ? e.data.text() : '' }; }
  var opts = { body: d.body || '', icon: 'icon-192.png', badge: 'icon-192.png', tag: d.tag || 'budget', renotify: true, data: { action: d.action || '' } };
  if (d.action === 'saisie') opts.actions = [{ action: 'saisie', title: '+ Saisir' }];
  e.waitUntil(self.registration.showNotification(d.title || 'Budget', opts));
});
self.addEventListener('notificationclick', function (e) {
  e.notification.close();
  var add = e.action === 'saisie' || (e.notification.data && e.notification.data.action === 'saisie');
  var url = self.registration.scope + (add ? '?saisie=1' : '');
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (list) {
    for (var i = 0; i < list.length; i++) {
      if (list[i].url.indexOf(self.registration.scope) === 0) {
        return (add && list[i].navigate ? list[i].navigate(url) : Promise.resolve(list[i])).then(function (c) { return (c || list[i]).focus(); });
      }
    }
    return self.clients.openWindow(url);
  }));
});
