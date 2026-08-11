package com.dracave.title.render;
import com.dracave.title.model.TitleAnimation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.List;
public final class FlowingGradientRenderer {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private FlowingGradientRenderer() {
    }
    public static String render(String display, List<String> colors, double cycle,
                                TitleAnimation.GradientMode mode, int charStep) {
        if (colors.size() < 2) {
            return display;
        }
        int[] points = display.codePoints().toArray();
        int n = points.length;
        int step = Math.max(1, charStep);
        double timePhase = GradientColor.phaseForCycle(cycle, mode);
        StringBuilder result = new StringBuilder(n * 11);
        String last = null;
        for (int i = 0; i < n; i++) {
            double pos = (double) (i - i % step) / (double) n;
            String color = GradientColor.lerpClosed(colors, (pos + timePhase) % 1.0);
            if (!color.equals(last)) {
                result.append('<').append(color).append('>');
                last = color;
            }
            result.append(MINI.escapeTags(new String(points, i, 1)));
        }
        return result.toString();
    }
}
