import { useCallback, useEffect, useState, type FormEvent } from 'react'
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
  dueAt: string
  resolvedAt: string | null
  slaStatus: string
  createdAt: string
}
type TicketPage = { content: Ticket[]; page: number; totalElements: number; totalPages: number }
type TicketSummary = { total: number; open: number; inProgress: number; resolved: number; overdue: number }
type TicketFilters = { q: string; status: string; priority: string; category: string; technicianId: string }
type TicketHistory = {
  id: number
  action: string
  fromStatus: string | null
  toStatus: string | null
  note: string | null
  actor: { id: number; fullName: string }
  createdAt: string
}
type TicketComment = {
  id: number
  content: string
  internal: boolean
  author: { id: number; fullName: string }
  createdAt: string
}
type TicketCategory = { id: number; name: string; active: boolean }
type TicketAttachment = {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  uploader: { id: number; fullName: string }
  createdAt: string
}
type Notification = {
  id: number
  ticketId: number
  type: string
  message: string
  readAt: string | null
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
  const [ticketPage, setTicketPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [summary, setSummary] = useState<TicketSummary>({ total: 0, open: 0, inProgress: 0, resolved: 0, overdue: 0 })
  const [filters, setFilters] = useState<TicketFilters>({ q: '', status: '', priority: '', category: '', technicianId: '' })
  const [ticketsLoading, setTicketsLoading] = useState(true)
  const [ticketError, setTicketError] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingTicket, setEditingTicket] = useState<Ticket | null>(null)
  const [users, setUsers] = useState<User[]>([])
  const [technicians, setTechnicians] = useState<User[]>([])
  const [historyTicketId, setHistoryTicketId] = useState<number | null>(null)
  const [history, setHistory] = useState<TicketHistory[]>([])
  const [commentsTicketId, setCommentsTicketId] = useState<number | null>(null)
  const [comments, setComments] = useState<TicketComment[]>([])
  const [categories, setCategories] = useState<TicketCategory[]>([])
  const [allCategories, setAllCategories] = useState<TicketCategory[]>([])
  const [attachmentsTicketId, setAttachmentsTicketId] = useState<number | null>(null)
  const [attachments, setAttachments] = useState<TicketAttachment[]>([])
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadNotifications, setUnreadNotifications] = useState(0)
  const [openWorkflowMenu, setOpenWorkflowMenu] = useState<string | null>(null)

  const filterParams = useCallback(() => {
    const params = new URLSearchParams()
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
    return params
  }, [filters])

  function handleWorkflowMenuToggle(menuId: string, isOpen: boolean) {
    setOpenWorkflowMenu((current) => isOpen ? menuId : current === menuId ? null : current)
  }

  const loadTickets = useCallback(async () => {
    const params = filterParams()
    params.set('page', String(ticketPage))
    params.set('size', '10')
    const token = localStorage.getItem(TOKEN_KEY)
    const response = await fetch(`/api/v1/tickets?${params}`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error('Não foi possível atualizar os chamados.')
    const page = await response.json() as TicketPage
    setTickets(page.content)
    setTotalPages(page.totalPages)
  }, [filterParams, ticketPage])

  const loadSummary = useCallback(async () => {
    const token = localStorage.getItem(TOKEN_KEY)
    const response = await fetch(`/api/v1/tickets/summary?${filterParams()}`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error('Não foi possível atualizar os indicadores.')
    setSummary(await response.json() as TicketSummary)
  }, [filterParams])

  const loadNotifications = useCallback(async () => {
    const token = localStorage.getItem(TOKEN_KEY)
    const headers = { Authorization: `Bearer ${token}` }
    const [listResponse, countResponse] = await Promise.all([
      fetch('/api/v1/notifications', { headers }),
      fetch('/api/v1/notifications/unread-count', { headers }),
    ])
    if (!listResponse.ok || !countResponse.ok) throw new Error('Não foi possível atualizar as notificações.')
    setNotifications(await listResponse.json() as Notification[])
    const unread = await countResponse.json() as { count: number }
    setUnreadNotifications(unread.count)
  }, [])

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
    loadCategories().catch(() => setTicketError('Não foi possível carregar as categorias.'))
    if (user.role === 'ADMIN') {
      loadUsers().catch(() => setTicketError('Não foi possível carregar a equipe.'))
      loadAllCategories().catch(() => setTicketError('Não foi possível carregar todas as categorias.'))
    }
    if (user.role === 'ADMIN' || user.role === 'TECNICO') {
      loadTechnicians().catch(() => setTicketError('Não foi possível carregar os técnicos.'))
    }
  }, [user])

  useEffect(() => {
    if (!user) return
    // Atualiza a visão sempre que página ou filtros mudarem.
    // oxlint-disable-next-line react/set-state-in-effect
    Promise.all([loadTickets(), loadSummary()])
      .catch((error: unknown) => setTicketError(error instanceof Error ? error.message : 'Erro ao carregar chamados.'))
      .finally(() => setTicketsLoading(false))
  }, [user, loadTickets, loadSummary])

  useEffect(() => {
    if (!user) return
    // oxlint-disable-next-line react/set-state-in-effect
    loadNotifications().catch(() => setTicketError('Não foi possível carregar as notificações.'))
    const interval = window.setInterval(() => {
      loadNotifications().catch(() => undefined)
    }, 60_000)
    return () => window.clearInterval(interval)
  }, [user, loadNotifications])

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

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setTicketPage(0)
    setFilters({
      q: String(form.get('q') ?? ''),
      status: String(form.get('status') ?? ''),
      priority: String(form.get('priority') ?? ''),
      category: String(form.get('category') ?? ''),
      technicianId: String(form.get('technicianId') ?? ''),
    })
  }

  function clearFilters() {
    setTicketPage(0)
    setFilters({ q: '', status: '', priority: '', category: '', technicianId: '' })
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

  async function loadCategories() {
    const response = await fetch('/api/v1/categories', { headers: authHeaders() })
    if (!response.ok) throw new Error('Não foi possível carregar as categorias.')
    setCategories(await response.json() as TicketCategory[])
  }

  async function loadAllCategories() {
    const response = await fetch('/api/v1/categories/admin', { headers: authHeaders() })
    if (!response.ok) throw new Error('Não foi possível carregar as categorias.')
    setAllCategories(await response.json() as TicketCategory[])
  }

  async function createCategory(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const response = await fetch('/api/v1/categories', {
      method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: form.get('name') }),
    })
    if (!response.ok) {
      setTicketError(response.status === 409 ? 'Esta categoria já existe.' : 'Não foi possível criar a categoria.')
      return
    }
    event.currentTarget.reset()
    await Promise.all([loadCategories(), loadAllCategories()])
  }

  async function toggleCategory(category: TicketCategory) {
    const response = await fetch(`/api/v1/categories/${category.id}`, {
      method: 'PATCH', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: category.name, active: !category.active }),
    })
    if (!response.ok) {
      setTicketError('Não foi possível alterar a categoria.')
      return
    }
    if (category.name === filters.category) clearFilters()
    await Promise.all([loadCategories(), loadAllCategories()])
  }

  async function showAttachments(ticketId: number) {
    if (attachmentsTicketId === ticketId) {
      setAttachmentsTicketId(null)
      return
    }
    const response = await fetch(`/api/v1/tickets/${ticketId}/attachments`, { headers: authHeaders() })
    if (!response.ok) {
      setTicketError('Não foi possível carregar os anexos.')
      return
    }
    setAttachments(await response.json() as TicketAttachment[])
    setAttachmentsTicketId(ticketId)
  }

  async function uploadAttachment(event: FormEvent<HTMLFormElement>, ticketId: number) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const response = await fetch(`/api/v1/tickets/${ticketId}/attachments`, {
      method: 'POST', headers: authHeaders(), body: data,
    })
    if (!response.ok) {
      setTicketError('Use um arquivo PDF, PNG, JPG ou TXT com até 5 MB.')
      return
    }
    form.reset()
    const refresh = await fetch(`/api/v1/tickets/${ticketId}/attachments`, { headers: authHeaders() })
    setAttachments(await refresh.json() as TicketAttachment[])
  }

  async function downloadAttachment(ticketId: number, attachment: TicketAttachment) {
    const response = await fetch(`/api/v1/tickets/${ticketId}/attachments/${attachment.id}`, { headers: authHeaders() })
    if (!response.ok) {
      setTicketError('Não foi possível baixar o anexo.')
      return
    }
    const url = URL.createObjectURL(await response.blob())
    const link = document.createElement('a')
    link.href = url
    link.download = attachment.originalName
    link.click()
    URL.revokeObjectURL(url)
  }

  async function markNotificationAsRead(notificationId: number) {
    const response = await fetch(`/api/v1/notifications/${notificationId}/read`, {
      method: 'PATCH', headers: authHeaders(),
    })
    if (!response.ok) {
      setTicketError('Não foi possível atualizar a notificação.')
      return
    }
    await loadNotifications()
  }

  async function markAllNotificationsAsRead() {
    const response = await fetch('/api/v1/notifications/read-all', {
      method: 'PATCH', headers: authHeaders(),
    })
    if (!response.ok) {
      setTicketError('Não foi possível atualizar as notificações.')
      return
    }
    await loadNotifications()
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
    setOpenWorkflowMenu(null)
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
    await Promise.all([loadTickets(), loadSummary()])
  }

  async function changeTicketStatus(ticket: Ticket, status: string) {
    if (status === ticket.status) return
    setOpenWorkflowMenu(null)
    setTicketError('')
    const response = await fetch(`/api/v1/tickets/${ticket.id}/status`, {
      method: 'PATCH',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, note: null }),
    })
    if (!response.ok) {
      setTicketError('Transição de status não permitida.')
      return
    }
    const updatedTicket = await response.json() as Ticket
    setTickets((current) => current.map((item) => item.id === updatedTicket.id ? updatedTicket : item))
    await Promise.all([loadTickets(), loadSummary()])
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

  async function showComments(ticketId: number) {
    if (commentsTicketId === ticketId) {
      setCommentsTicketId(null)
      return
    }
    const response = await fetch(`/api/v1/tickets/${ticketId}/comments`, { headers: authHeaders() })
    if (!response.ok) {
      setTicketError('Não foi possível carregar os comentários.')
      return
    }
    setComments(await response.json() as TicketComment[])
    setCommentsTicketId(ticketId)
  }

  async function addComment(event: FormEvent<HTMLFormElement>, ticketId: number) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const response = await fetch(`/api/v1/tickets/${ticketId}/comments`, {
      method: 'POST',
      headers: { ...authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: form.get('content'), internal: form.get('internal') === 'on' }),
    })
    if (!response.ok) {
      setTicketError('Não foi possível adicionar o comentário.')
      return
    }
    event.currentTarget.reset()
    const refresh = await fetch(`/api/v1/tickets/${ticketId}/comments`, { headers: authHeaders() })
    setComments(await refresh.json() as TicketComment[])
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
      await Promise.all([loadTickets(), loadSummary()])
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
      await Promise.all([loadTickets(), loadSummary()])
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
    setNotifications([])
    setUnreadNotifications(0)
    setAuthMode('login')
  }

  const statusLabel = { checking: 'Verificando API', online: 'API conectada', offline: 'API desconectada' }[apiState]
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
          <a className="nav-item" href="#notificacoes">Notificações {unreadNotifications > 0 && <strong className="notification-badge">{unreadNotifications}</strong>}</a>
          <a className="nav-item" href="#chamados">Chamados</a>
          {user.role === 'ADMIN' && <a className="nav-item" href="#equipe">Equipe</a>}
          {user.role === 'ADMIN' && <a className="nav-item" href="#categorias">Categorias</a>}
        </nav>
        <div className="user-menu"><span className="user-avatar">{user.fullName.charAt(0).toUpperCase()}</span><div><strong>{user.fullName}</strong><small>{user.role.toLowerCase()}</small></div><button type="button" onClick={logout}>Sair</button></div>
      </aside>

      <main className="dashboard">
        <header className="topbar"><div><p className="eyebrow">CENTRAL DE ATENDIMENTO</p><h1 id="visao-geral">Visão geral</h1></div><span className={`api-status ${apiState}`}><span aria-hidden="true" />{statusLabel}</span></header>
        <section className="welcome-card"><div><p className="eyebrow">OLÁ, {user.fullName.toUpperCase()}</p><h2>Gerencie solicitações de TI em um só lugar.</h2><p>Abra e acompanhe seus chamados com segurança.</p></div><button type="button" onClick={() => openTicketForm()}>Novo chamado</button></section>

        <section className="notifications-section" id="notificacoes">
          <div className="section-heading"><div><p className="eyebrow">ALERTAS DE SLA</p><h2>Notificações</h2></div>{unreadNotifications > 0 && <button className="text-button" type="button" onClick={markAllNotificationsAsRead}>Marcar todas como lidas</button>}</div>
          {notifications.length === 0 ? <p className="notifications-empty">Nenhum alerta de SLA no momento.</p> : (
            <div className="notification-list">
              {notifications.map((notification) => (
                <article className={notification.readAt ? 'notification-read' : ''} key={notification.id}>
                  <span className={`notification-kind ${notification.type === 'SLA_VENCIDO' ? 'overdue' : ''}`}>{notification.type === 'SLA_VENCIDO' ? 'SLA vencido' : 'SLA em risco'}</span>
                  <div><strong>{notification.message}</strong><small>{new Date(notification.createdAt).toLocaleString('pt-BR')}</small></div>
                  {!notification.readAt && <button type="button" onClick={() => markNotificationAsRead(notification.id)}>Marcar como lida</button>}
                </article>
              ))}
            </div>
          )}
        </section>

        {formOpen && (
          <section className="ticket-form-card">
            <div className="section-heading"><div><p className="eyebrow">CHAMADO</p><h2>{editingTicket ? 'Editar chamado' : 'Novo chamado'}</h2></div><button className="text-button" type="button" onClick={closeTicketForm}>Cancelar</button></div>
            <form key={editingTicket?.id ?? 'new'} onSubmit={handleTicketSubmit}>
              <label className="wide">Título<input name="title" defaultValue={editingTicket?.title} maxLength={160} required /></label>
              <label>Categoria<select name="category" defaultValue={editingTicket?.category ?? ''} required><option value="" disabled>Selecione</option>{categories.map((category) => <option key={category.id} value={category.name}>{category.name}</option>)}</select></label>
              <label>Prioridade<select name="priority" defaultValue={editingTicket?.priority ?? 'MEDIA'}><option value="BAIXA">Baixa</option><option value="MEDIA">Média</option><option value="ALTA">Alta</option><option value="CRITICA">Crítica</option></select></label>
              <label className="wide">Descrição<textarea name="description" defaultValue={editingTicket?.description} rows={5} maxLength={5000} required /></label>
              {ticketError && <p className="form-error wide" role="alert">{ticketError}</p>}
              <button type="submit" disabled={submitting}>{submitting ? 'Salvando...' : 'Salvar chamado'}</button>
            </form>
          </section>
        )}

        <section className="metrics" aria-label="Indicadores">
          <article><span>Total de chamados</span><strong>{summary.total}</strong><small>Com os filtros atuais</small></article>
          <article><span>Abertos</span><strong>{summary.open}</strong><small>Aguardando triagem</small></article>
          <article><span>Em andamento</span><strong>{summary.inProgress}</strong><small>Na fila de suporte</small></article>
          <article><span>Resolvidos</span><strong>{summary.resolved}</strong><small>Concluídos</small></article>
          <article className={summary.overdue > 0 ? 'metric-alert' : ''}><span>SLA vencido</span><strong>{summary.overdue}</strong><small>Exigem atenção</small></article>
        </section>

        <section className="tickets" id="chamados">
          <div className="section-heading"><div><p className="eyebrow">ATIVIDADE</p><h2>Chamados recentes</h2></div></div>
          <form className="ticket-filters" key={JSON.stringify(filters)} onSubmit={applyFilters}>
            <label className="filter-search">Buscar<input name="q" defaultValue={filters.q} placeholder="Título ou descrição" /></label>
            <label>Status<select name="status" defaultValue={filters.status}><option value="">Todos</option>{Object.keys(NEXT_STATUSES).map((status) => <option key={status} value={status}>{formatStatus(status)}</option>)}<option value="FECHADO">fechado</option></select></label>
            <label>Prioridade<select name="priority" defaultValue={filters.priority}><option value="">Todas</option><option value="BAIXA">Baixa</option><option value="MEDIA">Média</option><option value="ALTA">Alta</option><option value="CRITICA">Crítica</option></select></label>
            <label>Categoria<select name="category" defaultValue={filters.category}><option value="">Todas</option>{categories.map((category) => <option key={category.id} value={category.name}>{category.name}</option>)}</select></label>
            {(user.role === 'ADMIN' || user.role === 'TECNICO') && <label>Técnico<select name="technicianId" defaultValue={filters.technicianId}><option value="">Todos</option>{technicians.map((technician) => <option key={technician.id} value={technician.id}>{technician.fullName}</option>)}</select></label>}
            <div className="filter-actions"><button type="submit">Filtrar</button><button className="secondary-button" type="button" onClick={clearFilters}>Limpar</button></div>
          </form>
          {ticketError && !formOpen && <p className="form-error ticket-message" role="alert">{ticketError}</p>}
          {ticketsLoading ? <div className="empty-state"><p>Carregando chamados...</p></div> : tickets.length === 0 ? (
            <div className="empty-state"><span aria-hidden="true">✓</span><h3>Nenhum chamado para exibir</h3><p>Use “Novo chamado” para registrar sua primeira solicitação.</p></div>
          ) : (
            <div className="ticket-list">
              {tickets.map((ticket) => (
                <article className="ticket-row" key={ticket.id}>
                  <div><span className="ticket-id">#{ticket.id}</span><h3>{ticket.title}</h3><p>{ticket.category} · {ticket.requester.fullName} · {new Date(ticket.createdAt).toLocaleDateString('pt-BR')}</p>{ticket.technician && <small>Técnico: {ticket.technician.fullName}</small>}<small className={`sla ${ticket.slaStatus.toLowerCase()}`}>SLA: {formatStatus(ticket.slaStatus)} · {new Date(ticket.dueAt).toLocaleString('pt-BR')}</small></div>
                  <span className={`priority ${ticket.priority.toLowerCase()}`}>{ticket.priority.toLowerCase()}</span>
                  <span className="ticket-status">{formatStatus(ticket.status)}</span>
                  <div className="row-actions">
                    {ticket.requester.id === user.id && ticket.status === 'ABERTO' && <><button type="button" onClick={() => openTicketForm(ticket)}>Editar</button><button className="delete-action" type="button" onClick={() => deleteTicket(ticket)}>Excluir</button></>}
                    <button type="button" onClick={() => showHistory(ticket.id)}>Histórico</button>
                    <button type="button" onClick={() => showComments(ticket.id)}>Comentários</button>
                    <button type="button" onClick={() => showAttachments(ticket.id)}>Anexos</button>
                  </div>
                  {user.role === 'ADMIN' && (
                    <div className="workflow-control">
                      <span>Atribuir técnico</span>
                      <details
                        className="workflow-menu"
                        open={openWorkflowMenu === `assignment-${ticket.id}`}
                        onToggle={(event) => handleWorkflowMenuToggle(
                          `assignment-${ticket.id}`, event.currentTarget.open)}
                      >
                        <summary>{ticket.technician?.fullName ?? 'Selecione'}</summary>
                        <div className="workflow-menu-options">
                          {technicians.map((technician) => (
                            <button
                              key={technician.id}
                              type="button"
                              disabled={ticket.technician?.id === technician.id}
                              onClick={() => assignTicket(ticket.id, String(technician.id))}
                            >
                              {technician.fullName}
                            </button>
                          ))}
                        </div>
                      </details>
                    </div>
                  )}
                  {(user.role === 'ADMIN' || user.role === 'TECNICO') && NEXT_STATUSES[ticket.status]?.length > 0 && (
                    <div className="workflow-control">
                      <span>Status atual</span>
                      <details
                        className="workflow-menu"
                        open={openWorkflowMenu === `status-${ticket.id}`}
                        onToggle={(event) => handleWorkflowMenuToggle(
                          `status-${ticket.id}`, event.currentTarget.open)}
                      >
                        <summary>{formatStatus(ticket.status)}</summary>
                        <div className="workflow-menu-options">
                          <button type="button" disabled>Atual: {formatStatus(ticket.status)}</button>
                          {NEXT_STATUSES[ticket.status].map((status) => (
                            <button key={status} type="button" onClick={() => changeTicketStatus(ticket, status)}>
                              Alterar para: {formatStatus(status)}
                            </button>
                          ))}
                        </div>
                      </details>
                    </div>
                  )}
                  {historyTicketId === ticket.id && (
                    <div className="history-panel">
                      <strong>Histórico do atendimento</strong>
                      {history.map((item) => <div key={item.id}><span>{new Date(item.createdAt).toLocaleString('pt-BR')}</span><p>{item.actor.fullName}: {item.note ?? formatStatus(item.action)}{item.toStatus ? ` — ${formatStatus(item.toStatus)}` : ''}</p></div>)}
                    </div>
                  )}
                  {commentsTicketId === ticket.id && (
                    <div className="comments-panel">
                      <strong>Comentários</strong>
                      {comments.length === 0 && <p>Nenhum comentário adicionado.</p>}
                      {comments.map((comment) => <div key={comment.id} className={comment.internal ? 'internal-comment' : ''}><span>{new Date(comment.createdAt).toLocaleString('pt-BR')} · {comment.author.fullName}{comment.internal ? ' · interno' : ''}</span><p>{comment.content}</p></div>)}
                      <form onSubmit={(event) => addComment(event, ticket.id)}>
                        <textarea name="content" rows={3} maxLength={2000} placeholder="Escreva uma atualização..." required />
                        {(user.role === 'ADMIN' || user.role === 'TECNICO') && <label><input name="internal" type="checkbox" /> Visível somente para o suporte</label>}
                        <button type="submit">Comentar</button>
                      </form>
                    </div>
                  )}
                  {attachmentsTicketId === ticket.id && (
                    <div className="attachments-panel">
                      <strong>Anexos do chamado</strong>
                      {attachments.length === 0 && <p>Nenhum arquivo anexado.</p>}
                      {attachments.map((attachment) => <div key={attachment.id}><div><span>{attachment.originalName}</span><small>{attachment.uploader.fullName} · {(attachment.sizeBytes / 1024).toFixed(1)} KB · {new Date(attachment.createdAt).toLocaleString('pt-BR')}</small></div><button type="button" onClick={() => downloadAttachment(ticket.id, attachment)}>Baixar</button></div>)}
                      <form onSubmit={(event) => uploadAttachment(event, ticket.id)}>
                        <label>Adicionar arquivo<input name="file" type="file" accept=".pdf,.png,.jpg,.jpeg,.txt" required /></label>
                        <small>PDF, PNG, JPG ou TXT de até 5 MB.</small>
                        <button type="submit">Enviar</button>
                      </form>
                    </div>
                  )}
                </article>
              ))}
            </div>
          )}
          {totalPages > 1 && <div className="pagination"><button type="button" disabled={ticketPage === 0} onClick={() => setTicketPage((page) => page - 1)}>Anterior</button><span>Página {ticketPage + 1} de {totalPages}</span><button type="button" disabled={ticketPage + 1 >= totalPages} onClick={() => setTicketPage((page) => page + 1)}>Próxima</button></div>}
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

        {user.role === 'ADMIN' && (
          <section className="team-section" id="categorias">
            <div className="section-heading"><div><p className="eyebrow">CONFIGURAÇÃO</p><h2>Categorias de chamados</h2></div></div>
            <form className="category-form" onSubmit={createCategory}><label>Nova categoria<input name="name" maxLength={80} placeholder="Ex.: Impressoras" required /></label><button type="submit">Adicionar</button></form>
            <div className="team-list category-list">
              {allCategories.map((category) => (
                <article key={category.id}><div><strong>{category.name}</strong><span>{category.active ? 'Disponível para novos chamados' : 'Categoria inativa'}</span></div><button className={category.active ? 'deactivate-button' : 'secondary-button'} type="button" onClick={() => toggleCategory(category)}>{category.active ? 'Desativar' : 'Ativar'}</button></article>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  )
}

export default App
