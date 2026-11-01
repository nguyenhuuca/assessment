import { useTranslation } from 'react-i18next'

/** Success banner for a bulk action: summary line plus the skipped reasons, built by buildBulkNotice. */
export default function BulkResultNotice({ notice, style }) {
  const { t } = useTranslation('admin')
  return (
    <div className="app-alert success" role="status" style={style}>
      {notice.summary}
      {notice.reasons.length > 0 && (
        <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
          {notice.reasons.map(r => (
            <li key={r.code}>{t('bulkSkippedLabel')}: {r.label} ({r.count})</li>
          ))}
        </ul>
      )}
    </div>
  )
}
