package carfinder.web.dto;

import carfinder.listing.Site;
import carfinder.scoring.Preset;
import carfinder.scoring.Weights;
import carfinder.search.SearchLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * customWeights is only read for the CUSTOM preset. marketplaceAutomated (absent = false): Marketplace also searches
 * the site itself on the secondary account.
 */
public record StartSearchRequest(@Valid @NotNull SearchLimits limits, @NotNull Preset preset, Weights customWeights,
		@NotEmpty Set<@NotNull Site> sites, Boolean marketplaceAutomated) {
}
