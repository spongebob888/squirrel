package dev.squirrel.vpn;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.test.InstrumentationTestCase;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

@SuppressWarnings("deprecation")
public final class LogFormatterInstrumentedTest extends InstrumentationTestCase {
    public void testPlainLogsHaveReadableSeverityHeadersAndTimestampsInBothThemes() {
        for (boolean dark : new boolean[]{false, true}) {
            DiagnosticsUi theme = theme(dark);
            Spanned text = new LogFormatter(theme).format("— core.log —\n"
                + "2026-10-19T08:42:03.123Z INFO connected\n"
                + "08:42:04 WARN retrying\nERROR disconnected\nDEBUG details\nTRACE trace\n"
                + "2026-10-19T08:42:05Z Service started\nMessage contains WARN without a level\n");
            assertEquals(theme.primary, foreground(text, "— core.log —"));
            assertEquals(theme.secondary, foreground(text, "2026-10-19T08:42:03.123Z"));
            assertEquals(theme.secondary, foreground(text, "2026-10-19T08:42:05Z"));
            assertEquals(theme.success, foreground(text, "INFO"));
            assertEquals(theme.context.getColor(R.color.log_warning), foreground(text, "WARN"));
            assertEquals(theme.error, foreground(text, "ERROR"));
            assertEquals(theme.secondary, foreground(text, "DEBUG"));
            assertEquals(theme.secondary, foreground(text, "TRACE"));
            for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class)) {
                assertTrue("Low contrast in " + (dark ? "dark" : "light") + " logs", contrast(span.getForegroundColor(), theme.panel) >= 4.5);
            }
            int message = text.toString().lastIndexOf("WARN");
            assertEquals(0, text.getSpans(message, message + 4, ForegroundColorSpan.class).length);
            assertEquals(Typeface.BOLD, style(text, "INFO"));
        }
    }

    public void testAnsiStylesResetIndependentlyAndCopyAsVisibleText() {
        LogFormatter formatter = new LogFormatter(theme(false));
        Spanned text = formatter.format("\u001B[1;3;4;9;31mStyled\u001B[22;23;24;29;39m plain\n"
            + "\u001B[1mBold\u001B[0m normal");
        assertEquals("Styled plain\nBold normal", text.toString());
        assertEquals(Typeface.BOLD_ITALIC, style(text, "Styled"));
        assertEquals(1, text.getSpans(0, 6, UnderlineSpan.class).length);
        assertEquals(1, text.getSpans(0, 6, StrikethroughSpan.class).length);
        assertEquals(0, text.getSpans(7, 12, StyleSpan.class).length);
        assertEquals(0, text.getSpans(7, 12, ForegroundColorSpan.class).length);
        assertEquals(Typeface.BOLD, style(text, "Bold"));
        int normal = text.toString().indexOf("normal");
        assertEquals(0, text.getSpans(normal, text.length(), StyleSpan.class).length);
    }

    public void testAnsiIndexedRgbBackgroundAndInverseRemainReadable() {
        for (boolean dark : new boolean[]{false, true}) {
            DiagnosticsUi theme = theme(dark);
            Spanned text = new LogFormatter(theme).format("\u001B[38;5;25mIndexed\u001B[0m "
                + "\u001B[38;2;51;102;153mRGB\u001B[0m "
                + "\u001B[38;2;255;255;255;48;2;255;255;255mContrast\u001B[0m "
                + "\u001B[7mInverse\u001B[27m normal");
            assertEquals("Indexed RGB Contrast Inverse normal", text.toString());
            assertTrue(contrast(foreground(text, "Indexed"), theme.panel) >= 4.5);
            for (String token : new String[]{"Contrast", "Inverse"}) {
                int start = text.toString().indexOf(token);
                BackgroundColorSpan[] backgrounds = text.getSpans(start, start + token.length(), BackgroundColorSpan.class);
                assertEquals(1, backgrounds.length);
                assertTrue(contrast(foreground(text, token), backgrounds[0].getBackgroundColor()) >= 4.5);
            }
            if (!dark) assertEquals(Color.rgb(51, 102, 153), foreground(text, "RGB"));
        }
    }

    public void testTerminalControlsMalformedCodesAndMarkupCannotHideLogContent() {
        Spanned text = new LogFormatter(theme(false)).format("\u001B]0;title\u0007Before\u001B[2J after "
            + "\u001B]8;;https://example.test\u001B\\link\u001B]8;;\u001B\\ "
            + "\u001B[38;2;999;0;0mvisible\u001B[0m \u001B[999999999999mstill visible\u001B[0m "
            + "<b>literal</b> **literal** \u001B[31");
        assertEquals("Before after link visible still visible <b>literal</b> **literal** [31", text.toString());
        assertEquals(0, text.getSpans(0, text.length(), StyleSpan.class).length);
    }

    private DiagnosticsUi theme(boolean dark) {
        DiagnosticsUi[] result = new DiagnosticsUi[1];
        getInstrumentation().runOnMainSync(() -> {
            android.content.Context context = getInstrumentation().getTargetContext();
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            result[0] = new DiagnosticsUi(context.createConfigurationContext(config), () -> {}, (tag, selector, member) -> {}, tag -> {});
        });
        return result[0];
    }

    private int foreground(Spanned text, String token) {
        int start = text.toString().indexOf(token);
        ForegroundColorSpan[] spans = text.getSpans(start, start + token.length(), ForegroundColorSpan.class);
        assertEquals(token, 1, spans.length);
        return spans[0].getForegroundColor();
    }

    private int style(Spanned text, String token) {
        int start = text.toString().indexOf(token), style = 0;
        for (StyleSpan span : text.getSpans(start, start + token.length(), StyleSpan.class)) style |= span.getStyle();
        return style;
    }

    private double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private double luminance(int color) {
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color));
    }

    private double channel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
