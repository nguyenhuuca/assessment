import React from 'react'
import { useTranslation } from 'react-i18next'
import { TIME_PRESETS, toRangeParams } from '../../utils/timeRange.js'

const PRESET_LABEL = { '1h': 'timeLast1h', '12h': 'timeLast12h', '24h': 'timeLast24h', '7d': 'timeLast7d', '30d': 'timeLast30d' }

/** Select value <-> filter: presets are their key, plus 'all' and 'custom'. */
const toSelect = (f) => (f.mode === 'relative' ? f.preset : f.mode)
const fromSelect = (v) =>
  v === 'all' ? { mode: 'all' } : v === 'custom' ? { mode: 'custom', from: '', to: '' } : { mode: 'relative', preset: v }

/** Time range control for the Drive Sync file table; filtering happens on the server. */
export default function DriveTimeFilter({ value, onChange }) {
  const { t } = useTranslation('admin')
  const { error } = toRangeParams(value)

  return (
    <div className="drive-time-filter">
      <label>
        {t('timeFilter')}
        <select className="admin-status-select" value={toSelect(value)} onChange={e => onChange(fromSelect(e.target.value))}>
          {Object.keys(TIME_PRESETS).map(p => <option key={p} value={p}>{t(PRESET_LABEL[p])}</option>)}
          <option value="all">{t('timeAll')}</option>
          <option value="custom">{t('timeCustom')}</option>
        </select>
      </label>
      {value.mode === 'custom' && (
        <>
          <label>
            {t('timeFrom')}
            <input type="datetime-local" className="admin-status-select" value={value.from}
              onChange={e => onChange({ ...value, from: e.target.value })} />
          </label>
          <label>
            {t('timeTo')}
            <input type="datetime-local" className="admin-status-select" value={value.to}
              onChange={e => onChange({ ...value, to: e.target.value })} />
          </label>
        </>
      )}
      {error && <span className="app-alert error" role="alert">{t(error)}</span>}
    </div>
  )
}
