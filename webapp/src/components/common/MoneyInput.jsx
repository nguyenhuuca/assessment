import React from 'react'
import { formatGrouped, toDigits } from '../../utils/moneyInput.js'

/** Text input that shows a VND amount grouped (1.234.567) and reports the plain digit string via onChange. */
export default function MoneyInput({ value, onChange, ...rest }) {
  return (
    <input
      {...rest}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      value={formatGrouped(value)}
      onChange={e => onChange(toDigits(e.target.value))}
    />
  )
}
