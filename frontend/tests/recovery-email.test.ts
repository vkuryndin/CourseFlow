import { afterEach, describe, expect, it, vi } from 'vitest';
import { clearRecoveryEmail, recoveryEmail, rememberRecoveryEmail } from '../src/modules/identity/recovery-email';

afterEach(() => { localStorage.clear(); vi.useRealTimers(); });

describe('recovery email hint', () => {
    it('keeps only the email and expiration, then removes the hint on success', () => {
        rememberRecoveryEmail('student@example.test');
        expect(recoveryEmail()).toBe('student@example.test');
        expect(Object.keys(JSON.parse(localStorage.getItem('courseflow.recovery-email')!)).sort()).toEqual(['email', 'expiresAt']);
        clearRecoveryEmail();
        expect(recoveryEmail()).toBe('');
    });
    it('does not use an expired email hint', () => {
        vi.useFakeTimers();
        rememberRecoveryEmail('student@example.test');
        vi.advanceTimersByTime(30 * 60 * 1000);
        expect(recoveryEmail()).toBe('');
        expect(localStorage.getItem('courseflow.recovery-email')).toBeNull();
    });
    it('allows manual email input when the stored hint is invalid', () => {
        localStorage.setItem('courseflow.recovery-email', '{invalid');
        expect(recoveryEmail()).toBe('');
        expect(localStorage.getItem('courseflow.recovery-email')).toBeNull();
    });
});
