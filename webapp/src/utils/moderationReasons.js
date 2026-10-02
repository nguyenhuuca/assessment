export const REASONS = [
  { code: 'SPAM',          label: 'Spam, quảng cáo, link lặp lại' },
  { code: 'HARASSMENT',    label: 'Xúc phạm, bắt nạt, công kích cá nhân' },
  { code: 'HATE_SPEECH',   label: 'Thù ghét (chủng tộc, tôn giáo, giới tính…)' },
  { code: 'SEXUAL',        label: 'Nội dung tình dục / NSFW' },
  { code: 'VIOLENCE',      label: 'Đe dọa, kích động bạo lực' },
  { code: 'PERSONAL_INFO', label: 'Lộ thông tin cá nhân (SĐT, địa chỉ…)' },
  { code: 'OTHER',         label: 'Khác (cần ghi chú)' },
]

/** Vietnamese label for a moderation reason code; falls back to the raw code. */
export function reasonLabel(code) {
  return REASONS.find(r => r.code === code)?.label ?? code ?? ''
}
