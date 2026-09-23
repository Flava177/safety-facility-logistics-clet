/**
 * Collapsible category groups in the site sidebar (.app-sidebar). Collapsed
 * state persists across page loads via localStorage, keyed by group name,
 * so navigating to a different page doesn't reset what you had open.
 */
(function () {
  var STORAGE_KEY = 'clet-ds-sidebar-collapsed-groups';

  function loadCollapsed() {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {};
    } catch (e) {
      return {};
    }
  }
  function saveCollapsed(state) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      /* private browsing / storage disabled — collapse still works for this load */
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    var collapsed = loadCollapsed();

    document.querySelectorAll('.app-nav-group-toggle').forEach(function (btn) {
      var key = btn.dataset.group;
      var list = document.getElementById(btn.getAttribute('aria-controls'));
      if (!list) return;

      // Never hide the group that contains the current page — the user
      // should always be able to see where they are without an extra click.
      var containsCurrent = btn.dataset.containsCurrent === 'true';
      if (collapsed[key] && !containsCurrent) {
        btn.setAttribute('aria-expanded', 'false');
        list.hidden = true;
      }

      btn.addEventListener('click', function () {
        var expanded = btn.getAttribute('aria-expanded') === 'true';
        btn.setAttribute('aria-expanded', String(!expanded));
        list.hidden = expanded;
        collapsed[key] = expanded;
        saveCollapsed(collapsed);
      });
    });
  });
})();
