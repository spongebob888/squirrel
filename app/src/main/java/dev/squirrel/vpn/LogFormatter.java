package dev.squirrel.vpn;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native log presentation. The original log files and exported text stay untouched. */
final class LogFormatter {
    private static final Pattern ESCAPE = Pattern.compile(
        "\u001B(?:\\[([0-9;]*)m|\\[[0-?]*[ -/]*[@-~]|\\][^\u0007\u001B\\r\\n]*(?:\u0007|\u001B\\\\))");
    private static final Pattern HEADER = Pattern.compile("(?m)^— .+ —$");
    private static final String TIME = "(?:\\d{4}-\\d{2}-\\d{2}[T ])?\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?";
    private static final Pattern TIMESTAMP = Pattern.compile("(?m)^[ \\t]*(" + TIME + ")[ \\t]+");
    private static final Pattern PREFIX = Pattern.compile(
        "(?m)^(?:[ \\t]*(" + TIME + ")[ \\t]+)?[ \\t]*\\[?(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    private final DiagnosticsUi theme;
    private final int warning;
    private final int[] palette;

    LogFormatter(DiagnosticsUi theme) {
        this.theme = theme;
        warning = theme.context.getColor(R.color.log_warning);
        palette = new int[]{theme.ink, theme.error, theme.success, warning, theme.primary,
            theme.context.getColor(R.color.log_magenta), theme.download, theme.ink};
    }

    Spanned format(String raw) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        State state = new State();
        Matcher escape = ESCAPE.matcher(raw);
        int previous = 0;
        while (escape.find()) {
            append(text, raw.substring(previous, escape.start()), state);
            if (escape.group(1) != null) state.update(escape.group(1));
            previous = escape.end();
        }
        append(text, raw.substring(previous), state);
        Matcher header = HEADER.matcher(text);
        while (header.find()) {
            foreground(text, header.start(), header.end(), theme.primary);
            span(text, new StyleSpan(Typeface.BOLD), header.start(), header.end());
        }
        Matcher timestamp = TIMESTAMP.matcher(text);
        while (timestamp.find()) foreground(text, timestamp.start(1), timestamp.end(1), theme.secondary);
        Matcher prefix = PREFIX.matcher(text);
        while (prefix.find()) {
            String level = prefix.group(2);
            int color = level.equals("ERROR") || level.equals("FATAL") ? theme.error
                : level.startsWith("WARN") ? warning : level.equals("INFO") ? theme.success : theme.secondary;
            foreground(text, prefix.start(2), prefix.end(2), color);
            span(text, new StyleSpan(Typeface.BOLD), prefix.start(2), prefix.end(2));
        }
        return text;
    }

    private void append(SpannableStringBuilder text, String raw, State state) {
        int start = text.length();
        // Incomplete/unsupported escapes cannot execute terminal operations in the viewer.
        text.append(raw.replace("\u001B", ""));
        int end = text.length();
        if (end == start) return;
        int foreground = state.foreground == null ? theme.ink : state.foreground;
        int background = state.background == null ? theme.panel : state.background;
        if (state.inverse) {
            int swap = foreground;
            foreground = background;
            background = swap;
        }
        if (state.dim) foreground = mix(foreground, background, 0.35);
        if (state.foreground != null || state.background != null || state.inverse || state.dim) {
            span(text, new ForegroundColorSpan(readable(foreground, background)), start, end);
        }
        if (state.background != null || state.inverse) span(text, new BackgroundColorSpan(background), start, end);
        int style = (state.bold ? Typeface.BOLD : 0) | (state.italic ? Typeface.ITALIC : 0);
        if (style != 0) span(text, new StyleSpan(style), start, end);
        if (state.underline) span(text, new UnderlineSpan(), start, end);
        if (state.strike) span(text, new StrikethroughSpan(), start, end);
    }

    private void foreground(SpannableStringBuilder text, int start, int end, int color) {
        // Explicit ANSI styling takes precedence over inferred severity/timestamp colors.
        if (text.getSpans(start, end, ForegroundColorSpan.class).length == 0) {
            span(text, new ForegroundColorSpan(color), start, end);
        }
    }

    private static void span(Spannable text, Object span, int start, int end) {
        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private final class State {
        Integer foreground, background;
        boolean bold, italic, underline, strike, dim, inverse;

        void update(String sequence) {
            String[] codes = sequence.split(";", -1);
            for (int i = 0; i < codes.length; i++) {
                int code = number(codes[i]);
                switch (code) {
                    case 0: foreground = background = null;
                        bold = italic = underline = strike = dim = inverse = false; break;
                    case 1: bold = true; break;
                    case 2: dim = true; break;
                    case 3: italic = true; break;
                    case 4: underline = true; break;
                    case 7: inverse = true; break;
                    case 9: strike = true; break;
                    case 22: bold = dim = false; break;
                    case 23: italic = false; break;
                    case 24: underline = false; break;
                    case 27: inverse = false; break;
                    case 29: strike = false; break;
                    case 39: foreground = null; break;
                    case 49: background = null; break;
                    case 38: case 48:
                        if (i + 1 >= codes.length) break;
                        int mode = number(codes[++i]);
                        int required = mode == 5 ? 1 : mode == 2 ? 3 : 0;
                        if (required == 0) break;
                        if (i + required >= codes.length) return;
                        int first = number(codes[++i]);
                        Integer color;
                        if (mode == 5) color = first >= 0 && first <= 255 ? indexed(first) : null;
                        else {
                            int green = number(codes[++i]), blue = number(codes[++i]);
                            color = channel(first) && channel(green) && channel(blue) ? Color.rgb(first, green, blue) : null;
                        }
                        if (color != null) {
                            if (code == 38) foreground = color;
                            else background = color;
                        }
                        break;
                    default:
                        if (code >= 30 && code <= 37) foreground = palette[code - 30];
                        else if (code >= 90 && code <= 97) foreground = palette[code - 90];
                        else if (code >= 40 && code <= 47) background = palette[code - 40];
                        else if (code >= 100 && code <= 107) background = palette[code - 100];
                }
            }
        }
    }

    private int indexed(int index) {
        if (index < 16) return palette[index % 8];
        if (index >= 232) {
            int gray = 8 + (index - 232) * 10;
            return Color.rgb(gray, gray, gray);
        }
        int value = index - 16;
        return Color.rgb(cube(value / 36), cube(value / 6 % 6), cube(value % 6));
    }

    private static int cube(int value) { return value == 0 ? 0 : 55 + value * 40; }
    private static boolean channel(int value) { return value >= 0 && value <= 255; }
    private static int number(String value) {
        try { return value.isEmpty() ? 0 : Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static int readable(int color, int background) {
        double luminance = luminance(background);
        int target = contrast(Color.BLACK, luminance) > contrast(Color.WHITE, luminance) ? Color.BLACK : Color.WHITE;
        for (int step = 0; step <= 20; step++) {
            int adjusted = mix(color, target, step / 20.0);
            if (contrast(adjusted, luminance) >= 4.5) return adjusted;
        }
        return target;
    }

    private static double contrast(int foreground, double background) {
        double value = luminance(foreground);
        return (Math.max(value, background) + 0.05) / (Math.min(value, background) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color));
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int mix(int from, int to, double amount) {
        return Color.rgb((int) Math.round(Color.red(from) * (1 - amount) + Color.red(to) * amount),
            (int) Math.round(Color.green(from) * (1 - amount) + Color.green(to) * amount),
            (int) Math.round(Color.blue(from) * (1 - amount) + Color.blue(to) * amount));
    }
}
