package carfinder.web.dto;

import carfinder.scoring.Preset;
import carfinder.scoring.Weights;
import carfinder.search.SearchLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** limits and preset are optional: without limits the ad is scored without the user's caps; preset defaults to BALANCED. */
public record ScoreLinkRequest(@NotBlank String url, @Valid SearchLimits limits, Preset preset, Weights customWeights) {
}
