/**
 * RegisterForm component
 */

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { register as registerApi } from '../../api/auth';
import { registerSchema } from '../../utils/validation';
import type { RegisterRequest } from '../../types/backend';
import { useAuthStore } from '../../stores/auth-store';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';

export function RegisterForm() {
  const navigate = useNavigate();
  const loginAction = useAuthStore((state) => state.login);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting }
  } = useForm<RegisterRequest>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (values: RegisterRequest) => {
    try {
      const response = await registerApi(values);
      loginAction(response);
      toast.success('Registration successful');
      navigate('/dashboard');
    } catch (error: any) {
      toast.error(error.response?.data?.message || 'Registration failed. Please try again.');
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-3xl border border-slate-700 bg-slate-900/80 p-8 shadow-glow">
      <h1 className="text-3xl font-semibold text-white">Create Account</h1>
      <Input
        label="Email"
        type="email"
        {...register('email')}
        error={errors.email?.message}
        placeholder="Enter your email"
      />
      <Input
        label="Username"
        type="text"
        {...register('username')}
        error={errors.username?.message}
        placeholder="Choose a username"
      />
      <Input
        label="Password"
        type="password"
        {...register('password')}
        error={errors.password?.message}
        placeholder="Create a password"
      />
      <Button type="submit" isLoading={isSubmitting} className="w-full">
        Create Account
      </Button>
      <Button
        type="button"
        variant="ghost"
        onClick={() => navigate('/login')}
        className="w-full"
      >
        Already have an account? Sign In
      </Button>
    </form>
  );
}
