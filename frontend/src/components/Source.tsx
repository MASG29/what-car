import { isHttp } from '../api'

/** A link only for http(s) URLs; anything else (a report path) is plain text. */
export function Source({ url, label }: { url: string; label?: string }) {
  return isHttp(url) ? (
    <a href={url} target="_blank" rel="noreferrer noopener">
      {label ?? url}
    </a>
  ) : (
    <span>{label ? `${label}: ${url}` : url}</span>
  )
}
