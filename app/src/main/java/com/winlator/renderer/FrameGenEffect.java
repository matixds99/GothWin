package com.winlator.renderer;

import android.opengl.GLES20;
import android.os.SystemClock;

import com.winlator.renderer.material.ScreenMaterial;
import com.winlator.renderer.material.ShaderMaterial;

/**
 * Prosty generator klatek: po każdej klatce gry wyświetla klatkę pośrednią (mieszankę poprzedniej
 * i bieżącej), a po połowie odstępu między klatkami - właściwą klatkę. Nie szacuje ruchu, więc
 * jakość jest niższa niż w LSFG, ale działa w rendererze OpenGL.
 */
public class FrameGenEffect {
    public static final int TYPE_BLEND = 0;
    public static final int TYPE_ADAPTIVE = 1;

    private final GLRenderer renderer;
    private volatile int type;
    private RenderTarget last;
    private boolean hasLast = false;
    private volatile boolean repeatRequested = false;
    private boolean repeatScheduled = false;
    private long lastSourceTime = 0;
    private float intervalMs = 16.0f;
    private final ScreenMaterial copyMaterial = new ScreenMaterial();
    private final ShaderMaterial.Uniform prevTexture = new ShaderMaterial.Uniform("prevTexture");
    private final ShaderMaterial.Uniform mixFactor = new ShaderMaterial.Uniform("mixFactor");
    private final ShaderMaterial.Uniform adaptive = new ShaderMaterial.Uniform("adaptive");
    private final ScreenMaterial blendMaterial = new ScreenMaterial() {
        @Override
        protected String getFragmentShader() {
            return String.join("\n",
                "precision highp float;",

                "uniform sampler2D screenTexture;",
                "uniform sampler2D prevTexture;",
                "uniform float mixFactor;",
                "uniform bool adaptive;",
                "in vec2 vUV;",

                "layout(location = 0) out vec4 outFragColor;",

                "void main() {",
                    "vec4 current = texture(screenTexture, vUV);",
                    "vec4 previous = texture(prevTexture, vUV);",
                    "float t = mixFactor;",
                    // przy dużej różnicy pikseli (szybki ruch) zostaje bieżąca klatka, co ogranicza duchy
                    "if (adaptive) t = mix(mixFactor, 1.0, smoothstep(0.1, 0.4, length(current.rgb - previous.rgb)));",
                    "outFragColor = mix(previous, current, t);",
                "}"
            );
        }
    };

    public FrameGenEffect(GLRenderer renderer, int type) {
        this.renderer = renderer;
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean consumeRepeat() {
        boolean value = repeatRequested;
        repeatRequested = false;
        return value;
    }

    private void draw(ScreenMaterial material, int texture, int previousTexture) {
        material.use();
        renderer.quadVertices.bind(material.programId);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        material.setUniformInt(material.uniforms.screenTexture, 0);

        if (previousTexture != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexture);
            material.setUniformInt(prevTexture, 1);
            material.setUniformFloat(mixFactor, 0.5f);
            material.setUniformBool(adaptive, type == TYPE_ADAPTIVE);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, renderer.quadVertices.count());

        if (previousTexture != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void bindTarget(int framebuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glViewport(0, 0, renderer.surfaceWidth, renderer.surfaceHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    /** Wywoływane w wątku GL z końcowym wynikiem sceny (po efektach). */
    public void render(int inputTexture, boolean repeatPass) {
        if (last == null) {
            last = new RenderTarget();
            last.allocateFramebuffer(renderer.surfaceWidth, renderer.surfaceHeight);
        }

        bindTarget(0);
        if (!repeatPass && hasLast) draw(blendMaterial, inputTexture, last.getTextureId());
        else draw(copyMaterial, inputTexture, 0);

        bindTarget(last.getFramebuffer());
        draw(copyMaterial, inputTexture, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        hasLast = true;
        renderer.viewportNeedsUpdate = true;

        if (!repeatPass) scheduleRepeat();
    }

    private void scheduleRepeat() {
        long now = SystemClock.elapsedRealtime();
        long delta = now - lastSourceTime;
        if (lastSourceTime != 0 && delta < 100) intervalMs = intervalMs * 0.8f + delta * 0.2f;
        lastSourceTime = now;

        if (repeatScheduled) return;
        repeatScheduled = true;
        long delay = (long)Math.max(2.0f, Math.min(40.0f, intervalMs * 0.5f));
        renderer.xServerView.postDelayed(() -> {
            repeatScheduled = false;
            repeatRequested = true;
            renderer.xServerView.requestRender();
        }, delay);
    }
}
