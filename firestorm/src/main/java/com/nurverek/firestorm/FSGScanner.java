package com.nurverek.firestorm;

import vanguard.VLArrayFloat;
import vanguard.VLArrayShort;
import vanguard.VLDebug;

public abstract class FSGScanner{

    protected FSGAssembler assembler;
    protected FSBufferLayout layout;
    protected FSP program;
    protected FSMesh mesh;
    protected String name;

    protected FSGScanner(FSP program, FSBufferLayout layout, FSGAssembler assembler, String name){
        this.program = program;
        this.layout = layout;
        this.assembler = assembler;
        this.name = name.toLowerCase();

        mesh = new FSMesh();
    }

    protected abstract boolean scan(FSGAutomator automator, FSM.Data data);

    protected void bufferAndFinish(){
        layout.buffer(mesh);
        program.meshes().add(mesh);
    }

    protected void bufferDebugAndFinish(){
        layout.bufferDebug(mesh);
        program.meshes().add(mesh);
    }

    protected void debugInfo(){
        VLDebug.append("[");
        VLDebug.append(getClass().getSimpleName());
        VLDebug.append("] ");
        VLDebug.append("mesh[" + mesh.name + "] ");

        int size = mesh.size();
        VLArrayFloat[] data;
        VLArrayFloat array;
        int[] requirements = new int[FSG.ELEMENT_TOTAL_COUNT];

        if(mesh.indices != null){
            requirements[FSG.ELEMENT_INDEX] = mesh.indices.size();
        }

        for(int i = 0; i < size; i++){
            data = mesh.instance(i).data.elements;

            for(int i2 = 0; i2 < data.length; i2++){
                array = data[i2];

                if(array != null){
                    requirements[i2] += array.size();
                }
            }
        }

        VLDebug.append("storageRequirements[");

        if(assembler.INSTANCE_SHARE_POSITIONS){
            requirements[FSG.ELEMENT_POSITION] /= size;
        }
        if(assembler.INSTANCE_SHARE_COLORS){
            requirements[FSG.ELEMENT_COLOR] /= size;
        }
        if(assembler.INSTANCE_SHARE_TEXCOORDS){
            requirements[FSG.ELEMENT_TEXCOORD] /= size;
        }
        if(assembler.INSTANCE_SHARE_NORMALS){
            requirements[FSG.ELEMENT_NORMAL] /= size;
        }

        size = FSG.ELEMENT_NAMES.length;

        for(int i = 0; i < size; i++){
            VLDebug.append(FSG.ELEMENT_NAMES[i]);
            VLDebug.append("[");
            VLDebug.append(requirements[i]);

            if(i < size - 1){
                VLDebug.append("] ");
            }
        }

        VLDebug.append("]]\n");
    }

    public static class Singular extends FSGScanner{

        public Singular(FSP program, FSBufferLayout layout, FSGAssembler assembler, String name, int drawmode){
            super(program, layout, assembler, name);
            mesh.initialize(drawmode, 1, 0);
        }

        @Override
        protected boolean scan(FSGAutomator automator, FSM.Data data){
            if(data.name.equalsIgnoreCase(name)){
                mesh.name(name);

                FSInstance instance = new FSInstance();
                mesh.addInstance(instance);

                if(assembler.LOAD_INDICES){
                    mesh.indices(new VLArrayShort(data.indices.array()));
                }

                assembler.buildFirst(instance, this, data);

                return true;
            }

            return false;
        }
    }

    public static class Instanced extends FSGScanner{

        public Instanced(FSP program, FSBufferLayout layout, FSGAssembler assembler, String prefixname, int drawmode, int estimatedsize){
            super(program, layout, assembler, prefixname);
            mesh.initialize(drawmode, estimatedsize, (int)Math.ceil(estimatedsize / 2f));
        }

        @Override
        protected boolean scan(FSGAutomator automator, FSM.Data data){
            if(data.name.contains(name)){
                mesh.name(name);

                FSInstance instance = new FSInstance();
                mesh.addInstance(instance);

                if(assembler.LOAD_INDICES && mesh.indices == null){
                    mesh.indices(new VLArrayShort(data.indices.array()));
                    assembler.buildFirst(instance, this, data);

                }else{
                    assembler.buildRest(instance, this, data);
                }

                return true;
            }

            return false;
        }
    }

    public static class InstancedCopy extends FSGScanner{

        private final int copycount;

        public InstancedCopy(FSP program, FSBufferLayout layout, FSGAssembler assembler, String prefixname, int drawmode, int copycount){
            super(program, layout, assembler, prefixname);

            this.copycount = copycount;
            mesh.initialize(drawmode, copycount, 0);
        }

        @Override
        protected boolean scan(FSGAutomator automator, FSM.Data data){
            if(data.name.contains(name)){
                mesh.name(name);

                FSInstance instance = new FSInstance();
                mesh.addInstance(instance);

                if(assembler.LOAD_INDICES && mesh.indices == null){
                    mesh.indices(new VLArrayShort(data.indices.array()));
                    assembler.buildFirst(instance, this, data);

                    for(int i = 0; i < copycount; i++){
                        instance = new FSInstance();
                        mesh.addInstance(instance);

                        assembler.buildFirst(instance, this, data);
                    }
                }

                return true;
            }

            return false;
        }
    }
}
