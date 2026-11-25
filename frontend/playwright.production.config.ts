import { defineConfig } from '@playwright/test';
import base from './playwright.config';

// Same scenarios as the dev suite, but against the actual build on a separate port.
export default defineConfig({
    ...base,
    use: { ...base.use, baseURL: 'http://localhost:4173' },
    webServer: {
        command: 'npm run preview -- --host localhost --port 4173 --strictPort',
        url: 'http://localhost:4173',
        reuseExistingServer: false,
    },
});
