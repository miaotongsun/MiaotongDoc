// MiaotongDoc: Service Worker v2 - self-unregistering
// This script forces the browser to unregister any existing Service Worker
var _v = '2.' + Date.now();
self.addEventListener('install', function(event) {
  self.skipWaiting();
});
self.addEventListener('activate', function(event) {
  event.waitUntil(
    Promise.all([
      // Clear all caches
      caches.keys().then(function(names) {
        return Promise.all(names.map(function(n) { return caches.delete(n); }));
      }),
      // Unregister this Service Worker
      self.registration.unregister(),
      // Claim all clients
      self.clients.claim()
    ])
  );
});
// Override fetch to pass through without caching
self.addEventListener('fetch', function(event) {
  // Do nothing - let the request pass through to the network
});
