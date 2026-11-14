import { importMarketplace } from '../api'
import type { LimitsForm } from '../limits'
import { marketplaceSearchUrl } from '../marketplace'
import { HarUpload } from './HarUpload'
import { HelpSteps } from './HelpSteps'

/** The user browses Marketplace and saves a HAR; the app only reads item pages of imported ads, logged out. */
export function MarketplacePanel({ form, disabled }: { form: LimitsForm; disabled: boolean }) {
  return (
    <div className="subpanel">
      <p>
        <a href={marketplaceSearchUrl(form)} target="_blank" rel="noreferrer noopener">
          Abrir pesquisa no Marketplace
        </a>
      </p>
      <HelpSteps />
      <HarUpload send={importMarketplace} disabled={disabled} />
    </div>
  )
}
