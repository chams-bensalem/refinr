/**
 * Configuration lue au chargement de la page, sans rebuild : `/config.js` definit `window.__REFINR_CONFIG__`.
 * En dev (Vite) c'est `public/config.js` (vide) ; dans l'image Docker, nginx le genere depuis `REFINR_API_BASE_URL`.
 */
interface RuntimeConfig {
    /** URL de base de l'API REST (defaut `/api/v1`, meme origine, via le proxy Vite ou nginx). */
    apiBaseUrl?: string;
}

declare global {
    interface Window {
        __REFINR_CONFIG__?: RuntimeConfig;
    }
}

const DEFAULT_API_BASE_URL = '/api/v1';

export function apiBaseUrl(): string {
    const configured = window.__REFINR_CONFIG__?.apiBaseUrl?.trim();
    return configured ? configured.replace(/\/+$/, '') : DEFAULT_API_BASE_URL;
}
