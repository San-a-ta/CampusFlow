(() => {
    const storageKey = 'campusflow-theme';
    const preference = localStorage.getItem(storageKey) || 'system';
    const media = window.matchMedia('(prefers-color-scheme: dark)');

    function applyTheme(selected) {
        const resolved = selected === 'system'
            ? (media.matches ? 'dark' : 'light')
            : selected;
        document.documentElement.dataset.theme = resolved;
        document.documentElement.dataset.themePreference = selected;
    }

    applyTheme(preference);

    function initializeSelector() {
        const selector = document.getElementById('theme-selector');
        if (!selector) return;
        selector.value = localStorage.getItem(storageKey) || 'system';
        selector.addEventListener('change', () => {
            localStorage.setItem(storageKey, selector.value);
            applyTheme(selector.value);
        });
    }

    document.addEventListener('DOMContentLoaded', initializeSelector, { once: true });
    media.addEventListener('change', () => {
        if ((localStorage.getItem(storageKey) || 'system') === 'system') applyTheme('system');
    });
})();
