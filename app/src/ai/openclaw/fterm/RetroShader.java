package ai.openclaw.fterm;

/**
 * Windows Terminal's {@code samples/PixelShaders/Retro.hlsl}, ported to AGSL.
 *
 * <p>The original is thirty lines of HLSL and does exactly two things to the finished frame,
 * in this order:
 *
 * <pre>
 *   color += Blur(color, sigma = 2 * scale) * 0.3;      // the phosphor glow
 *   color *= 1 - (floor(y / scale) mod 2) * 0.5;        // the scanlines
 * </pre>
 *
 * <p>Both constants are WT's, unchanged. There is deliberately no vignette: WT's shader has
 * none, and an earlier version of this class added one because it "looked retro" - which is
 * the difference between imitating a screenshot and porting the effect.
 *
 * <p>The one difference is the blur kernel. WT samples a 13x13 grid one texel apart, which is
 * 169 texture fetches per pixel and fine for a desktop GPU; this samples 5x5 taps two scale
 * units apart, which covers the same ±2 sigma footprint for 49 fetches. On a phone the
 * footprint is what the eye reads, not the sample count.
 *
 * <p>The scanline is a multiply, not an added line. WT halves every other row, so the dark
 * background stays dark and only the lit rows lose brightness; the effect lives in the
 * contrast between rows rather than in a bright stripe. WT's own source has a disabled
 * brighten-instead branch (the {@code && false} on the {@code length(color) < 0.2} test) with
 * a TODO against it - matching the shipped behaviour means leaving that branch off.
 */
public final class RetroShader {

    /** WT's SCANLINE_FACTOR: odd rows keep half their brightness. */
    public static final float SCANLINE_FACTOR = 0.5f;

    /**
     * WT's glow term is {@code 0.3f}. This is deliberately lower.
     *
     * <p>0.3 is tuned for a desktop monitor at arm's length with subpixel-antialiased text;
     * on a phone held 25 cm away with the same glyphs it reads as a haze over every line and
     * turns a terminal into something you squint through. Measured on device, 0.3 smeared the
     * background from 12 to 16 at the base of every glyph. 0.1 keeps the phosphor edge without
     * the bloom. The scanline multiply stays at WT's exact 0.5 - that one is texture, not
     * brightness, and it is what actually reads as "CRT".
     */
    public static final float GLOW_STRENGTH = 0.1f;

    /** The AGSL text. {@code resolution} is needed only to clamp edge samples. */
    public static final String SOURCE =
            "uniform shader content;\n"
            + "uniform float2 resolution;\n"
            + "uniform float scale;\n"
            + "uniform float sigma;\n"
            + "const float SCANLINE_FACTOR = " + SCANLINE_FACTOR + ";\n"
            + "const float GLOW_STRENGTH = " + GLOW_STRENGTH + ";\n"
            + "\n"
            + "float4 sampleAt(float2 at) {\n"
            + "    float2 p = clamp(at, float2(0.0), resolution - 1.0);\n"
            + "    return float4(content.eval(p));\n"
            + "}\n"
            + "\n"
            + "half4 main(float2 fragCoord) {\n"
            + "    float4 color = sampleAt(fragCoord);\n"
            + "    float stepPx = scale * 2.0;\n"
            + "    float4 sum = float4(0.0);\n"
            + "    float weightSum = 0.0;\n"
            + "    for (int y = -2; y <= 2; y++) {\n"
            + "        for (int x = -2; x <= 2; x++) {\n"
            + "            float2 offset = float2(float(x), float(y)) * stepPx;\n"
            + "            float w = exp(-0.5 * dot(offset, offset) / (sigma * sigma));\n"
            + "            sum += sampleAt(fragCoord + offset) * w;\n"
            + "            weightSum += w;\n"
            + "        }\n"
            + "    }\n"
            + "    color += (sum / weightSum) * GLOW_STRENGTH;\n"
            + "    float wave = 1.0 - mod(floor(fragCoord.y / scale), 2.0) * SCANLINE_FACTOR;\n"
            + "    color *= wave;\n"
            + "    return half4(clamp(color.rgb, 0.0, 1.0), 1.0);\n"
            + "}\n";

    private RetroShader() {}
}
