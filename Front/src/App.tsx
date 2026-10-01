import { useEffect, useState, type FormEvent } from 'react'
import './App.css'

type ApiState = 'checking' | 'online' | 'offline'
type AuthMode = 'login' | 'register'
type User = { id: number; fullName: string; email: string; role: string; active?: boolean }
type AuthResponse = { accessToken: string; user: User }
type Ticket = {
  id: number
  title: string
  description: string
  status: string
  priority: string
  category: string
  requester: { id: number; fullName: string }
  technician: { id: number; fullName: string } | null
  createdAt: string
}
type TicketPage = { content: Ticket[]; totalElements: number }
type TicketHistory = {
  id: number
  action: string
  fromStatus: string | null
  toStatus: string | null
  note: string | null
  actor: { id: number; fullName: string }
  createdAt: string
}

const TOKEN_KEY = 'chamados.token'
const NEXT_STATUSES: Record<string, string[]> = {
  ABERTO: ['EM_TRIAGEM'],
  EM_TRIAGEM: ['EM_ATENDIMENTO'],
  EM_ATENDIMENTO: ['AGUARDANDO_USUARIO', 'RESOLVIDO'],
  AGUARDANDO_USUARIO: ['EM_ATENDIMENTO'],
  RESOLVIDO: ['FECHADO', 'REABERTO'],
  REABERTO: ['EM_ATENDIMENTO'],
}

function formatStatus(status: string) {
  return status.replaceAll('_', ' ').toLowerCase()
}

