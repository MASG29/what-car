# Knowledge data

Every row cites its source in `source_url`, and `tag` records how the figure was verified
(`I` marks an inference, `agg` an aggregator site). No figure is estimated.

## consumption.csv

- Sources: the French ADEME/UTAC type-approval files (editions 2001 to 2012, published on
  data.gouv.fr) for most rows; Toyota's 2006 Yaris brochure for the two Yaris XP10 rows. All
  figures are combined NEDC values in l/100 km.
- ADEME rows: manual gearbox only. For each generation and engine, the earliest edition inside
  the generation's production window is used, and the value is the most common combined figure
  among that engine's rows. On a tie, the higher figure is kept, so the score never looks better
  than the source allows.
