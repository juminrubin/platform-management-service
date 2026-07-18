import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { msalInstance } from './auth/msalConfig'
import App from './App.tsx'
import './index.css'

async function bootstrap() {
  await msalInstance.initialize()
  await msalInstance.handleRedirectPromise()

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

bootstrap().catch((err) => {
  console.error('Failed to start UI', err)
  document.body.innerHTML = `<pre style="padding:1rem;color:#b91c1c">Failed to start UI: ${String(err)}</pre>`
})
