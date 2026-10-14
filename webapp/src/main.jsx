import React from 'react'
import ReactDOM from 'react-dom/client'
import './i18n'
import App from './App.jsx'
import 'bootstrap/dist/css/bootstrap.min.css'
import './styles/index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
