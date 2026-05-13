import { LoginForm } from '../components/auth/LoginForm';

export function LoginPage() {
  return (
    <div className="grid min-h-screen place-items-center px-4 py-10 bg-slate-950">
      <div className="w-full max-w-xl">
        <LoginForm />
      </div>
    </div>
  );
}

export default LoginPage;
