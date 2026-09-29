import { useEffect, useState } from 'react'
import './App.css'

type ApiState = 'checking' | 'online' | 'offline'

function App() {
  const [apiState, setApiState] = useState<ApiState>('checking')

  useEffect(() => {
    const controller = new AbortController()

    fetch('/api/v1/status', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error('API indisponível')
        setApiState('online')
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        setApiState('offline')
      })

    return () => controller.abort()
  }, [])

  const statusLabel = {
    checking: 'Verificando API',
    online: 'API conectada',
    offline: 'API desconectada',
  }[apiState]

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">CT</span>
          <span>Chamados TI</span>
        </div>

        <nav aria-label="Menu principal">
          <a className="nav-item active" href="#visao-geral">Visão geral</a>
          <a className="nav-item" href="#chamados">Chamados</a>
          <a className="nav-item" href="#equipe">Equipe</a>
        </nav>

        <p className="sidebar-note">Primeira versão em desenvolvimento</p>
      </aside>

      <main>
        <header className="topbar">
          <div>
            <p className="eyebrow">CENTRAL DE ATENDIMENTO</p>
            <h1 id="visao-geral">Visão geral</h1>
          </div>
          <span className={`api-status ${apiState}`}>
            <span aria-hidden="true" />
            {statusLabel}
          </span>
        </header>

        <section className="welcome-card">
          <div>
            <p className="eyebrow">SUPORTE ORGANIZADO</p>
            <h2>Gerencie solicitações de TI em um só lugar.</h2>
            <p>Abra, acompanhe e priorize chamados com histórico e responsáveis definidos.</p>
          </div>
          <button type="button" disabled title="Disponível na próxima entrega">
            Novo chamado
          </button>
        </section>

        <section className="metrics" aria-label="Indicadores">
          <article><span>Chamados abertos</span><strong>—</strong><small>Aguardando integração</small></article>
          <article><span>Em atendimento</span><strong>—</strong><small>Aguardando integração</small></article>
          <article><span>Resolvidos hoje</span><strong>—</strong><small>Aguardando integração</small></article>
        </section>

        <section className="tickets" id="chamados">
          <div className="section-heading">
            <div>
              <p className="eyebrow">ATIVIDADE</p>
              <h2>Chamados recentes</h2>
            </div>
          </div>
          <div className="empty-state">
            <span aria-hidden="true">✓</span>
            <h3>Nenhum chamado para exibir</h3>
            <p>Os chamados cadastrados pela API aparecerão aqui.</p>
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
