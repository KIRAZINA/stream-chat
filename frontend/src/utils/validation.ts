/**
 * Validation schemas using Zod
 */

import { z } from 'zod';

// Auth schemas
export const authSchema = z.object({
  username: z
    .string()
    .min(3, 'Username must be at least 3 characters')
    .max(50, 'Username must be at most 50 characters')
    .regex(/^[a-zA-Z0-9_]+$/, 'Alphanumeric + underscore only'),
  email: z.string().email('Invalid email format').optional(),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

export const loginSchema = authSchema.pick({ username: true, password: true });

export const registerSchema = authSchema;

// Stream schemas
export const streamSettingsSchema = z.object({
  slowModeEnabled: z.boolean(),
  slowModeSeconds: z.number().min(0).optional(),
  followersOnlyMode: z.boolean(),
  followersOnlyDurationMinutes: z.number().min(0).optional(),
  subscribersOnlyMode: z.boolean(),
  emoteOnlyMode: z.boolean(),
  maxMessageLength: z.number().min(1).optional(),
  profanityFilterEnabled: z.boolean(),
  linkProtectionEnabled: z.boolean(),
});

export const streamRequestSchema = z.object({
  streamKey: z
    .string()
    .min(3, 'Stream key must be at least 3 characters')
    .max(50, 'Stream key must be at most 50 characters')
    .regex(/^[a-zA-Z0-9_-]+$/, 'Alphanumeric, dash, and underscore only'),
  title: z
    .string()
    .min(1, 'Title is required')
    .max(200, 'Title must be at most 200 characters'),
  description: z.string().max(1000, 'Description must be at most 1000 characters').optional(),
  category: z.string().optional(),
});

// Moderation schemas
export const timeoutRequestSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  durationSeconds: z.number().min(1, 'Duration must be at least 1 second'),
  reason: z.string().optional(),
});

export const banRequestSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  permanent: z.boolean(),
  reason: z.string().optional(),
});

export const pinMessageRequestSchema = z.object({
  messageId: z.number(),
  pin: z.boolean(),
});

export const addModeratorRequestSchema = z.object({
  username: z.string().min(1, 'Username is required'),
});

// Type exports
export type AuthFormData = z.infer<typeof authSchema>;
export type LoginFormData = z.infer<typeof loginSchema>;
export type RegisterFormData = z.infer<typeof registerSchema>;
export type StreamSettingsFormData = z.infer<typeof streamSettingsSchema>;
export type StreamRequestFormData = z.infer<typeof streamRequestSchema>;
export type TimeoutRequestFormData = z.infer<typeof timeoutRequestSchema>;
export type BanRequestFormData = z.infer<typeof banRequestSchema>;
