package carfinder.geo;

import static carfinder.knowledge.Text.normalise;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Portuguese localities from the committed GeoNames file (CC BY 4.0). No network calls, ever. */
@Component
public class Places {

	private static final String RESOURCE = "/geo/places-pt.tsv";
	/** Same-name rows in one district this close are one place (a town and its municipality). */
	private static final double SAME_PLACE_KM = 10;
	private static final double EARTH_RADIUS_KM = 6371.0088;

	private record Row(Place place, String feature, long population) {
		boolean populated() {
			return feature.startsWith("PPL");
		}

		boolean capital() {
			return feature.equals("PPLA") || feature.equals("PPLC");
		}
	}

	private final Map<String, List<Row>> byName = new HashMap<>();
	private final Map<String, Place> capitals = new HashMap<>();
	/** Normalised district name to the name as written in the file. */
	private final Map<String, String> districts = new HashMap<>();

	@Autowired
	public Places() {
		try (InputStream in = Places.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing " + RESOURCE);
			}
			load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public Places(Path file) {
		try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
			load(lines);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Places loadClasspath() {
		return new Places();
	}

	private void load(Stream<String> lines) {
		Map<String, Row> bestCapital = new HashMap<>();
		lines.filter(l -> !l.isBlank() && !l.startsWith("#")).forEach(l -> {
			String[] c = l.split("\t");
			Row r = new Row(new Place(c[0], c[1], Double.parseDouble(c[2]), Double.parseDouble(c[3])), c[4],
					Long.parseLong(c[5]));
			byName.computeIfAbsent(normalise(c[0]), k -> new ArrayList<>()).add(r);
			districts.putIfAbsent(normalise(c[1]), c[1]);
			if (r.capital()) {
				bestCapital.merge(normalise(c[1]), r, (a, b) -> b.population() > a.population() ? b : a);
			}
		});
		bestCapital.forEach((district, r) -> capitals.put(district, r.place()));
	}

	/** The centre the user typed, "Name" or "Name, District". Anything but one clear place is a PT error. */
	public Place centre(String typed) {
		String t = typed == null ? "" : typed.trim();
		int comma = t.lastIndexOf(',');
		String name = comma < 0 ? t : t.substring(0, comma);
		String district = comma < 0 ? null : t.substring(comma + 1);
		Located l = lookup(name, district);
		return switch (l.precision()) {
			case EXACT -> l.place().orElseThrow();
			case AMBIGUOUS -> throw new IllegalArgumentException(
					"Localidade ambígua: " + name.trim() + ". Escreve \"" + name.trim() + ", Distrito\".");
			default -> throw new IllegalArgumentException("Localidade desconhecida: " + t);
		};
	}

	/** The district's own spelling when the text names one of the districts, in any case or accents. */
	public Optional<String> district(String name) {
		return name == null ? Optional.empty() : Optional.ofNullable(districts.get(normalise(name)));
	}

	/** Where an ad is: its city if found once, else the district capital, else nothing. Never guesses. */
	public Located resolve(String city, String district) {
		Located l = lookup(city, district);
		if (l.precision() != Precision.UNKNOWN) {
			return l;
		}
		Place capital = district == null ? null : capitals.get(normalise(district));
		return capital == null ? l : new Located(Optional.of(capital), Precision.DISTRICT_ONLY);
	}

	private Located lookup(String city, String district) {
		if (city == null || city.isBlank()) {
			return new Located(Optional.empty(), Precision.UNKNOWN);
		}
		List<Place> found = distinctPlaces(byName.getOrDefault(normalise(city), List.of()));
		if (found.isEmpty()) {
			return new Located(Optional.empty(), Precision.UNKNOWN);
		}
		if (found.size() > 1 && district != null && !district.isBlank()) {
			found = found.stream().filter(p -> normalise(p.district()).equals(normalise(district))).toList();
		}
		return found.size() == 1 ? new Located(Optional.of(found.get(0)), Precision.EXACT)
				: new Located(Optional.empty(), Precision.AMBIGUOUS);
	}

	/** One place per cluster of same-district rows within 10 km; a populated-place row represents the cluster. */
	private static List<Place> distinctPlaces(List<Row> rows) {
		List<List<Row>> clusters = new ArrayList<>();
		for (Row r : rows) {
			List<Row> home = clusters.stream().filter(c -> c.stream().anyMatch(o -> o.place().district()
					.equals(r.place().district()) && distanceKm(o.place(), r.place()) <= SAME_PLACE_KM)).findFirst()
					.orElse(null);
			if (home == null) {
				clusters.add(new ArrayList<>(List.of(r)));
			}
			else {
				home.add(r);
			}
		}
		return clusters.stream().map(c -> c.stream().min(Comparator.comparing((Row r) -> !r.populated())).orElseThrow()
				.place()).toList();
	}

	/** Haversine, straight line. */
	public static double distanceKm(Place a, Place b) {
		double dLat = Math.toRadians(b.lat() - a.lat());
		double dLon = Math.toRadians(b.lon() - a.lon());
		double h = Math.pow(Math.sin(dLat / 2), 2)
				+ Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLon / 2), 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
	}
}