function App() {
  const [apiState, setApiState] = useState<ApiState>('checking')
  const [user, setUser] = useState<User | null>(null)
  const [sessionChecked, setSessionChecked] = useState(() => !localStorage.getItem(TOKEN_KEY))
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authError, setAuthError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [ticketsLoading, setTicketsLoading] = useState(true)
  const [ticketError, setTicketError] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingTicket, setEditingTicket] = useState<Ticket | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [technicians, setTechnicians] = useState<User[]>([])
  const [historyTicketId, setHistoryTicketId] = useState<number | null>(null)
  const [history, setHistory] = useState<TicketHistory[]>([])

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

  useEffect(() => {
    if (!user) return
    const token = localStorage.getItem(TOKEN_KEY)
    fetch('/api/v1/tickets', { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        if (!response.ok) throw new Error('Não foi possível carregar os chamados.')
        const page = await response.json() as TicketPage
        setTickets(page.content)
      })
      .catch((error: unknown) => setTicketError(error instanceof Error ? error.message : 'Erro ao carregar chamados.'))
      .finally(() => setTicketsLoading(false))

    if (user.role === 'ADMIN') loadUsers().catch(() => setTicketError('Não foi possível carregar a equipe.'))
    if (user.role === 'ADMIN' || user.role === 'TECNICO') {
      loadTechnicians().catch(() => setTicketError('Não foi possível carregar os técnicos.'))
    }
  }, [user])

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
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
      })
      if (!response.ok) throw new Error(response.status === 409 ? 'Este e-mail já está cadastrado.' : 'Confira os dados informados.')
      const auth = await response.json() as AuthResponse
      localStorage.setItem(TOKEN_KEY, auth.accessToken)
      setUser(auth.user)
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Não foi possível entrar.')
    } finally {
      setSubmitting(false)
    }
  }

  async function loadTickets() {
    const token = localStorage.getItem(TOKEN_KEY)
    const response = await fetch('/api/v1/tickets', { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error('Não foi possível atualizar os chamados.')
    const page = await response.json() as TicketPage
    setTickets(page.content)
  }

  async function loadUsers() {
    const response = await fetch('/api/v1/users', { headers: authHeaders() })
    if (!response.ok) throw new Error('Não foi possível carregar os usuários.')
    setUsers(await response.json() as User[])
  }

  async function loadTechnicians() {
    const response = await fetch('/api/v1/users/technicians', { headers: authHeaders() })
    if (!response.ok) throw new Error('Não foi possível carregar os técnicos.')
    setTechnicians(await response.json() as User[])
  }

  async function changeUserRole(userId: number, role: string) {
    setTicketError('')
    const response = await fetch(`/api/v1/users/${userId}/role`, {
      method: 'PATCH',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ role }),
    })
    if (!response.ok) {
      setTicketError('Não foi possível alterar o perfil.')
      return
    }
    await Promise.all([loadUsers(), loadTechnicians()])
  }

  async function assignTicket(ticketId: number, technicianId: string) {
    if (!technicianId) return
    setTicketError('')
    const response = await fetch(`/api/v1/tickets/${ticketId}/assignment`, {
      method: 'PATCH',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ technicianId: Number(technicianId) }),
    })
    if (!response.ok) {
      setTicketError('Não foi possível atribuir o chamado.')
      return
    }
    await loadTickets()
  }

  async function changeTicketStatus(ticket: Ticket, status: string) {
    if (status === ticket.status) return
    const note = window.prompt('Observação sobre a mudança de status (opcional):')
    if (note === null) return
    setTicketError('')
    const response = await fetch(`/api/v1/tickets/${ticket.id}/status`, {
      method: 'PATCH',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, note }),
    })
    if (!response.ok) {
      setTicketError('Transição de status não permitida.')
      return
    }
    await loadTickets()
    if (historyTicketId === ticket.id) setHistoryTicketId(null)
  }

  async function showHistory(ticketId: number) {
    if (historyTicketId === ticketId) {
      setHistoryTicketId(null)
      return
    }
    const response = await fetch(`/api/v1/tickets/${ticketId}/history`, { headers: authHeaders() })
    if (!response.ok) {
      setTicketError('Não foi possível carregar o histórico.')
      return
    }
    setHistory(await response.json() as TicketHistory[])
    setHistoryTicketId(ticketId)
  }

  function authHeaders() {
    return { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` }
  }

  async function handleTicketSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setTicketError('')
    const form = new FormData(event.currentTarget)
    const payload = {
      title: form.get('title'), description: form.get('description'),
      priority: form.get('priority'), category: form.get('category'),
    }
    const url = editingTicket ? `/api/v1/tickets/${editingTicket.id}` : '/api/v1/tickets'

    try {
      const response = await fetch(url, {
        method: editingTicket ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` },
        body: JSON.stringify(payload),
      })
      if (!response.ok) throw new Error('Não foi possível salvar o chamado.')
      await loadTickets()
      closeTicketForm()
    } catch (error) {
      setTicketError(error instanceof Error ? error.message : 'Erro ao salvar chamado.')
    } finally {
      setSubmitting(false)
    }
  }

  async function deleteTicket(ticket: Ticket) {
    if (!window.confirm(`Excluir o chamado “${ticket.title}”?`)) return
    setTicketError('')
    try {
      const response = await fetch(`/api/v1/tickets/${ticket.id}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` },
      })
      if (!response.ok) throw new Error('Não foi possível excluir o chamado.')
      await loadTickets()
    } catch (error) {
      setTicketError(error instanceof Error ? error.message : 'Erro ao excluir chamado.')
    }
  }

  function openTicketForm(ticket: Ticket | null = null) {
    setEditingTicket(ticket)
    setFormOpen(true)
    setTicketError('')
  }

  function closeTicketForm() {
    setEditingTicket(null)
    setFormOpen(false)
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY)
    setUser(null)
    setTickets([])
    setAuthMode('login')
  }

  const statusLabel = { checking: 'Verificando API', online: 'API conectada', offline: 'API desconectada' }[apiState]
  const openCount = tickets.filter((ticket) => ticket.status === 'ABERTO').length
  const progressCount = tickets.filter((ticket) => ['EM_TRIAGEM', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO'].includes(ticket.status)).length
  const resolvedCount = tickets.filter((ticket) => ['RESOLVIDO', 'FECHADO'].includes(ticket.status)).length

  if (!sessionChecked) return <div className="loading-screen">Validando sessão...</div>

  if (!user) {
    return (
      <div className="auth-page">
        <section className="auth-intro">
          <div className="brand"><span className="brand-mark">CT</span><span>Chamados TI</span></div>
          <div><p className="eyebrow">CENTRAL DE ATENDIMENTO</p><h1>Suporte técnico simples e organizado.</h1><p>Registre solicitações, acompanhe o atendimento e mantenha o histórico em um só lugar.</p></div>
          <span className={`api-status ${apiState}`}><span aria-hidden="true" />{statusLabel}</span>
        </section>
        <main className="auth-panel">
          <div className="auth-card">
            <p className="eyebrow">ACESSO AO SISTEMA</p>
            <h2>{authMode === 'login' ? 'Entre na sua conta' : 'Crie sua conta'}</h2>
            <p>{authMode === 'login' ? 'Use seu e-mail e senha para continuar.' : 'O novo usuário será cadastrado como solicitante.'}</p>
            <form onSubmit={handleAuth}>
              {authMode === 'register' && <label>Nome completo<input name="fullName" autoComplete="name" maxLength={120} required /></label>}
              <label>E-mail<input name="email" type="email" autoComplete="email" maxLength={160} required /></label>
              <label>Senha<input name="password" type="password" autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} minLength={8} maxLength={72} required /></label>
              {authError && <p className="form-error" role="alert">{authError}</p>}
              <button type="submit" disabled={submitting || apiState === 'offline'}>{submitting ? 'Aguarde...' : authMode === 'login' ? 'Entrar' : 'Criar conta'}</button>
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
          {user.role === 'ADMIN' && <a className="nav-item" href="#equipe">Equipe</a>}
        </nav>
        <div className="user-menu"><span className="user-avatar">{user.fullName.charAt(0).toUpperCase()}</span><div><strong>{user.fullName}</strong><small>{user.role.toLowerCase()}</small></div><button type="button" onClick={logout}>Sair</button></div>
      </aside>

      <main className="dashboard">
        <header className="topbar"><div><p className="eyebrow">CENTRAL DE ATENDIMENTO</p><h1 id="visao-geral">Visão geral</h1></div><span className={`api-status ${apiState}`}><span aria-hidden="true" />{statusLabel}</span></header>
        <section className="welcome-card"><div><p className="eyebrow">OLÁ, {user.fullName.toUpperCase()}</p><h2>Gerencie solicitações de TI em um só lugar.</h2><p>Abra e acompanhe seus chamados com segurança.</p></div><button type="button" onClick={() => openTicketForm()}>Novo chamado</button></section>

        {formOpen && (
          <section className="ticket-form-card">
            <div className="section-heading"><div><p className="eyebrow">CHAMADO</p><h2>{editingTicket ? 'Editar chamado' : 'Novo chamado'}</h2></div><button className="text-button" type="button" onClick={closeTicketForm}>Cancelar</button></div>
            <form key={editingTicket?.id ?? 'new'} onSubmit={handleTicketSubmit}>
              <label className="wide">Título<input name="title" defaultValue={editingTicket?.title} maxLength={160} required /></label>
              <label>Categoria<input name="category" defaultValue={editingTicket?.category} maxLength={80} placeholder="Ex.: Hardware" required /></label>
              <label>Prioridade<select name="priority" defaultValue={editingTicket?.priority ?? 'MEDIA'}><option value="BAIXA">Baixa</option><option value="MEDIA">Média</option><option value="ALTA">Alta</option><option value="CRITICA">Crítica</option></select></label>
              <label className="wide">Descrição<textarea name="description" defaultValue={editingTicket?.description} rows={5} maxLength={5000} required /></label>
              {ticketError && <p className="form-error wide" role="alert">{ticketError}</p>}
              <button type="submit" disabled={submitting}>{submitting ? 'Salvando...' : 'Salvar chamado'}</button>
            </form>
          </section>
        )}

        <section className="metrics" aria-label="Indicadores">
          <article><span>Chamados abertos</span><strong>{openCount}</strong><small>Nesta página</small></article>
          <article><span>Em atendimento</span><strong>{progressCount}</strong><small>Nesta página</small></article>
          <article><span>Resolvidos</span><strong>{resolvedCount}</strong><small>Nesta página</small></article>
        </section>

        <section className="tickets" id="chamados">
          <div className="section-heading"><div><p className="eyebrow">ATIVIDADE</p><h2>Chamados recentes</h2></div></div>
          {ticketError && !formOpen && <p className="form-error ticket-message" role="alert">{ticketError}</p>}
          {ticketsLoading ? <div className="empty-state"><p>Carregando chamados...</p></div> : tickets.length === 0 ? (
            <div className="empty-state"><span aria-hidden="true">✓</span><h3>Nenhum chamado para exibir</h3><p>Use “Novo chamado” para registrar sua primeira solicitação.</p></div>
          ) : (
            <div className="ticket-list">
              {tickets.map((ticket) => (
                <article className="ticket-row" key={ticket.id}>
                  <div><span className="ticket-id">#{ticket.id}</span><h3>{ticket.title}</h3><p>{ticket.category} · {ticket.requester.fullName} · {new Date(ticket.createdAt).toLocaleDateString('pt-BR')}</p>{ticket.technician && <small>Técnico: {ticket.technician.fullName}</small>}</div>
                  <span className={`priority ${ticket.priority.toLowerCase()}`}>{ticket.priority.toLowerCase()}</span>
                  <span className="ticket-status">{formatStatus(ticket.status)}</span>
                  <div className="row-actions">
                    {ticket.requester.id === user.id && ticket.status === 'ABERTO' && <><button type="button" onClick={() => openTicketForm(ticket)}>Editar</button><button type="button" onClick={() => deleteTicket(ticket)}>Excluir</button></>}
                    <button type="button" onClick={() => showHistory(ticket.id)}>Histórico</button>
                  </div>
                  {user.role === 'ADMIN' && (
                    <label className="workflow-control">Atribuir técnico
                      <select value={ticket.technician?.id ?? ''} onChange={(event) => assignTicket(ticket.id, event.target.value)}>
                        <option value="">Selecione</option>
                        {technicians.map((technician) => <option key={technician.id} value={technician.id}>{technician.fullName}</option>)}
                      </select>
                    </label>
                  )}
                  {(user.role === 'ADMIN' || user.role === 'TECNICO') && NEXT_STATUSES[ticket.status]?.length > 0 && (
                    <label className="workflow-control">Próximo status
                      <select value="" onChange={(event) => changeTicketStatus(ticket, event.target.value)}>
                        <option value="">Selecione</option>
                        {NEXT_STATUSES[ticket.status].map((status) => <option key={status} value={status}>{formatStatus(status)}</option>)}
                      </select>
                    </label>
                  )}
                  {historyTicketId === ticket.id && (
                    <div className="history-panel">
                      <strong>Histórico do atendimento</strong>
                      {history.map((item) => <div key={item.id}><span>{new Date(item.createdAt).toLocaleString('pt-BR')}</span><p>{item.actor.fullName}: {item.note ?? formatStatus(item.action)}{item.toStatus ? ` — ${formatStatus(item.toStatus)}` : ''}</p></div>)}
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>

        {user.role === 'ADMIN' && (
          <section className="team-section" id="equipe">
            <div className="section-heading"><div><p className="eyebrow">ADMINISTRAÇÃO</p><h2>Equipe e permissões</h2></div></div>
            <div className="team-list">
              {users.map((member) => (
                <article key={member.id}><div><strong>{member.fullName}</strong><span>{member.email}</span></div><select value={member.role} onChange={(event) => changeUserRole(member.id, event.target.value)}><option value="SOLICITANTE">Solicitante</option><option value="TECNICO">Técnico</option><option value="ADMIN">Administrador</option></select></article>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  )
}

export default App
