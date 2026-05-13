import { describe, expect, it } from 'vitest';
import { canModerate, hasGlobalRole } from './permissions';
import type { User } from '../types/backend';

const makeUser = (roles: string[]): User => ({
  id: 1,
  username: 'test',
  email: 'test@test.com',
  roles: roles.map((r, i) => ({ id: i, role: r })),
  streamRoles: [],
});

describe('permission helpers', () => {
  it('hasGlobalRole checks user roles', () => {
    const mod = makeUser(['ROLE_USER', 'ROLE_MODERATOR']);
    expect(hasGlobalRole(mod, 'ROLE_MODERATOR')).toBe(true);
    expect(hasGlobalRole(mod, 'ROLE_ADMIN')).toBe(false);
  });

  it('canModerate allows moderator, admin, and broadcaster roles', () => {
    expect(canModerate(makeUser(['ROLE_MODERATOR']), [])).toBe(true);
    expect(canModerate(makeUser(['ROLE_ADMIN']), [])).toBe(true);
    expect(canModerate(makeUser(['ROLE_BROADCASTER']), [{ id: 1, userId: 1, streamId: 1, role: 'ROLE_BROADCASTER' }])).toBe(true);
    expect(canModerate(makeUser(['ROLE_USER']), [])).toBe(false);
  });
});
