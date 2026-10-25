package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Czyta aktualnie grana muzyke z systemowego odtwarzacza Windows (Spotify itp.)
 * przez PowerShell i pozwala nia sterowac (pauza, nastepny, poprzedni, przewijanie).
 * Pobiera tez okladke utworu.
 */
public final class MediaControl {
	private MediaControl() {
	}

	public static final Identifier COVER = Identifier.of("ghostvisual", "dynamic/cover");
	public static final int COVER_TEX = 84;

	public static volatile boolean available = false;
	public static volatile boolean playing = false;
	public static volatile String title = "";
	public static volatile String artist = "";
	public static volatile double position = 0;
	public static volatile double duration = 0;
	public static volatile long updatedAt = 0;
	/** true gdy okladka jest zaladowana jako tekstura COVER. */
	public static volatile boolean hasCover = false;

	private static int loadedCoverVersion = 0;
	private static Process poller;
	private static Thread reader;

	private static final String PREAMBLE = String.join("\n",
		"Add-Type -AssemblyName System.Runtime.WindowsRuntime",
		"$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
		"function Await($op, $type) { $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }",
		"[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
		"[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage.Streams,ContentType=WindowsRuntime]",
		"$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
		"function Pick { $s = $null; foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x; break } }; if ($s -eq $null) { $s = $mgr.GetCurrentSession() }; return $s }"
	);

	private static final String POLL = String.join("\n",
		"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
		"function B($v) { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$v)) }",
		"$cover = Join-Path $env:TEMP 'ghostvisual_cover.img'",
		"$ver = 0; $last = ''; $has = $false",
		"while ($true) {",
		"  try {",
		"    $s = Pick",
		"    if ($s -ne $null) {",
		"      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
		"      $i = $s.GetPlaybackInfo(); $tl = $s.GetTimelineProperties()",
		"      $key = [string]$p.Title + '|' + [string]$p.Artist",
		"      if ($key -ne $last) { $last = $key; $has = $false }",
		"      if (-not $has -and $p.Thumbnail -ne $null) {",
		"        try {",
		"          $st = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])",
		"          $ns = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($st)",
		"          $fs = [System.IO.File]::Create($cover); $ns.CopyTo($fs); $fs.Close(); $ns.Close()",
		"          $has = $true; $ver++",
		"        } catch { }",
		"      }",
		"      $cv = 0; if ($has) { $cv = $ver }",
		"      [Console]::Out.WriteLine('S|' + [int]$i.PlaybackStatus + '|' + (B $p.Title) + '|' + (B $p.Artist) + '|' + [int]$tl.Position.TotalSeconds + '|' + [int]$tl.EndTime.TotalSeconds + '|' + $cv)",
		"    } else { [Console]::Out.WriteLine('N') }",
		"  } catch { [Console]::Out.WriteLine('N') }",
		"  [Console]::Out.Flush()",
		"  Start-Sleep -Milliseconds 1000",
		"}"
	);

	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	public static void sync(boolean wanted) {
		if (wanted) {
			start();
		} else {
			stop();
			available = false;
		}
	}

