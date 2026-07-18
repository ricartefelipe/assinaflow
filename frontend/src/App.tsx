import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { CadastroPage, ContaPage, ContratarPage, EntrarPage, HomePage } from './pages'
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
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
