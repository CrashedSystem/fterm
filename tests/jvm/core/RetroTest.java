/**
 * The shader must stay a port of WT's Retro.hlsl, not a thing that "looks retro".
 *
 * The first version of this effect failed exactly here: it added a bright scanline instead of
 * multiplying a row down, and it drew a vignette WT does not have. Neither was caught by
 * looking at a screenshot, because both looked plausible. These assertions pin the three
 * numbers WT hardcodes and the order the two operations run in, which is the part a future
 * "cleanup" is most likely to quietly change.
 */
public class RetroTest {

    static int pass = 0;

    static void check(boolean c, String what) {
        if (!c) throw new AssertionError(what);
        pass++;
    }

    public static void main(String[] args) throws Exception {
        final String src = io.github.crashedsystem.terminal.RetroShader.SOURCE;

        // 1. WT's two constants, unchanged.
        check(io.github.crashedsystem.terminal.RetroShader.SCANLINE_FACTOR == 0.5f,
                "SCANLINE_FACTOR is WT's 0.5");
        // Deliberately below WT's 0.3: see the comment on the constant. The test pins the
        // value that was actually chosen on device so it cannot drift back silently.
        check(io.github.crashedsystem.terminal.RetroShader.GLOW_STRENGTH == 0.1f,
                "glow is the tuned-down 0.1, not WT's 0.3");

        // 2. Both strengths reach the shader as uniforms, not as baked-in constants.
        //    A background picture needs a different depth for the same effect (see the
        //    ON_IMAGE constants), so the values are uniform inputs and one compiled shader
        //    serves both cases. A constant nobody uses is a lie; so is one that cannot vary.
        check(src.contains("uniform float scanlineFactor"), "the scanline depth is a uniform");
        check(src.contains("uniform float glowStrength"), "the glow strength is a uniform");
        check(io.github.crashedsystem.terminal.RetroShader.SCANLINE_FACTOR_ON_IMAGE == 0.16f,
                "over a picture the scanline drops to a third of WT's depth");
        check(io.github.crashedsystem.terminal.RetroShader.GLOW_STRENGTH_ON_IMAGE == 0.05f,
                "and the glow halves, because a bloom over a photo reads as haze");

        // 3. The glow is ADDED to the frame - that is what makes it a glow.
        check(src.contains("color += (sum / weightSum) * glowStrength"),
                "the blurred frame is added, not mixed in");

        // 4. The scanline is WT's expression: every other scale-row multiplied down.
        check(src.contains("1.0 - mod(floor(fragCoord.y / scale), 2.0) * scanlineFactor"),
                "the scanline is WT's multiply, not an added bright line");
        check(!src.contains("+ wave") && !src.contains("color +=") || !src.contains("wave *"),
                "no additive scanline branch");

        // 5. Order: glow first, scanline second. WT does the multiply on the glowing frame,
        //    so reversing them halves the glow as well.
        int glow = src.indexOf("glowStrength");
        int scan = src.indexOf("float wave");
        check(glow > 0 && scan > glow, "the glow is applied before the scanline multiply");

        // 6. Gaussian, not a box blur - the falloff is the whole look.
        check(src.contains("exp(-0.5 * dot(offset, offset) / (sigma * sigma))"),
                "the blur kernel is gaussian");
        check(src.contains("sigma"), "the gaussian is parameterised by sigma");

        // 7. No vignette. WT's shader has none; adding one is the mistake this test exists for.
        check(!src.toLowerCase().contains("vignette"), "no vignette");
        check(!src.contains("RadialGradient"), "no radial falloff");

        // 8. Alpha is forced opaque: a shader that returns the input alpha can make the whole
        //    terminal ghost through wherever the background was transparent.
        check(src.contains("return half4(clamp(color.rgb, 0.0, 1.0), 1.0)"),
                "the result is clamped opaque");

        // 9. The scanline period is the display density, not the cell height. A half-cell
        //    period (22 px on this phone) is a stripe you can read from across the room.
        String view = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("view.src"))), "UTF-8");
        check(view.contains("getResources().getDisplayMetrics().density"),
                "Retro's \"scale\" is the UI scale, so the period is the display density");
        check(!view.contains("mFontLineSpacing / 2f"),
                "the cell height is not used as the scanline period");

        System.out.println("ok  " + pass + " retro assertions");
        System.out.println("ALL OK");
    }
}
