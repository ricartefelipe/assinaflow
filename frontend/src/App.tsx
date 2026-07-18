import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { AdminPage, CadastroPage, ContaPage, ContratarPage, EntrarPage, HomePage, TrocarPlanoPage } from './pages'
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
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
