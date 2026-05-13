import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';

export function NotFoundPage() {
  return (
    <div className="grid min-h-screen place-items-center px-4 py-10 bg-slate-950">
      <div className="rounded-3xl border border-slate-700 bg-slate-900/90 p-10 text-center shadow-glow">
        <h1 className="text-5xl font-semibold text-slate-100">404</h1>
        <p className="mt-4 text-slate-400">Page not found.</p>
        <Link to="/dashboard">
          <Button className="mt-6">Back to Dashboard</Button>
        </Link>
      </div>
    </div>
  );
}

export default NotFoundPage;
