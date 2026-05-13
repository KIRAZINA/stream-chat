import { RegisterForm } from '../components/auth/RegisterForm';

export function RegisterPage() {
  return (
    <div className="grid min-h-screen place-items-center px-4 py-10 bg-slate-950">
      <div className="w-full max-w-xl">
        <RegisterForm />
      </div>
    </div>
  );
}

export default RegisterPage;