	private static String encode(String s) {
		return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_16LE));
	}

	private static String decode(String s) {
		try {
			return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return "";
		}
	}

	private static ProcessBuilder shell(String script) {
		ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
			"-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encode(script));
		pb.redirectError(ProcessBuilder.Redirect.DISCARD);
		return pb;
	}

	public static synchronized void start() {
		if (!supported() || (poller != null && poller.isAlive())) {
			return;
		}
		try {
			poller = shell(PREAMBLE + "\n" + POLL).start();
			final Process p = poller;
			reader = new Thread(() -> readLoop(p), "ghostvisual-media");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception e) {
			GhostVisualClient.LOGGER.warn("Nie udalo sie uruchomic odczytu muzyki", e);
		}
	}

	public static synchronized void stop() {
		if (poller != null) {
			poller.destroy();
			poller = null;
		}
	}

	private static void readLoop(Process p) {
		try (var br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("S|")) {
					String[] a = line.split("\\|", -1);
					if (a.length >= 7) {
						title = decode(a[2]);
						artist = decode(a[3]);
						playing = "4".equals(a[1]);
						position = parse(a[4]);
						duration = parse(a[5]);
						updatedAt = System.currentTimeMillis();
						available = !title.isEmpty();
						int cv = (int) parse(a[6]);
						if (cv <= 0) {
							hasCover = false;
							loadedCoverVersion = 0;
						} else if (cv != loadedCoverVersion) {
							loadedCoverVersion = cv;
							loadCover();
						}
					}
				} else if (line.startsWith("N")) {
					available = false;
					playing = false;
					title = "";
					artist = "";
					hasCover = false;
					loadedCoverVersion = 0;
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static double parse(String s) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void loadCover() {
		try {
			String tmp = System.getenv("TEMP");
			if (tmp == null) {
				tmp = System.getProperty("java.io.tmpdir");
			}
			File f = new File(tmp, "ghostvisual_cover.img");
			BufferedImage src = ImageIO.read(f);
			if (src == null) {
				hasCover = false;
				return;
			}
			BufferedImage dst = new BufferedImage(COVER_TEX, COVER_TEX, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = dst.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			// kadrowanie do kwadratu ze srodka
			int side = Math.min(src.getWidth(), src.getHeight());
			int sx = (src.getWidth() - side) / 2;
			int sy = (src.getHeight() - side) / 2;
			g.drawImage(src, 0, 0, COVER_TEX, COVER_TEX, sx, sy, sx + side, sy + side, null);
			g.dispose();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(dst, "png", out);
			final byte[] png = out.toByteArray();
			MinecraftClient mc = MinecraftClient.getInstance();
			mc.execute(() -> {
				try (InputStream in = new ByteArrayInputStream(png)) {
					NativeImage img = NativeImage.read(in);
					mc.getTextureManager().registerTexture(COVER, new NativeImageBackedTexture(img));
					hasCover = true;
				} catch (Exception e) {
					hasCover = false;
				}
			});
		} catch (Exception e) {
			hasCover = false;
		}
	}

	public static double livePosition() {
		double pos = position;
		if (playing) {
			pos += (System.currentTimeMillis() - updatedAt) / 1000.0;
		}
		if (duration > 0) {
			pos = Math.min(pos, duration);
		}
		return Math.max(0, pos);
	}

	/** Komendy: toggle, next, prev, seek:SEKUNDY */
	public static void send(String cmd) {
		if (!supported()) {
			return;
		}
		if ("toggle".equals(cmd)) {
			position = livePosition();
			updatedAt = System.currentTimeMillis();
			playing = !playing;
		}
		Thread t = new Thread(() -> {
			try {
				String body;
				if (cmd.startsWith("seek:")) {
					long sec = (long) parse(cmd.substring(5));
					body = "if ($s -ne $null) { [void](Await ($s.TryChangePlaybackPositionAsync([long](" + sec + " * 10000000))) ([bool])) }";
				} else {
					String method = switch (cmd) {
						case "next" -> "TrySkipNextAsync";
						case "prev" -> "TrySkipPreviousAsync";
						default -> "TryTogglePlayPauseAsync";
					};
					body = "if ($s -ne $null) { [void](Await ($s." + method + "()) ([bool])) }";
				}
				Process pr = shell(PREAMBLE + "\n$s = Pick\n" + body).start();
				pr.getInputStream().readAllBytes();
				pr.waitFor();
			} catch (Exception e) {
				GhostVisualClient.LOGGER.warn("Nie udalo sie wyslac polecenia do odtwarzacza", e);
			}
		}, "ghostvisual-media-cmd");
		t.setDaemon(true);
		t.start();
	}

	public static void seek(double seconds) {
		if (duration <= 0) {
			return;
		}
		seconds = Math.max(0, Math.min(duration, seconds));
		position = seconds;
		updatedAt = System.currentTimeMillis();
		send("seek:" + (long) seconds);
	}
}
