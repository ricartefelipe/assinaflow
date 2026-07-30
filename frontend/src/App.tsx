import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AdminCustomersPage } from './admin/AdminCustomersPage'
import { AdminDashboardPage } from './admin/AdminDashboardPage'
import { AdminLayout } from './admin/AdminLayout'
import { AdminOperationsPage } from './admin/AdminOperationsPage'
import { AdminSubscriptionsPage } from './admin/AdminSubscriptionsPage'
import { AuthProvider } from './auth/AuthContext'
import { CadastroPage, ContaPage, ContratarPage, EntrarPage, HomePage, TrocarPlanoPage } from './pages'
import './styles.css'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/cadastro" element={<CadastroPage />} />
          <Route path="/entrar" element={<EntrarPage />} />
          <Route path="/conta" element={<ContaPage />} />
          <Route path="/contratar" element={<ContratarPage />} />
          <Route path="/trocar-plano" element={<TrocarPlanoPage />} />
          <Route path="/admin" element={<AdminLayout />}>
            <Route index element={<AdminDashboardPage />} />
            <Route path="clientes" element={<AdminCustomersPage />} />
            <Route path="assinaturas" element={<AdminSubscriptionsPage />} />
            <Route path="operacoes" element={<AdminOperationsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
