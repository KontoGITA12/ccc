package com.ghostvisual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tekst piosenki z lrclib.net (darmowe API), dopasowany czasowo do aktualnej pozycji. */
public final class Lyrics {
	private Lyrics() {
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
	private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

	private static volatile List<double[]> times = new ArrayList<>();
	private static volatile List<String> lines = new ArrayList<>();
	private static volatile boolean synced = false;
	private static String key = "";
	public static volatile String state = "";

	public static void update(String title, String artist, double duration) {
		String k = title + "|" + artist;
		if (k.equals(key)) {
			return;
		}
		key = k;
		times = new ArrayList<>();
		lines = new ArrayList<>();
		state = "Szukam tekstu...";
		Thread t = new Thread(() -> load(title, artist, duration), "GhostVisual-Lyrics");
		t.setDaemon(true);
		t.start();
	}

	private static String clean(String s) {
		return s.replaceAll("\\(.*?\\)|\\[.*?]", "").replace(" - Topic", "").replaceAll("(?i)official.*|lyrics?|video", "").trim();
	}

	private static String q(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static JsonObject get(String url) throws Exception {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
			.header("User-Agent", "GhostVisual/1.2 (Fabric mod)").build(), HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			return null;
		}
		JsonElement e = JsonParser.parseString(r.body());
		if (e.isJsonArray()) {
			JsonArray a = e.getAsJsonArray();
			for (JsonElement x : a) {
				JsonObject o = x.getAsJsonObject();
				if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
					return o;
				}
			}
			return a.size() > 0 ? a.get(0).getAsJsonObject() : null;
		}
		return e.getAsJsonObject();
	}

	private static void load(String title, String artist, double duration) {
		try {
			String ct = clean(title), ca = clean(artist);
			JsonObject o = null;
			try {
				o = get("https://lrclib.net/api/get?track_name=" + q(ct) + "&artist_name=" + q(ca)
					+ (duration > 0 ? "&duration=" + Math.round(duration) : ""));
			} catch (Exception ignored) {
			}
			if (o == null) {
				o = get("https://lrclib.net/api/search?q=" + q(ca + " " + ct));
			}
			if (o == null) {
				state = "Brak tekstu";
				return;
			}
			String syn = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() ? o.get("syncedLyrics").getAsString() : "";
			String plain = o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull() ? o.get("plainLyrics").getAsString() : "";
			List<double[]> tm = new ArrayList<>();
			List<String> ln = new ArrayList<>();
			if (!syn.isBlank()) {
				for (String row : syn.split("\n")) {
					Matcher m = LRC.matcher(row);
					if (m.find()) {
						double ts = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
						if (m.group(3) != null) {
							ts += Double.parseDouble("0." + m.group(3));
						}
						tm.add(new double[]{ts});
						ln.add(row.substring(m.end()).trim());
					}
				}
				synced = true;
			} else if (!plain.isBlank()) {
				for (String row : plain.split("\n")) {
					if (!row.isBlank()) {
						ln.add(row.trim());
						tm.add(new double[]{0});
					}
				}
				synced = false;
			}
			if (ln.isEmpty()) {
				state = "Brak tekstu";
				return;
			}
			times = tm;
			lines = ln;
			state = "";
		} catch (Exception e) {
			state = "Brak tekstu";
		}
	}

	/** Indeks aktualnej linii albo -1. */
	public static int index(double pos, double duration) {
		List<String> l = lines;
		if (l.isEmpty()) {
			return -1;
		}
		if (!synced) {
			if (duration <= 0) {
				return 0;
			}
			return (int) Math.min(l.size() - 1, pos / duration * l.size());
		}
		List<double[]> t = times;
		int idx = -1;
		for (int i = 0; i < t.size() && i < l.size(); i++) {
			if (t.get(i)[0] <= pos + 0.15) {
				idx = i;
			} else {
				break;
			}
		}
		return idx;
	}

	public static String line(int i) {
		List<String> l = lines;
		return i >= 0 && i < l.size() ? l.get(i) : "";
	}
}
