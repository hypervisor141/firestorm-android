package com.nurverek.firestorm;

import vanguard.VLArrayFloat;
import vanguard.VLListType;

public class FSLightPoint extends FSLight{

    public static final String[] STRUCT_MEMBERS = new String[]{
            "vec3 position",
            "Attenuation attenuation"
    };

    public static final String FUNCTION_LIGHT =
            "vec3 pointLight(PointLight light, Material material, vec3 normal, vec3 cameraPos, vec3 lightdir, float shadow, float attenuation){\n" +
                    "\treturn material.ambient * attenuation +\n" +
                    "\t       shadow * (material.diffuse * max(dot(normal, lightdir), 0.0) * attenuation +\n" +
                    "\t       material.specular * pow(max(dot(normal, normalize(lightdir + cameraPos)), 0.0), material.shininess) * attenuation);\n" +
                    "}";

    protected FSAttenuation attenuation;
    protected VLArrayFloat position;

    public FSLightPoint(FSAttenuation attenuation, VLArrayFloat position){
        this.attenuation = attenuation;
        this.position = position;

        update(new VLListType<>(new FSConfig[]{
                new FSP.Uniform3fvd(position, 0, 1),
                attenuation
        }, 0));
    }

    public FSAttenuation attenuation(){
        return attenuation;
    }

    @Override
    public VLArrayFloat position(){
        return position;
    }

    @Override
    public String[] getStructMembers(){
        return STRUCT_MEMBERS;
    }

    @Override
    public String getLightFunction(){
        return FUNCTION_LIGHT;
    }
}
