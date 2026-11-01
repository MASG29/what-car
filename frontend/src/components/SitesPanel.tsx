/**
 * Standvirtual is the only active site. Marketplace is paused (no toggle); when it returns, switching it on
 * must open an in-page confirmation naming the account risk.
 */
export function SitesPanel() {
  return (
    <section className="panel">
      <h2>Sites</h2>
      <label className="inline">
        <input type="checkbox" checked disabled />
        Standvirtual
      </label>
      <p className="inline muted">
        Marketplace — <strong>Em pausa</strong>
      </p>
    </section>
  )
}
