// Production build. Overwritten at build time by scripts/set-api-url.mjs when
// API_URL is set (e.g. in the Cloudflare Pages build environment).
export const environment = {
  apiUrl: 'http://localhost:8080',
};
