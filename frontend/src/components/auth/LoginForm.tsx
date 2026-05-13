/**
 * LoginForm component
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, useLocation } from 'react-router-dom';
import toast from 'react-hot-toast';
import { login } from '../../api/auth';
import { loginSchema } from '../../utils/validation';
import type { LoginRequest } from '../../types/backend';
import { useAuthStore } from '../../stores/auth-store';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';

export function LoginForm() {
  const navigate = useNavigate();
  const location = useLocation();
  const loginAction = useAuthStore((state) => state.login);

  const from = (location.state as any)?.from?.pathname || '/dashboard';

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting }
  } = useForm<LoginRequest>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginRequest) => {
    try {
      const response = await login(values);
      loginAction(response);
      toast.success('Login successful');
      navigate(from, { replace: true });
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Login failed. Please check your credentials.');
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-3xl border border-slate-700 bg-slate-900/80 p-8 shadow-glow">
      <h1 className="text-3xl font-semibold text-white">Sign In</h1>
      <Input
        label="Username"
        type="text"
        {...register('username')}
        error={errors.username?.message}
        placeholder="Enter your username"
      />
      <Input
        label="Password"
        type="password"
        {...register('password')}
        error={errors.password?.message}
        placeholder="Enter your password"
      />
      <Button type="submit" isLoading={isSubmitting} className="w-full">
        Sign In
      </Button>
      <Button
        type="button"
        variant="ghost"
        onClick={() => navigate('/register')}
        className="w-full"
      >
        Don't have an account? Register
      </Button>
    </form>
  );
}
