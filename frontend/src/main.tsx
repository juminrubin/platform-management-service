import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'

async function bootstrap() {
  const { initializeMsal } = await import('./auth/msalConfig')
  await initializeMsal()
  const { default: App } = await import('./App.tsx')

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

bootstrap().catch((err) => {
  console.error('Failed to start UI', err)
  const message = err instanceof Error ? err.message : String(err)
  document.body.innerHTML = `<pre style="padding:1rem;color:#b91c1c;white-space:pre-wrap">Failed to start UI:\n${message}</pre>`
})
