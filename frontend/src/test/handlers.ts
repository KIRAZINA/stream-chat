import { rest } from 'msw';

export const handlers = [
  rest.get('/api/users/me', (req, res, ctx) => {
    return res(
      ctx.json({
        id: 1,
        username: 'testuser',
        email: 'test@example.com',
        roles: ['ROLE_USER'],
        streamRoles: [],
      })
    );
  }),

  rest.get('/api/streams/:id/messages', (req, res, ctx) => {
    return res(
      ctx.json([
        {
          id: 1,
          content: 'Hello world!',
          username: 'testuser',
          timestamp: new Date().toISOString(),
          userId: 1,
        },
      ])
    );
  }),
];