package com.nurverek.firestorm;

import android.opengl.GLES32;

import vanguard.VLArrayFloat;
import vanguard.VLFloat;
import vanguard.VLInt;
import vanguard.VLListType;

public final class FSShadowDirect extends FSShadow<FSLightDirect>{

    public static final String[] STRUCT_MEMBERS = new String[]{
            "float minbias",
            "float maxbias",
            "float divident",
            "mat4 lightvp"
    };

    public static final String FUNCTION_NORMAL =
            "float shadowMap(vec4 fragPosLightSpace, sampler2DShadow shadowmap, float bias, float divident){\n" +
                    "\t   vec3 coords = (fragPosLightSpace.xyz / fragPosLightSpace.w) * 0.5 + 0.5;\n" +
                    "\t   if(coords.z > 1.0){\n" +
                    "\t       return 0.0;\n" +
                    "\t   }\n" +
                    "\t   coords.z -= bias;\n" +
                    "\t   return texture(shadowmap, coords) / divident;\n" +
                    "}";

    public static final String FUNCTION_SOFT =
            "float shadowMap(vec4 fragPosLightSpace, sampler2DShadow shadowmap, float bias, float divident){\n" +
                    "\t   vec3 coords = (fragPosLightSpace.xyz / fragPosLightSpace.w) * 0.5 + 0.5;\n" +
                    "\t   if(coords.z > 1.0){\n" +
                    "\t       return 0.0;\n" +
                    "\t   }\n" +
                    "\t   coords.z -= bias;\n" +
                    "\t   float shadow = 0.0;\n" +
                    "\t   vec2 texelSize = 1.0 / vec2(textureSize(shadowmap, 0).xy);\n" +
                    "\t   for(int x = -1; x <= 1; x++){\n" +
                    "\t       for(int y = -1; y <= 1; y++){\n" +
                    "\t           shadow += texture(shadowmap, coords.xyz + vec3((vec2(x, y) * texelSize), 0.0)); \n" +
                    "\t       }\n" +
                    "\t   }\n" +
                    "\t   return shadow / divident;\n" +
                    "}";

    public static final int SELECT_STRUCT_DATA = 0;

    protected FSView config;

    public FSShadowDirect(FSLightDirect light, VLInt width, VLInt height, VLFloat minbias, VLFloat maxbias, VLFloat divident){
        super(1, 0, light, width, height, minbias, maxbias, divident);

        config = new FSView();
        config.setOrthographicMode();

        configs().add(new FSConfigSequence(new VLListType<>(new FSConfig[]{
                new FSP.Uniform1f(minbias),
                new FSP.Uniform1f(maxbias),
                new FSP.Uniform1f(divident),
                new FSP.UniformMatrix4fvd(lightViewProjection(), 0, 1)
        }, 0)));
    }

    @Override
    protected FSTexture initializeTexture(VLInt texunit, VLInt width, VLInt height){
        FSTexture texture = new FSTexture(new VLInt(GLES32.GL_TEXTURE_2D), texunit);
        texture.bind();
        texture.storage2D(1, GLES32.GL_DEPTH_COMPONENT32F, width.get(), height.get());
        texture.minFilter(GLES32.GL_NEAREST);
        texture.magFilter(GLES32.GL_NEAREST);
        texture.wrapS(GLES32.GL_CLAMP_TO_EDGE);
        texture.wrapT(GLES32.GL_CLAMP_TO_EDGE);
        texture.compareMode(GLES32.GL_COMPARE_REF_TO_TEXTURE);
        texture.compareFunc(GLES32.GL_LEQUAL);
        texture.unbind();

        return texture;
    }

    @Override
    protected FSFrameBuffer initializeFrameBuffer(FSTexture texture){
        FSFrameBuffer buffer = new FSFrameBuffer();
        buffer.initialize();
        buffer.bind();
        buffer.attachTexture2D(GLES32.GL_DEPTH_ATTACHMENT, texture.target().get(), texture.id(), 0);
        buffer.checkStatus();

        FSStatic.CACHE_INT[0] = GLES32.GL_NONE;

        GLES32.glReadBuffer(GLES32.GL_NONE);
        GLES32.glDrawBuffers(0, FSStatic.CACHE_INT, 0);

        buffer.unbind();

        return buffer;
    }

    public void updateLightProjection(float upX, float upY, float upZ, float left, float right,
                                      float bottom, float top, float znear, float zfar){
        float[] pos = light.position().provider();
        float[] cent = light.center().provider();

        config.lookAt(pos[0], pos[1], pos[2], cent[0], cent[1], cent[2], upX, upY, upZ);
        config.orthographic(left, right, bottom, top, znear, zfar);
        config.applyViewProjection();
    }

    public VLArrayFloat lightViewProjection(){
        return config.matrixViewProjection();
    }

    public FSView viewConfig(){
        return config;
    }

    @Override
    public String[] getStructMemebers(){
        return STRUCT_MEMBERS;
    }

    @Override
    public String getShadowFunction(){
        return FUNCTION_NORMAL;
    }

    @Override
    public String getSoftShadowFunction(){
        return FUNCTION_SOFT;
    }
}
