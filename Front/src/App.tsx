import { useEffect, useState, type FormEvent } from 'react'
import './App.css'

type ApiState = 'checking' | 'online' | 'offline'
type AuthMode = 'login' | 'register'
type User = { id: number; fullName: string; email: string; role: string }
type AuthResponse = { accessToken: string; user: User }

const TOKEN_KEY = 'chamados.token'

function App() {
  const [apiState, setApiState] = useState<ApiState>('checking')
  const [user, setUser] = useState<User | null>(null)
  const [sessionChecked, setSessionChecked] = useState(() => !localStorage.getItem(TOKEN_KEY))
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authError, setAuthError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    fetch('/api/v1/status')
      .then((response) => setApiState(response.ok ? 'online' : 'offline'))
      .catch(() => setApiState('offline'))

    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) return

    fetch('/api/v1/auth/me', { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        if (!response.ok) throw new Error('Sessão inválida')
        setUser(await response.json() as User)
      })
      .catch(() => localStorage.removeItem(TOKEN_KEY))
      .finally(() => setSessionChecked(true))
  }, [])

  async function handleAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setAuthError('')

    const form = new FormData(event.currentTarget)
    const payload = authMode === 'register'
      ? { fullName: form.get('fullName'), email: form.get('email'), password: form.get('password') }
      : { email: form.get('email'), password: form.get('password') }

    try {
      const response = await fetch(`/api/v1/auth/${authMode}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

      if (!response.ok) {
        throw new Error(response.status === 409 ? 'Este e-mail já está cadastrado.' : 'Confira os dados informados.')
      }

      const auth = await response.json() as AuthResponse
      localStorage.setItem(TOKEN_KEY, auth.accessToken)
      setUser(auth.user)
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Não foi possível entrar.')
    } finally {
      setSubmitting(false)
    }
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY)
    setUser(null)
    setAuthMode('login')
  }

  const statusLabel = {
    checking: 'Verificando API',
    online: 'API conectada',
    offline: 'API desconectada',
  }[apiState]

  if (!sessionChecked) {
    return <div className="loading-screen">Validando sessão...</div>
  }

  if (!user) {
    return (
      <div className="auth-page">
        <section className="auth-intro">
          <div className="brand"><span className="brand-mark">CT</span><span>Chamados TI</span></div>
          <div>
            <p className="eyebrow">CENTRAL DE ATENDIMENTO</p>
            <h1>Suporte técnico simples e organizado.</h1>
            <p>Registre solicitações, acompanhe o atendimento e mantenha o histórico em um só lugar.</p>
          </div>
          <span className={`api-status ${apiState}`}><span aria-hidden="true" />{statusLabel}</span>
        </section>

        <main className="auth-panel">
          <div className="auth-card">
            <p className="eyebrow">ACESSO AO SISTEMA</p>
            <h2>{authMode === 'login' ? 'Entre na sua conta' : 'Crie sua conta'}</h2>
            <p>{authMode === 'login' ? 'Use seu e-mail e senha para continuar.' : 'O novo usuário será cadastrado como solicitante.'}</p>

            <form onSubmit={handleAuth}>
              {authMode === 'register' && (
                <label>Nome completo<input name="fullName" autoComplete="name" maxLength={120} required /></label>
              )}
              <label>E-mail<input name="email" type="email" autoComplete="email" maxLength={160} required /></label>
              <label>Senha<input name="password" type="password" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} minLength={8} maxLength={72} required /></label>
              {authError && <p className="form-error" role="alert">{authError}</p>}
              <button type="submit" disabled={submitting || apiState === 'offline'}>
                {submitting ? 'Aguarde...' : authMode === 'login' ? 'Entrar' : 'Criar conta'}
              </button>
            </form>

            <button className="mode-switch" type="button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setAuthError('') }}>
              {authMode === 'login' ? 'Ainda não tenho conta' : 'Já tenho uma conta'}
            </button>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">CT</span><span>Chamados TI</span></div>
        <nav aria-label="Menu principal">
          <a className="nav-item active" href="#visao-geral">Visão geral</a>
          <a className="nav-item" href="#chamados">Chamados</a>
          <a className="nav-item" href="#equipe">Equipe</a>
        </nav>
        <div className="user-menu">
          <span className="user-avatar">{user.fullName.charAt(0).toUpperCase()}</span>
          <div><strong>{user.fullName}</strong><small>{user.role.toLowerCase()}</small></div>
          <button type="button" onClick={logout}>Sair</button>
        </div>
      </aside>

      <main className="dashboard">
        <header className="topbar">
          <div><p className="eyebrow">CENTRAL DE ATENDIMENTO</p><h1 id="visao-geral">Visão geral</h1></div>
          <span className={`api-status ${apiState}`}><span aria-hidden="true" />{statusLabel}</span>
        </header>
        <section className="welcome-card">
          <div><p className="eyebrow">OLÁ, {user.fullName.toUpperCase()}</p><h2>Gerencie solicitações de TI em um só lugar.</h2><p>Abra, acompanhe e priorize chamados com histórico e responsáveis definidos.</p></div>
          <button type="button" disabled title="Disponível na próxima entrega">Novo chamado</button>
        </section>
        <section className="metrics" aria-label="Indicadores">
          <article><span>Chamados abertos</span><strong>—</strong><small>Aguardando integração</small></article>
          <article><span>Em atendimento</span><strong>—</strong><small>Aguardando integração</small></article>
          <article><span>Resolvidos hoje</span><strong>—</strong><small>Aguardando integração</small></article>
        </section>
        <section className="tickets" id="chamados">
          <div className="section-heading"><div><p className="eyebrow">ATIVIDADE</p><h2>Chamados recentes</h2></div></div>
          <div className="empty-state"><span aria-hidden="true">✓</span><h3>Nenhum chamado para exibir</h3><p>Seus chamados aparecerão aqui.</p></div>
        </section>
      </main>
    </div>
  )
}

export default App
