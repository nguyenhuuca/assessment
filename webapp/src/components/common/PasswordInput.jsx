import React, { useId, useState } from 'react'

export default function PasswordInput({
  label,
  value,
  onChange,
  autoComplete = 'current-password',
  placeholder,
  required = false,
  id,
  ariaLabel,
  style,
}) {
  const autoId = useId()
  const inputId = id || autoId
  const [visible, setVisible] = useState(false)

  return (
    <div className="app-field" style={{ marginBottom: 0, ...style }}>
      {label && <label className="app-label" htmlFor={inputId}>{label}</label>}
      <div style={{ position: 'relative' }}>
        <input
          id={inputId}
          aria-label={label ? undefined : ariaLabel}
          className="app-input"
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={onChange}
          autoComplete={autoComplete}
          placeholder={placeholder}
          required={required}
          style={{ paddingRight: 40, width: '100%', boxSizing: 'border-box' }}
        />
        <button
          type="button"
          onClick={() => setVisible(v => !v)}
          aria-pressed={visible}
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
          style={{
            position: 'absolute', right: 6, top: '50%', transform: 'translateY(-50%)',
            background: 'none', border: 'none', cursor: 'pointer',
            color: 'var(--text-muted)', display: 'flex', alignItems: 'center', padding: 4,
          }}
        >
          <span className="material-symbols-outlined" style={{ fontSize: 18 }}>
            {visible ? 'visibility_off' : 'visibility'}
          </span>
        </button>
      </div>
    </div>
  )
}
