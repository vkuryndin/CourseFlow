// Only the last requested email is kept, not credentials or a reset token.
// localStorage allows an email link to open in a new tab of the same browser.
const key = 'courseflow.recovery-email';
const lifetime = 30 * 60 * 1000;

export function rememberRecoveryEmail(email: string) {
    try {
        localStorage.setItem(key, JSON.stringify({ email, expiresAt: Date.now() + lifetime }));
    } catch { /* Recovery still works if browser storage is unavailable. */ }
}

export function clearRecoveryEmail() {
    try { localStorage.removeItem(key); } catch { /* Storage can be disabled. */ }
}

export function recoveryEmail(): string {
    try {
        const stored = JSON.parse(localStorage.getItem(key) || 'null');
        if (typeof stored?.email === 'string' && typeof stored.expiresAt === 'number' && stored.expiresAt > Date.now()) {
            return stored.email;
        }
    } catch { /* Treat invalid or inaccessible storage as no hint. */ }
    clearRecoveryEmail();
    return '';
}
