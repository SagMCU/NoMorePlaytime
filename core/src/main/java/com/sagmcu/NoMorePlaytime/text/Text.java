package com.sagmcu.NoMorePlaytime.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat text parsed from "&amp;" codes: &amp;0-9 a-f colors, &amp;#RRGGBB hex colors, &amp;k l m n o formats and &amp;r reset.
 * Kept free of Minecraft types; the platform adapter turns the segments into components.
 */
public final class Text {
	public record Segment(String text, int rgb, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
						  boolean obfuscated) {
	}

	private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	private final List<Segment> segments;

	private Text(List<Segment> segments) {
		this.segments = segments;
	}

	public List<Segment> segments() {
		return segments;
	}

	public static Text parse(String raw) {
		List<Segment> out = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();
		int rgb = -1;
		boolean bold = false, italic = false, underlined = false, strikethrough = false, obfuscated = false;

		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '&' && i + 1 < raw.length()) {
				char next = Character.toLowerCase(raw.charAt(i + 1));
				boolean consumed = true;
				int skip = 1;

				if (next == '#' && isHex(raw, i + 2)) {
					flush(out, buffer, rgb, bold, italic, underlined, strikethrough, obfuscated);
					rgb = Integer.parseInt(raw.substring(i + 2, i + 8), 16);
					bold = italic = underlined = strikethrough = obfuscated = false;
					skip = 7;
				} else if (Character.digit(next, 16) >= 0) {
					flush(out, buffer, rgb, bold, italic, underlined, strikethrough, obfuscated);
					rgb = COLORS[Character.digit(next, 16)];
					bold = italic = underlined = strikethrough = obfuscated = false;
				} else if ("klmnor".indexOf(next) >= 0) {
					flush(out, buffer, rgb, bold, italic, underlined, strikethrough, obfuscated);
					switch (next) {
						case 'k' -> obfuscated = true;
						case 'l' -> bold = true;
						case 'm' -> strikethrough = true;
						case 'n' -> underlined = true;
						case 'o' -> italic = true;
						default -> {
							rgb = -1;
							bold = italic = underlined = strikethrough = obfuscated = false;
						}
					}
				} else {
					consumed = false;
				}

				if (consumed) {
					i += skip;
					continue;
				}
			}
			buffer.append(c);
		}
		flush(out, buffer, rgb, bold, italic, underlined, strikethrough, obfuscated);
		return new Text(out);
	}

	private static void flush(List<Segment> out, StringBuilder buffer, int rgb, boolean bold, boolean italic,
							  boolean underlined, boolean strikethrough, boolean obfuscated) {
		if (buffer.isEmpty())
			return;
		out.add(new Segment(buffer.toString(), rgb, bold, italic, underlined, strikethrough, obfuscated));
		buffer.setLength(0);
	}

	private static boolean isHex(String s, int from) {
		if (from + 6 > s.length())
			return false;
		for (int i = from; i < from + 6; i++) {
			if (Character.digit(s.charAt(i), 16) < 0)
				return false;
		}
		return true;
	}
}
