import { NavLink, Navigate, Outlet, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const nav = [
  { to: '/admin', end: true, label: 'Dashboard' },
  { to: '/admin/clientes', end: false, label: 'Clientes' },
  { to: '/admin/assinaturas', end: false, label: 'Assinaturas' },
  { to: '/admin/operacoes', end: false, label: 'Operações' },
]

export function AdminLayout() {
  const { user, loading, logout } = useAuth()

  if (loading) {
    return (
      <div className="admin-boot">
        <p className="muted">Carregando painel…</p>
      </div>
    )
  }

  if (!user) {
    return <Navigate to="/entrar?next=/admin" replace />
  }

  if (user.role !== 'ADMIN') {
    return <Navigate to="/conta" replace />
  }

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        <Link className="admin-brand" to="/admin">
          <span className="admin-brand-mark">AssinaFlow</span>
          <span className="admin-brand-sub">Console</span>
        </Link>
        <nav className="admin-nav" aria-label="Admin">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `admin-nav-link${isActive ? ' active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="admin-sidebar-foot">
          <p className="admin-user">{user.nome}</p>
          <p className="muted tiny">{user.email}</p>
          <div className="admin-sidebar-actions">
            <Link className="btn btn-ghost btn-sm" to="/conta">Portal</Link>
            <button className="btn btn-ghost btn-sm" type="button" onClick={logout}>Sair</button>
          </div>
        </div>
      </aside>
      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  )
}
