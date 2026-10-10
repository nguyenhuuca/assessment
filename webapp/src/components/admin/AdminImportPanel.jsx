import React from 'react'
import ImportForm from './ImportForm.jsx'
import ImportTable from './ImportTable.jsx'

export default function AdminImportPanel() {
  return (
    <div className="import-panel">
      <ImportForm />
      <ImportTable />
    </div>
  )
}
