package hypervisor.firestorm.program;

import android.opengl.GLES32;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import hypervisor.firestorm.engine.FSCFrames;
import hypervisor.firestorm.engine.FSControl;
import hypervisor.firestorm.engine.FSElements;
import hypervisor.firestorm.engine.FSRPass;
import hypervisor.firestorm.tools.FSLog;
import hypervisor.firestorm.tools.FSTools;
import hypervisor.firestorm.engine.FSView;
import hypervisor.firestorm.mesh.FSBufferBinding;
import hypervisor.firestorm.mesh.FSTypeMesh;
import hypervisor.vanguard.array.VLArray;
import hypervisor.vanguard.array.VLArrayFloat;
import hypervisor.vanguard.array.VLArrayInt;
import hypervisor.vanguard.array.VLArrayUtils;
import hypervisor.vanguard.buffer.VLBufferTracker;
import hypervisor.vanguard.list.arraybacked.VLListType;
import hypervisor.vanguard.primitive.VLFloat;
import hypervisor.vanguard.primitive.VLInt;
import hypervisor.vanguard.utils.VLCopyable;
import hypervisor.vanguard.utils.VLLog;
import hypervisor.vanguard.utils.VLLoggable;

@SuppressWarnings("unused")
public abstract class FSP{

    private static final int BUFFER_PRINT_LIMIT = 50;

    protected VLListType<FSShader> shaders;
    protected VLListType<FSTypeMesh<?>> targets;

    protected CoreConfig coreconfigs;

    protected FSLog log;
    protected int program;
    protected int debug;
    protected int uniformlocation;

    public FSP(int shadercapacity, int targetcapacity, int debugmode){
        program = -1;
        debug = debugmode;

        shaders = new VLListType<>(shadercapacity, shadercapacity);
        targets = new VLListType<>(targetcapacity, targetcapacity);

        uniformlocation = 0;
        log = new FSLog(10);
    }

    protected FSP(){

    }

    protected abstract CoreConfig generateConfigurations(VLListType<FSTypeMesh<?>> targets, int debug);

    public VLListType<FSTypeMesh<?>> meshes(){
        return targets;
    }

    public CoreConfig coreConfigs(){
        return coreconfigs;
    }

    public FSLog log(){
        return log;
    }

    public int id(){
        return program;
    }

    public FSP build(){
        program = GLES32.glCreateProgram();

        log.addTag(getClass().getSimpleName().concat(String.valueOf(program)));

        coreconfigs = generateConfigurations(targets, debug);

        int size = shaders.size();

        for(int i = 0; i < size; i++){
            FSShader shader = shaders.get(i);
            shader.buildSource();
            shader.compile();
            shader.attach();

            if(debug > FSControl.DEBUG_DISABLED){
                log.append("[Compiling and attaching shader] type[");
                log.append(shader.type);
                log.append("] ");

                shader.log(log, null);
            }

            shader.debugInfo(this, log, debug);
            log.printInfo();
        }

        GLES32.glLinkProgram(program);
        FSTools.checkGLError();

        for(int i = 0; i < size; i++){
            shaders.get(i).detach();
        }

        int[] results = new int[1];
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, results, 0);
        FSTools.checkGLError();

        if(results[0] != GLES32.GL_TRUE){
            String info = GLES32.glGetProgramInfoLog(program);
            FSTools.checkGLError();

            size = shaders.size();

            for(int i = 0; i < size; i++){
                FSShader shader = shaders.get(i);

                log.append("[");
                log.append(i + 1);
                log.append("/");
                log.append(size);
                log.append("]");
                log.append(" shaderType[");
                log.append(shader.type);
                log.append("]");
                log.printError();

                shader.log(log, null);
            }

            log.append("Program[");
            log.append(program);
            log.append("] program build failure : ");
            log.append(info);
            log.printError();

            throw new RuntimeException();
        }

        if(debug > FSControl.DEBUG_DISABLED){
            try{
                if(coreconfigs.setupconfig != null){
                    log.append("[Notifying program built for SetupConfig]\n");
                    coreconfigs.setupconfig.notifyProgramBuilt(this);
                }
                if(coreconfigs.meshconfig != null){
                    log.append("[Notifying program built for MeshConfig]\n");
                    coreconfigs.meshconfig.notifyProgramBuilt(this);
                }
                if(coreconfigs.postdrawconfig != null){
                    log.append("[Notifying program built for PostDrawConfig]\n");
                    coreconfigs.postdrawconfig.notifyProgramBuilt(this);
                }

                log.printInfo();

            }catch(Exception ex){
                log.append("Failed.\n");
                log.printError();

                throw new RuntimeException("Error during program configuration setup", ex);
            }

        }else{
            if(coreconfigs.setupconfig != null){
                coreconfigs.setupconfig.notifyProgramBuilt(this);
            }
            if(coreconfigs.meshconfig != null){
                coreconfigs.meshconfig.notifyProgramBuilt(this);
            }
            if(coreconfigs.postdrawconfig != null){
                coreconfigs.postdrawconfig.notifyProgramBuilt(this);
            }
        }

        return this;
    }

    public void releaseShaders(){
        int size = shaders.size();

        for(int i = 0; i < size; i++){
            shaders.get(i).delete();
        }

        shaders = null;
    }

    public void draw(FSRPass pass, int passindex){
        if(debug >= FSControl.DEBUG_NORMAL){
            try{
                FSTools.checkGLError();
                FSTools.checkGLError();

            }catch(Exception ex){
                throw new RuntimeException("Pre-program-run error, there is an unchecked EGL/GL error somewhere in your code before this point.", ex);
            }
        }

        use();

        int meshsize = targets.size();
        int processedcount = 0;

        if(debug > FSControl.DEBUG_DISABLED){
            log.reset();

            if(coreconfigs.setupconfig != null){
                log.addTag("SetupConfig");

                coreconfigs.setupconfig.runDebug(pass, this, null, -1, passindex, log, debug);

                log.removeLastTag();
            }
            if(coreconfigs.meshconfig != null){
                log.addTag("MeshConfig");

                for(int i = 0; i < meshsize; i++){
                    FSTypeMesh<?> target = targets.get(i);
                    log.addTag(target.name());

                    if(target.enabled()){
                        target.configure(this, pass, i, passindex);
                        coreconfigs.meshconfig.runDebug(pass, this, target, i, passindex, log, debug);

                        processedcount++;

                    }else{
                        log.append("[DISABLED]");
                        log.printInfo();
                    }

                    log.removeLastTag();
                }

                log.removeLastTag();
            }
            if(coreconfigs.postdrawconfig != null){
                log.addTag("PostDrawConfig");

                coreconfigs.postdrawconfig.runDebug(pass, this, null, -1, passindex, log, debug);

                log.removeLastTag();
            }

        }else{
            if(coreconfigs.setupconfig != null){
                coreconfigs.setupconfig.run(pass, this, null, -1, passindex);
            }
            if(coreconfigs.meshconfig != null){
                for(int i = 0; i < meshsize; i++){
                    FSTypeMesh<?> target = targets.get(i);

                    if(target.enabled()){
                        processedcount++;

                        target.configure(this, pass, i, passindex);
                        coreconfigs.meshconfig.configure(this, pass, target, i, passindex);
                    }
                }
            }
            if(coreconfigs.postdrawconfig != null){
                coreconfigs.postdrawconfig.run(pass, this, null, -1, passindex);
            }
        }

        FSCFrames.addTotalMeshesProcessed(processedcount);
    }

    public void postFrame(FSRPass pass, int passindex){
        if(coreconfigs.postframeconfig != null){
            if(debug >= FSControl.DEBUG_NORMAL){
                try{
                    FSTools.checkGLError();

                }catch(Exception ex){
                    throw new RuntimeException("Pre-program-run error (there is an unchecked error somewhere before in the code)", ex);
                }
            }

            use();

            if(debug > FSControl.DEBUG_DISABLED){
                log.reset();
                log.addTag("PostFrameConfig");

                coreconfigs.postframeconfig.runDebug(pass, this, null, -1, passindex, log, debug);

                log.removeLastTag();

            }else{
                coreconfigs.postframeconfig.run(pass, this, null, -1, passindex);
            }
        }
    }

    public void configureMesh(FSRPass pass, FSTypeMesh<?> mesh, int targetindex, int passindex){
        coreconfigs.meshconfig.configure(this, pass, mesh, targetindex, passindex);
    }

    public void use(){
        GLES32.glUseProgram(program);

        if(debug >= FSControl.DEBUG_NORMAL){
            try{
                FSTools.checkGLError();

            }catch(Exception ex){
                log.append("Error on program activation program[");
                log.append(program);
                log.append("]");
                log.printError();

                throw new RuntimeException(ex);
            }
        }
    }

    protected int nextUniformLocation(int glslsize){
        int location = uniformlocation;
        uniformlocation += glslsize;

        return location;
    }

    public void unuse(){
        GLES32.glUseProgram(0);
    }

    public void bindAttribLocation(int index, String name){
        GLES32.glBindAttribLocation(program, index, name);
    }

    public void uniformBlockBinding(int location, int bindpoint){
        GLES32.glUniformBlockBinding(program, location, bindpoint);
    }

    public void shaderStorageBlockBinding(int location, int bindpoint){
        throw new RuntimeException("GLES 3.2 does not allow dynamic shader storage buffer index binding.");
    }

    public int getAttribLocation(String name){
        return GLES32.glGetAttribLocation(program, name);
    }

    public int getUniformLocation(String name){
        return GLES32.glGetUniformLocation(program, name);
    }

    public int getUniformBlockIndex(String name){
        return GLES32.glGetUniformBlockIndex(program, name);
    }

    public int getProgramResourceIndex(String name, int resourcetype){
        return GLES32.glGetProgramResourceIndex(program, resourcetype, name);
    }

    public QueryResults[] getAttributeList(int count){
        int[] ids = new int[count];
        GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORMS, ids, 0);

        QueryResults[] data = new QueryResults[count];

        for(int i = 0; i < count; i++){
            data[i] = new QueryResults();

            GLES32.glGetActiveAttrib(program, i, QueryResults.BUFFER_SIZE, data[i].length, 0, data[i].size, 0, data[i].type, 0, data[i].name, 0);
        }

        return data;
    }

    public QueryResults[] getUniformList(int count){
        int[] ids = new int[count];
        GLES32.glGetProgramiv(program, GLES32.GL_ACTIVE_UNIFORMS, ids, 0);

        QueryResults[] data = new QueryResults[count];

        for(int i = 0; i < count; i++){
            GLES32.glGetActiveUniform(program, i, QueryResults.BUFFER_SIZE, data[i].length, 0, data[i].size, 0, data[i].type, 0, data[i].name, 0);
        }

        return data;
    }

    public void destroy(){
        GLES32.glDeleteProgram(program);
        releaseShaders();

        coreconfigs = null;

        targets.clear();
    }

    public static class CoreConfig{
        
        public FSConfigGroup setupconfig;
        public FSConfigGroup meshconfig;
        public FSConfigGroup postdrawconfig;
        public FSConfigGroup postframeconfig;

        public CoreConfig(FSConfigGroup setupconfig, FSConfigGroup meshconfig, FSConfigGroup postdrawconfig, FSConfigGroup postframeconfig){
            this.setupconfig = setupconfig;
            this.meshconfig = meshconfig;
            this.postdrawconfig = postdrawconfig;
            this.postframeconfig = postframeconfig;
        }

        protected CoreConfig(){

        }
    }

    public static class ViewProjectionMatrixUniform extends FSConfigLocated{

        public FSView target;

        public ViewProjectionMatrixUniform(Mode mode, FSView target){
            super(mode);
            this.target = target;
        }

        public ViewProjectionMatrixUniform(ViewProjectionMatrixUniform src, long flags){
            copy(src, flags);
        }

        protected ViewProjectionMatrixUniform(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniformMatrix4fv(location, 1, false, target.matrixViewProjection(), 0);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            target = ((ViewProjectionMatrixUniform)src).target;
        }

        @Override
        public ViewProjectionMatrixUniform duplicate(long flags){
            return new ViewProjectionMatrixUniform(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" target[");
            log.append(Arrays.toString(target.matrixViewProjection()));
            log.append("]");
        }
    }

    public static class ViewPosUniform extends FSConfigLocated{

        public FSView target;

        public ViewPosUniform(Mode mode, FSView target){
            super(mode);
            this.target = target;
        }

        public ViewPosUniform(ViewPosUniform src, long flags){
            copy(src, flags);
        }

        protected ViewPosUniform(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            float[] copy = target.settingsView();
            GLES32.glUniform3fv(location, 1, copy, 0);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            target = ((ViewProjectionMatrixUniform)src).target;
        }

        @Override
        public ViewPosUniform duplicate(long flags){
            return new ViewPosUniform(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" target[");
            log.append(Arrays.toString(target.settingsView()));
            log.append("]");
        }
    }

    public static class Clear extends FSConfig{

        public int flag;

        public Clear(Mode mode, int flag){
            super(mode);
            this.flag = flag;
        }

        public Clear(Clear src, long flags){
            copy(src, flags);
        }

        protected Clear(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glClear(flag);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            flag = ((Clear)src).flag;
        }

        @Override
        public Clear duplicate(long flags){
            return new Clear(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" flag[");
            log.append(flag);
            log.append("]");
        }
    }

    public static class ClearColor extends FSConfig{

        public float[] color;

        public ClearColor(Mode mode, float[] color){
            super(mode);
            this.color = color;
        }

        public ClearColor(ClearColor src, long flags){
            copy(src, flags);
        }

        protected ClearColor(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glClearColor(color[0], color[1], color[2], color[3]);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                color = ((ClearColor)src).color;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                color = ((ClearColor)src).color.clone();

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }
        }

        @Override
        public ClearColor duplicate(long flags){
            return new ClearColor(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" color[");
            log.append(Arrays.toString(color));
            log.append("]");
        }
    }

    public static class ViewPort extends FSConfig{

        public FSView view;
        public int x;
        public int y;
        public int width;
        public int height;

        public ViewPort(Mode mode, FSView view, int x, int y, int width, int height){
            super(mode);

            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.view = view;
        }

        public ViewPort(ViewPort src, long flags){
            copy(src, flags);
        }

        protected ViewPort(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            view.viewPort(x, y, width, height);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            ViewPort target = (ViewPort)src;

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                view = target.view;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                view = target.view.duplicate(VLCopyable.FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }

            x = target.x;
            y = target.y;
            width = target.width;
            height = target.height;
        }

        @Override
        public ViewPort duplicate(long flags){
            return new ViewPort(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x);
            log.append("], y[");
            log.append(y);
            log.append("], width[");
            log.append(width);
            log.append("], height[");
            log.append(height);
        }
    }

    public static class DepthMask extends FSConfig{

        public boolean mask;

        public DepthMask(Mode mode, boolean mask){
            super(mode);
            this.mask = mask;
        }

        public DepthMask(DepthMask src, long flags){
            copy(src, flags);
        }

        protected DepthMask(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glDepthMask(mask);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            mask = ((DepthMask)src).mask;
        }

        @Override
        public DepthMask duplicate(long flags){
            return new DepthMask(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" mask[");
            log.append(mask);
            log.append("]");
        }
    }

    public static class CullFace extends FSConfig{

        public int cullmode;

        public CullFace(Mode mode, int cullmode){
            super(mode);
            this.cullmode = cullmode;
        }

        public CullFace(CullFace src, long flags){
            copy(src, flags);
        }

        protected CullFace(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glCullFace(cullmode);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            cullmode = ((CullFace)src).cullmode;
        }

        @Override
        public CullFace duplicate(long flags){
            return new CullFace(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" mode[");
            log.append(cullmode);
            log.append("]");
        }
    }

    public static class AttribDivisor extends FSConfigLocated{

        public int divisor;

        public AttribDivisor(Mode mode, int divisor){
            super(mode);
            this.divisor = divisor;
        }

        public AttribDivisor(AttribDivisor src, long flags){
            copy(src, flags);
        }

        protected AttribDivisor(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttribDivisor(location, divisor);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            divisor = ((AttribDivisor)src).divisor;
        }

        @Override
        public AttribDivisor duplicate(long flags){
            return new AttribDivisor(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" divisor[");
            log.append(divisor);
            log.append("]");
        }
    }

    public static class ReadBuffer extends FSConfig{

        public int readmode;

        public ReadBuffer(Mode mode, int readmode){
            super(mode);
            this.readmode = readmode;
        }

        public ReadBuffer(ReadBuffer src, long flags){
            copy(src, flags);
        }

        protected ReadBuffer(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glReadBuffer(readmode);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            readmode = ((ReadBuffer)src).readmode;
        }

        @Override
        public ReadBuffer duplicate(long flags){
            return new ReadBuffer(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" mode[");
            log.append(readmode);
            log.append("]");
        }
    }

    public static class AttribEnable extends FSConfig{

        public FSConfigLocated config;

        public AttribEnable(Mode mode, FSConfigLocated config){
            super(mode);
            this.config = config;
        }

        public AttribEnable(AttribEnable src, long flags){
            copy(src, flags);
        }

        protected AttribEnable(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glEnableVertexAttribArray(config.location());
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            this.config = ((AttribEnable)src).config;
        }

        @Override
        public AttribEnable duplicate(long flags){
            return new AttribEnable(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" target[");
            log.append(config.getClass().getSimpleName());
            log.append("] location[");
            log.append(config.location());
            log.append("]");
        }
    }

    public static class AttribDisable extends FSConfig{

        public FSConfigLocated config;

        public AttribDisable(Mode mode, FSConfigLocated config){
            super(mode);
            this.config = config;
        }

        public AttribDisable(AttribDisable src, long flags){
            copy(src, flags);
        }

        protected AttribDisable(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glDisableVertexAttribArray(config.location());
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            this.config = ((AttribDisable)src).config;
        }

        @Override
        public AttribDisable duplicate(long flags){
            return new AttribDisable(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" target[");
            log.append(config.getClass().getSimpleName());
            log.append("] location[");
            log.append(config.location());
            log.append("]");
        }
    }

    public abstract static class Array<TYPE extends VLArray<?, ?>> extends FSConfigLocated{

        protected TYPE array;
        protected int offset;
        protected int count;

        public Array(Mode mode, TYPE array, int offset, int count){
            super(mode);

            this.array = array;
            this.offset = offset;
            this.count = count;
        }

        public Array(Mode mode){
            super(mode);
        }

        protected Array(){

        }

        public final void offset(int s){
            offset = s;
        }

        public final void count(int s){
            offset = s;
        }

        public final void array(TYPE array){
            this.array = array;
        }


        public final int offset(){
            return offset;
        }

        public final int count(){
            return count;
        }

        public int instance(){
            return -1;
        }

        public int element(){
            return -1;
        }

        public final TYPE array(){
            return array;
        }

        @Override
        public int getGLSLSize(){
            return count;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            Array<TYPE> target = (Array<TYPE>)src;

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                array = target.array;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                array = (TYPE)target.array.duplicate(FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }

            offset = target.offset;
            count = target.count;
        }

        @Override
        public abstract Array<TYPE> duplicate(long flags);

        @Override
        public abstract void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug);
    }

    public abstract static class ArrayDirect<TYPE extends VLArray<?, ?>> extends Array<TYPE>{

        public ArrayDirect(Mode mode, TYPE array, int offset, int count){
            super(mode, array, offset, count);
        }

        public ArrayDirect(Mode mode){
            super(mode);
        }

        protected ArrayDirect(){

        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            log.append(" offset[");
            log.append(offset);
            log.append("] count[");
            log.append(count);
            log.append("] array[");

            array.log(log, null);

            log.append("]");
        }

        @Override
        public abstract ArrayDirect<TYPE> duplicate(long flags);
    }

    public abstract static class ArrayElement<TYPE extends VLArray<?, ?>> extends Array<TYPE>{

        protected int instance;
        protected int element;

        public ArrayElement(Mode mode, int element, int instance, int offset, int count){
            super(mode, null, offset, count);

            this.element = element;
            this.instance = instance;
        }

        public ArrayElement(Mode mode){
            super(mode);
        }

        protected ArrayElement(){

        }

        public final void set(int instance, int element){
            this.element = element;
            this.instance = instance;
        }

        public final int element(){
            return element;
        }

        public final int instance(){
            return instance;
        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            array = (TYPE)mesh.get(instance).elementData(element);
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            ArrayElement<TYPE> target = (ArrayElement<TYPE>)src;

            target.instance = instance;
            target.element = element;
        }

        @Override
        public abstract ArrayElement<TYPE> duplicate(long flags);

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            log.append(" instance[");
            log.append(instance);
            log.append("] element[");
            log.append(FSElements.NAMES[element]);
            log.append("] array[");

            if(array == null){
                log.append("element[");
                log.append(mesh.get(instance).elementData(element).getClass().getSimpleName());
                log.append(", ");
                log.append(element);
                log.append("]");

            }else{
                array.log(log, null);
            }

            log.append("]");
        }
    }

    public static class AttribPointer extends FSConfigLocated{

        public int element;
        public int bindingindex;
        public boolean normalized;

        public AttribPointer(Mode mode, int element, int bindingindex, boolean normalized){
            super(mode);

            this.element = element;
            this.bindingindex = bindingindex;
            this.normalized = normalized;
        }

        public AttribPointer(AttribPointer src, long flags){
            copy(src, flags);
        }

        protected AttribPointer(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSBufferBinding<?> binding = mesh.binding(element, bindingindex);
            VLBufferTracker tracker = binding.tracker;

            int bytesize = tracker.typebytesize;
            binding.vbuffer.bind();

            GLES32.glVertexAttribPointer(location, tracker.unitsubcount, FSElements.GLTYPES[element], normalized, tracker.stride * bytesize, tracker.offset * bytesize);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            AttribPointer target = (AttribPointer)src;

            element = target.element;
            bindingindex = target.bindingindex;
            normalized = target.normalized;
        }

        @Override
        public AttribPointer duplicate(long flags){
            return new AttribPointer(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" element[");
            log.append(FSElements.NAMES[element]);
            log.append("] bindingIndex[");
            log.append(bindingindex);
            log.append("] normalized[");
            log.append(normalized);
            log.append("] tracker[");

            try{
                FSBufferBinding<?> binding = mesh.binding(element, bindingindex);

                binding.tracker.log(log, null);
                log.append("] buffer[");

                binding.vbuffer.log(log, BUFFER_PRINT_LIMIT);
                log.append("]");

            }catch(Exception ex){
                log.append(" [FAILED]");
                throw new RuntimeException("Mesh does not have the necessary binding for this config to use.", ex);
            }
        }
    }

    public static class AttribIPointer extends FSConfigLocated{

        public int element;
        public int bindingindex;

        public AttribIPointer(Mode mode, int element, int bindingindex){
            super(mode);

            this.element = element;
            this.bindingindex = bindingindex;
        }

        public AttribIPointer(AttribIPointer src, long flags){
            copy(src, flags);
        }

        protected AttribIPointer(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSBufferBinding<?> binding = mesh.binding(element, bindingindex);
            VLBufferTracker tracker = binding.tracker;

            int bytesize = tracker.typebytesize;

            binding.vbuffer.bind();

            GLES32.glVertexAttribIPointer(location, tracker.unitsubcount, FSElements.GLTYPES[element], tracker.stride * bytesize, tracker.offset * bytesize);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            AttribPointer target = (AttribPointer)src;

            element = target.element;
            bindingindex = target.bindingindex;
        }

        @Override
        public AttribIPointer duplicate(long flags){
            return new AttribIPointer(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" element[");
            log.append(FSElements.NAMES[element]);
            log.append("] bindingIndex[");
            log.append(bindingindex);
            log.append("] tracker[");

            try{
                FSBufferBinding<?> binding = mesh.binding(element, bindingindex);

                binding.tracker.log(log, null);
                log.append("] buffer[");

                binding.vbuffer.log(log, BUFFER_PRINT_LIMIT);
                log.append("]");

            }catch(Exception ex){
                log.append(" [FAILED]");
                throw new RuntimeException("Mesh does not have the necessary binding for this config to use.", ex);
            }
        }
    }

    public static class UniformMatrix4fvd extends ArrayDirect<VLArrayFloat>{

        public UniformMatrix4fvd(Mode mode, VLArrayFloat array, int offset, int count){
            super(mode, array, offset, count);
        }

        public UniformMatrix4fvd(UniformMatrix4fvd src, long flags){
            copy(src, flags);
        }

        protected UniformMatrix4fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniformMatrix4fv(location, count(), false, array().array, offset());
        }

        @Override
        public UniformMatrix4fvd duplicate(long flags){
            return new UniformMatrix4fvd(this, flags);
        }
    }

    public static class UniformMatrix4fve extends ArrayElement<VLArrayFloat>{

        public UniformMatrix4fve(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public UniformMatrix4fve(UniformMatrix4fve src, long flags){
            copy(src, flags);
        }

        protected UniformMatrix4fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniformMatrix4fv(location, count(), false, array().array, offset());
        }

        @Override
        public UniformMatrix4fve duplicate(long flags){
            return new UniformMatrix4fve(this, flags);
        }
    }

    public static class Uniform4fvd extends ArrayDirect<VLArrayFloat>{

        public Uniform4fvd(Mode mode, VLArrayFloat array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform4fvd(Uniform4fvd src, long flags){
            copy(src, flags);
        }

        protected Uniform4fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform4fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform4fvd duplicate(long flags){
            return new Uniform4fvd(this, flags);
        }
    }

    public static class Uniform4fve extends ArrayElement<VLArrayFloat>{

        public Uniform4fve(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform4fve(Uniform4fve src, long flags){
            copy(src, flags);
        }

        protected Uniform4fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);

            GLES32.glUniform4fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform4fve duplicate(long flags){
            return new Uniform4fve(this, flags);
        }
    }

    public static class Uniform3fvd extends ArrayDirect<VLArrayFloat>{

        public Uniform3fvd(Mode mode, VLArrayFloat array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform3fvd(Uniform3fvd src, long flags){
            copy(src, flags);
        }

        protected Uniform3fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform3fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform3fvd duplicate(long flags){
            return new Uniform3fvd(this, flags);
        }
    }

    public static class Uniform3fve extends ArrayElement<VLArrayFloat>{

        public Uniform3fve(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform3fve(Uniform3fve src, long flags){
            copy(src, flags);
        }

        protected Uniform3fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform3fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform3fve duplicate(long flags){
            return new Uniform3fve(this, flags);
        }
    }

    public static class Uniform2fvd extends ArrayDirect<VLArrayFloat>{

        public Uniform2fvd(Mode mode, VLArrayFloat array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform2fvd(Uniform2fvd src, long flags){
            copy(src, flags);
        }

        protected Uniform2fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform2fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform2fvd duplicate(long flags){
            return new Uniform2fvd(this, flags);
        }
    }

    public static class Uniform2fve extends ArrayElement<VLArrayFloat>{

        public Uniform2fve(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform2fve(Uniform2fve src, long flags){
            copy(src, flags);
        }

        protected Uniform2fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform2fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform2fve duplicate(long flags){
            return new Uniform2fve(this, flags);
        }
    }

    public static class Uniform1fvd extends ArrayDirect<VLArrayFloat>{

        public Uniform1fvd(Mode mode, VLArrayFloat array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform1fvd(Uniform1fvd src, long flags){
            copy(src, flags);
        }

        protected Uniform1fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform1fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform1fvd duplicate(long flags){
            return new Uniform1fvd(this, flags);
        }
    }

    public static class Uniform1fve extends ArrayElement<VLArrayFloat>{

        public Uniform1fve(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform1fve(Uniform1fve src, long flags){
            copy(src, flags);
        }

        protected Uniform1fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform1fv(location, count(), array().array, offset());
        }

        @Override
        public Uniform1fve duplicate(long flags){
            return new Uniform1fve(this, flags);
        }
    }

    public static class Uniform4f extends FSConfigLocated{

        public VLFloat x;
        public VLFloat y;
        public VLFloat z;
        public VLFloat w;

        public Uniform4f(Mode mode, VLFloat x, VLFloat y, VLFloat z, VLFloat w){
            super(mode);

            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        public Uniform4f(Uniform4f src, long flags){
            copy(src, flags);
        }

        protected Uniform4f(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform4f(location, x.value, y.value, z.value, w.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public Uniform4f duplicate(long flags){
            return new Uniform4f(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("], z[");
            log.append(z.value);
            log.append("], w[");
            log.append(w.value);
            log.append("]");
        }
    }

    public static class Uniform3f extends FSConfigLocated{

        public VLFloat x;
        public VLFloat y;
        public VLFloat z;

        public Uniform3f(Mode mode, VLFloat x, VLFloat y, VLFloat z){
            super(mode);

            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Uniform3f(Uniform3f src, long flags){
            copy(src, flags);
        }

        protected Uniform3f(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform3f(location, x.value, y.value, z.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public Uniform3f duplicate(long flags){
            return new Uniform3f(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("], z[");
            log.append(z.value);
            log.append("]");
        }
    }

    public static class Uniform2f extends FSConfigLocated{

        public VLFloat x;
        public VLFloat y;

        public Uniform2f(Mode mode, VLFloat x, VLFloat y){
            super(mode);

            this.x = x;
            this.y = y;
        }

        public Uniform2f(Uniform2f src, long flags){
            copy(src, flags);
        }

        protected Uniform2f(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform2f(location, x.value, y.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public Uniform2f duplicate(long flags){
            return new Uniform2f(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("]");
        }
    }

    public static class Uniform1f extends FSConfigLocated{

        public VLFloat x;

        public Uniform1f(Mode mode, VLFloat x){
            super(mode);
            this.x = x;
        }

        public Uniform1f(Uniform1f src, long flags){
            copy(src, flags);
        }

        protected Uniform1f(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform1f(location, x.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public Uniform1f duplicate(long flags){
            return new Uniform1f(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("]");
        }
    }

    public static class Uniform4ivd extends ArrayDirect<VLArrayInt>{

        public Uniform4ivd(Mode mode, VLArrayInt array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform4ivd(Uniform4ivd src, long flags){
            copy(src, flags);
        }

        protected Uniform4ivd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform4iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform4ivd duplicate(long flags){
            return new Uniform4ivd(this, flags);
        }
    }

    public static class Uniform4ive extends ArrayElement<VLArrayInt>{

        public Uniform4ive(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform4ive(Uniform4ive src, long flags){
            copy(src, flags);
        }

        protected Uniform4ive(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform4iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform4ive duplicate(long flags){
            return new Uniform4ive(this, flags);
        }
    }

    public static class Uniform3ivd extends ArrayDirect<VLArrayInt>{

        public Uniform3ivd(Mode mode, VLArrayInt array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform3ivd(Uniform3ivd src, long flags){
            copy(src, flags);
        }

        protected Uniform3ivd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform4iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform3ivd duplicate(long flags){
            return new Uniform3ivd(this, flags);
        }
    }

    public static class Uniform3ive extends ArrayElement<VLArrayInt>{

        public Uniform3ive(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform3ive(Uniform3ive src, long flags){
            copy(src, flags);
        }

        protected Uniform3ive(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform4iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform3ive duplicate(long flags){
            return new Uniform3ive(this, flags);
        }
    }

    public static class Uniform2ivd extends ArrayDirect<VLArrayInt>{

        public Uniform2ivd(Mode mode, VLArrayInt array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform2ivd(Uniform2ivd src, long flags){
            copy(src, flags);
        }

        protected Uniform2ivd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform4iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform2ivd duplicate(long flags){
            return new Uniform2ivd(this, flags);
        }
    }

    public static class Uniform2ive extends ArrayElement<VLArrayInt>{

        public Uniform2ive(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform2ive(Uniform2ive src, long flags){
            copy(src, flags);
        }

        protected Uniform2ive(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform4iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform2ive duplicate(long flags){
            return new Uniform2ive(this, flags);
        }
    }

    public static class Uniform1ivd extends ArrayDirect<VLArrayInt>{

        public Uniform1ivd(Mode mode, VLArrayInt array, int offset, int count){
            super(mode, array, offset, count);
        }

        public Uniform1ivd(Uniform1ivd src, long flags){
            copy(src, flags);
        }

        protected Uniform1ivd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform1iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform1ivd duplicate(long flags){
            return new Uniform1ivd(this, flags);
        }
    }

    public static class Uniform1ive extends ArrayElement<VLArrayInt>{

        public Uniform1ive(Mode mode, int instance, int element, int offset, int count){
            super(mode, element, instance, offset, count);
        }

        public Uniform1ive(Uniform1ive src, long flags){
            copy(src, flags);
        }

        protected Uniform1ive(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glUniform1iv(location, count(), array().array, offset());
        }

        @Override
        public Uniform1ive duplicate(long flags){
            return new Uniform1ive(this, flags);
        }
    }

    public static class Uniform4i extends FSConfigLocated{

        public VLInt x;
        public VLInt y;
        public VLInt z;
        public VLInt w;

        public Uniform4i(Mode mode, VLInt x, VLInt y, VLInt z, VLInt w){
            super(mode);

            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        public Uniform4i(Uniform4i src, long flags){
            copy(src, flags);
        }

        protected Uniform4i(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform4i(location, x.value, y.value, z.value, w.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            Uniform4i target = (Uniform4i)src;

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                x = target.x;
                y = target.y;
                z = target.z;
                w = target.w;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                x = target.x.duplicate(FLAG_DUPLICATE);
                y = target.y.duplicate(FLAG_DUPLICATE);
                z = target.z.duplicate(FLAG_DUPLICATE);
                w = target.w.duplicate(FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }
        }

        @Override
        public Uniform4i duplicate(long flags){
            return new Uniform4i(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("], z[");
            log.append(z.value);
            log.append("], w[");
            log.append(w.value);
            log.append("]");
        }
    }

    public static class Uniform3i extends FSConfigLocated{

        public VLInt x;
        public VLInt y;
        public VLInt z;

        public Uniform3i(Mode mode, VLInt x, VLInt y, VLInt z){
            super(mode);

            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Uniform3i(Uniform3i src, long flags){
            copy(src, flags);
        }

        protected Uniform3i(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform3i(location, x.value, y.value, z.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            Uniform3i target = (Uniform3i)src;

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                x = target.x;
                y = target.y;
                z = target.z;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                x = target.x.duplicate(FLAG_DUPLICATE);
                y = target.y.duplicate(FLAG_DUPLICATE);
                z = target.z.duplicate(FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }
        }

        @Override
        public Uniform3i duplicate(long flags){
            return new Uniform3i(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("], z[");
            log.append(z.value);
            log.append("]");
        }
    }

    public static class Uniform2i extends FSConfigLocated{

        public VLInt x;
        public VLInt y;

        public Uniform2i(Mode mode, VLInt x, VLInt y){
            super(mode);

            this.x = x;
            this.y = y;
        }

        public Uniform2i(Uniform2i src, long flags){
            copy(src, flags);
        }

        protected Uniform2i(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform2i(location, x.value, y.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            Uniform2i target = (Uniform2i)src;

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                x = target.x;
                y = target.y;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                x = target.x.duplicate(FLAG_DUPLICATE);
                y = target.y.duplicate(FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }
        }

        @Override
        public Uniform2i duplicate(long flags){
            return new Uniform2i(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("]");
        }
    }

    public static class Uniform1i extends FSConfigLocated{

        public VLInt x;

        public Uniform1i(Mode mode, VLInt x){
            super(mode);
            this.x = x;
        }

        public Uniform1i(Uniform1i src, long flags){
            copy(src, flags);
        }

        protected Uniform1i(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform1i(location, x.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                x = ((Uniform1i)src).x;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                x = ((Uniform1i)src).x.duplicate(FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }
        }

        @Override
        public Uniform1i duplicate(long flags){
            return new Uniform1i(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("]");
        }
    }

    public static class Attrib4fvd extends ArrayDirect<VLArrayFloat>{

        public Attrib4fvd(Mode mode, VLArrayFloat array, int offset){
            super(mode, array, offset, 4);
        }

        public Attrib4fvd(Attrib4fvd src, long flags){
            copy(src, flags);
        }

        protected Attrib4fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttrib4fv(location, array().array, offset());
        }

        @Override
        public Attrib4fvd duplicate(long flags){
            return new Attrib4fvd(this, flags);
        }
    }

    public static class Attrib4fve extends ArrayElement<VLArrayFloat>{

        public Attrib4fve(Mode mode, int instance, int element, int offset){
            super(mode, element, instance, offset, 4);
        }

        public Attrib4fve(Attrib4fve src, long flags){
            copy(src, flags);
        }

        protected Attrib4fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glVertexAttrib4fv(location, array().array, offset());
        }

        @Override
        public Attrib4fve duplicate(long flags){
            return new Attrib4fve(this, flags);
        }
    }

    public static class Attrib3fvd extends ArrayDirect<VLArrayFloat>{

        public Attrib3fvd(Mode mode, VLArrayFloat array, int offset){
            super(mode, array, offset, 3);
        }

        public Attrib3fvd(Attrib3fvd src, long flags){
            copy(src, flags);
        }

        protected Attrib3fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttrib3fv(location, array().array, offset());
        }

        @Override
        public Attrib3fvd duplicate(long flags){
            return new Attrib3fvd(this, flags);
        }
    }

    public static class Attrib3fve extends ArrayElement<VLArrayFloat>{

        public Attrib3fve(Mode mode, int instance, int element, int offset){
            super(mode, element, instance, offset, 3);
        }

        public Attrib3fve(Attrib3fve src, long flags){
            copy(src, flags);
        }

        protected Attrib3fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glVertexAttrib3fv(location, array().array, offset());
        }

        @Override
        public Attrib3fve duplicate(long flags){
            return new Attrib3fve(this, flags);
        }
    }

    public static class Attrib2fvd extends ArrayDirect<VLArrayFloat>{

        public Attrib2fvd(Mode mode, VLArrayFloat array, int offset){
            super(mode, array, offset, 2);
        }

        public Attrib2fvd(Attrib2fvd src, long flags){
            copy(src, flags);
        }

        protected Attrib2fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttrib2fv(location, array().array, offset());
        }

        @Override
        public Attrib2fvd duplicate(long flags){
            return new Attrib2fvd(this, flags);
        }
    }

    public static class Attrib2fve extends ArrayElement<VLArrayFloat>{

        public Attrib2fve(Mode mode, int instance, int element, int offset){
            super(mode, element, instance, offset, 2);
        }

        public Attrib2fve(Attrib2fve src, long flags){
            copy(src, flags);
        }

        protected Attrib2fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glVertexAttrib2fv(location, array().array, offset());
        }

        @Override
        public Attrib2fve duplicate(long flags){
            return new Attrib2fve(this, flags);
        }
    }

    public static class Attrib1fvd extends ArrayDirect<VLArrayFloat>{

        public Attrib1fvd(Mode mode, VLArrayFloat array, int offset){
            super(mode, array, offset, 1);
        }

        public Attrib1fvd(Attrib1fvd src, long flags){
            copy(src, flags);
        }

        protected Attrib1fvd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttrib1fv(location, array().array, offset());
        }

        @Override
        public Attrib1fvd duplicate(long flags){
            return new Attrib1fvd(this, flags);
        }
    }

    public static class Attrib1fve extends ArrayElement<VLArrayFloat>{

        public Attrib1fve(Mode mode, int instance, int element, int offset){
            super(mode, element, instance, offset, 1);
        }

        public Attrib1fve(Attrib1fve src, long flags){
            copy(src, flags);
        }

        protected Attrib1fve(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glVertexAttrib1fv(location, array().array, offset());
        }

        @Override
        public Attrib1fve duplicate(long flags){
            return new Attrib1fve(this, flags);
        }
    }

    public static class AttribI4i extends FSConfigLocated{

        public VLInt x;
        public VLInt y;
        public VLInt z;
        public VLInt w;

        public AttribI4i(Mode mode, VLInt x, VLInt y, VLInt z, VLInt w){
            super(mode);

            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        public AttribI4i(AttribI4i src, long flags){
            copy(src, flags);
        }

        protected AttribI4i(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttribI4i(location, x.value, y.value, z.value, w.value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            AttribI4i target = (AttribI4i)src;

            if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
                x = target.x;
                y = target.y;
                z = target.z;
                w = target.w;

            }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
                x = target.x.duplicate(FLAG_DUPLICATE);
                y = target.y.duplicate(FLAG_DUPLICATE);
                z = target.z.duplicate(FLAG_DUPLICATE);
                w = target.w.duplicate(FLAG_DUPLICATE);

            }else{
                VLCopyable.Helper.throwMissingDefaultFlags();
            }
        }

        @Override
        public AttribI4i duplicate(long flags){
            return new AttribI4i(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" x[");
            log.append(x.value);
            log.append("], y[");
            log.append(y.value);
            log.append("], z[");
            log.append(z.value);
            log.append("], w[");
            log.append(w.value);
            log.append("]");
        }
    }

    public static class AttribI4ivd extends ArrayDirect<VLArrayInt>{

        public AttribI4ivd(Mode mode, VLArrayInt array, int offset){
            super(mode, array, offset, 4);
        }

        public AttribI4ivd(AttribI4ivd src, long flags){
            copy(src, flags);
        }

        protected AttribI4ivd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttribI4iv(location, array().array, offset());
        }

        @Override
        public AttribI4ivd duplicate(long flags){
            return new AttribI4ivd(this, flags);
        }
    }

    public static class AttribI4ive extends ArrayElement<VLArrayInt>{

        public AttribI4ive(Mode mode, int instance, int element, int offset){
            super(mode, element, instance, offset, 4);
        }

        public AttribI4ive(AttribI4ive src, long flags){
            copy(src, flags);
        }

        protected AttribI4ive(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glVertexAttribI4iv(location, array().array, offset());
        }

        @Override
        public AttribI4ive duplicate(long flags){
            return new AttribI4ive(this, flags);
        }
    }

    public static class AttribI4uivd extends ArrayDirect<VLArrayInt>{

        public AttribI4uivd(Mode mode, VLArrayInt array, int offset){
            super(mode, array, offset, 4);
        }

        public AttribI4uivd(AttribI4uivd src, long flags){
            copy(src, flags);
        }

        protected AttribI4uivd(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glVertexAttribI4uiv(location, array().array, offset());
        }

        @Override
        public AttribI4uivd duplicate(long flags){
            return new AttribI4uivd(this, flags);
        }
    }

    public static class AttribI4uive extends ArrayElement<VLArrayInt>{

        public AttribI4uive(Mode mode, int instance, int element, int offset){
            super(mode, element, instance, offset, 4);
        }

        public AttribI4uive(AttribI4uive src, long flags){
            copy(src, flags);
        }

        protected AttribI4uive(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            super.configure(program, pass, mesh, meshindex, passindex);
            GLES32.glVertexAttribI4uiv(location, array().array, offset());
        }

        @Override
        public AttribI4uive duplicate(long flags){
            return new AttribI4uive(this, flags);
        }
    }

    public static class UniformBlockElement extends FSConfigLocated{

        public int element;
        public int bindingindex;

        protected String name;

        public UniformBlockElement(Mode mode, int element, String name, int bindingindex){
            super(mode);

            this.element = element;
            this.bindingindex = bindingindex;
            this.name = name;
        }

        public UniformBlockElement(UniformBlockElement src, long flags){
            copy(src, flags);
        }

        protected UniformBlockElement(){

        }

        @Override
        protected void notifyProgramBuilt(FSP program){
            location = program.getUniformBlockIndex(name);
            name = null;
        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSVertexBuffer<?> buffer = mesh.binding(element, bindingindex).vbuffer;

            program.uniformBlockBinding(location, buffer.bindPoint());
            buffer.bindBufferBase();
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            UniformBlockElement target = (UniformBlockElement)src;
            element = target.element;
            bindingindex = target.bindingindex;
            name = target.name;
        }

        @Override
        public UniformBlockElement duplicate(long flags){
            return new UniformBlockElement(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" element[");
            log.append(FSElements.NAMES[element]);
            log.append("] bindingIndex[");
            log.append(bindingindex);
            log.append("] tracker[");

            try{
                FSBufferBinding<?> binding = mesh.binding(element, bindingindex);

                binding.tracker.log(log, null);
                log.append("] buffer[");

                binding.vbuffer.log(log, BUFFER_PRINT_LIMIT);
                log.append("]");

            }catch(Exception ex){
                log.append(" [FAILED]");
                throw new RuntimeException("Mesh does not have the necessary binding for this config to use.", ex);
            }
        }
    }

    public static class UniformBlockData extends FSConfigLocated{

        public FSVertexBuffer<?> vbuffer;
        protected String name;

        public UniformBlockData(Mode mode, FSVertexBuffer<?> vbuffer, String name){
            super(mode);

            this.vbuffer = vbuffer;
            this.name = name;
        }

        public UniformBlockData(UniformBlockData src, long flags){
            copy(src, flags);
        }

        protected UniformBlockData(){

        }

        @Override
        protected void notifyProgramBuilt(FSP program){
            location = program.getUniformBlockIndex(name);
            name = null;
        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            program.uniformBlockBinding(location, vbuffer.bindPoint());
            vbuffer.bindBufferBase();
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            UniformBlockData target = (UniformBlockData)src;
            vbuffer = target.vbuffer;
            name = target.name;
        }

        @Override
        public UniformBlockData duplicate(long flags){
            return new UniformBlockData(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" element[NONE] ");

            vbuffer.log(log, BUFFER_PRINT_LIMIT);

            log.append("]");
        }
    }

    public static class TextureBind extends FSConfig{

        public FSTexture texture;

        public TextureBind(Mode mode, FSTexture texture){
            super(mode);
            this.texture = texture;
        }

        public TextureBind(TextureBind src, long flags){
            copy(src, flags);
        }

        protected TextureBind(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            texture.activateUnit();
            texture.bind();
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            texture = ((TextureBind)src).texture;
        }

        @Override
        public TextureBind duplicate(long flags){
            return new TextureBind(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);
            log.append(" [DYNAMIC]");
        }
    }

    public static class TextureColorBind extends FSConfig{

        public TextureColorBind(Mode mode){
            super(mode);
        }

        public TextureColorBind(TextureColorBind src, long flags){
            copy(src, flags);
        }

        protected TextureColorBind(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSTexture t = mesh.first().colorTexture();
            t.activateUnit();
            t.bind();
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public TextureColorBind duplicate(long flags){
            return new TextureColorBind(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);
            log.append(" [DYNAMIC]");
        }
    }

    public static class TextureColorUnit extends FSConfigLocated{

        public TextureColorUnit(Mode mode){
            super(mode);
        }

        public TextureColorUnit(TextureColorUnit src, long flags){
            copy(src, flags);
        }

        protected TextureColorUnit(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glUniform1i(location, mesh.first().colorTexture().unit().value);
        }

        @Override
        public int getGLSLSize(){
            return 1;
        }

        @Override
        public TextureColorUnit duplicate(long flags){
            return new TextureColorUnit(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);
            log.append(" [DYNAMIC]");
        }
    }

    public static class DrawArrays extends FSConfig{

        public int drawmode;

        public DrawArrays(Mode mode, int drawmode){
            super(mode);
            this.drawmode = drawmode;
        }

        public DrawArrays(DrawArrays src, long flags){
            copy(src, flags);
        }

        protected DrawArrays(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glDrawArrays(drawmode, 0, mesh.first().vertexSize());
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public DrawArrays duplicate(long flags){
            return new DrawArrays(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" drawMode[");
            log.append(drawmode);
            log.append("] indexCount[");
            log.append(mesh.first().vertexSize());
            log.append("]");
        }
    }

    public static class DrawArraysInstanced extends FSConfig{

        public int drawmode;

        public DrawArraysInstanced(Mode mode, int drawmode){
            super(mode);
            this.drawmode = drawmode;
        }

        public DrawArraysInstanced(DrawArraysInstanced src, long flags){
            copy(src, flags);
        }

        protected DrawArraysInstanced(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            GLES32.glDrawArraysInstanced(drawmode, 0, mesh.first().vertexSize(), mesh.size());
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public DrawArraysInstanced duplicate(long flags){
            return new DrawArraysInstanced(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" drawMode[");
            log.append(drawmode);
            log.append("] indexCount[");
            log.append(mesh.first().vertexSize());
            log.append("] instanceCount[");
            log.append(mesh.size());
            log.append("]");
        }
    }

    public static class DrawElements extends FSConfig{

        public int bindingindex;
        public int drawmode;

        public DrawElements(Mode mode, int bindingindex, int drawmode){
            super(mode);

            this.bindingindex = bindingindex;
            this.drawmode = drawmode;
        }

        public DrawElements(DrawElements src, long flags){
            copy(src, flags);
        }

        protected DrawElements(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSBufferBinding<?> binding = mesh.binding(FSElements.ELEMENT_INDEX, bindingindex);
            VLBufferTracker tracker = binding.tracker;

            binding.vbuffer.bind();
            GLES32.glDrawElements(drawmode, tracker.count, FSElements.GLTYPES[FSElements.ELEMENT_INDEX], tracker.offset * FSElements.BYTES[FSElements.ELEMENT_INDEX]);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            bindingindex = ((DrawElements)src).bindingindex;
        }

        @Override
        public DrawElements duplicate(long flags){
            return new DrawElements(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" element[");
            log.append(FSElements.NAMES[FSElements.ELEMENT_INDEX]);
            log.append("] bindingIndex[");
            log.append(bindingindex);
            log.append("] buffer[");

            mesh.binding(FSElements.ELEMENT_INDEX, bindingindex).vbuffer.log(log, BUFFER_PRINT_LIMIT);

            log.append("]");
        }
    }

    public static class DrawElementsInstanced extends FSConfig{

        public int bindingindex;
        public int drawmode;

        public DrawElementsInstanced(Mode mode, int bindingindex, int drawmode){
            super(mode);

            this.bindingindex = bindingindex;
            this.drawmode = drawmode;
        }

        public DrawElementsInstanced(DrawElementsInstanced src, long flags){
            copy(src, flags);
        }

        protected DrawElementsInstanced(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSBufferBinding<?> binding = mesh.binding(FSElements.ELEMENT_INDEX, bindingindex);
            VLBufferTracker tracker = binding.tracker;

            binding.vbuffer.bind();
            GLES32.glDrawElementsInstanced(drawmode, tracker.count, FSElements.GLTYPES[FSElements.ELEMENT_INDEX], tracker.offset * FSElements.BYTES[FSElements.ELEMENT_INDEX], mesh.size());
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);
            bindingindex = ((DrawElementsInstanced)src).bindingindex;
        }

        @Override
        public DrawElementsInstanced duplicate(long flags){
            return new DrawElementsInstanced(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" drawMode[");
            log.append(drawmode);
            log.append("] instanceCount[");
            log.append(mesh.size());
            log.append("] bindingIndex[");
            log.append(bindingindex);
            log.append("] tracker[");

            try{
                FSBufferBinding<?> binding = mesh.binding(FSElements.ELEMENT_INDEX, bindingindex);

                binding.tracker.log(log, null);
                log.append("] buffer[");

                binding.vbuffer.log(log, BUFFER_PRINT_LIMIT);
                log.append("]");

            }catch(Exception ex){
                log.append(" [FAILED]");
                throw new RuntimeException("Mesh does not have the necessary binding for this config to use.", ex);
            }
        }
    }

    public static class DrawRangeElements extends FSConfig{

        public int start;
        public int end;
        public int count;
        public int bindingindex;
        public int drawmode;

        public DrawRangeElements(Mode mode, int start, int end, int count, int bindingindex, int drawmode){
            super(mode);

            this.start = start;
            this.end = end;
            this.count = count;
            this.bindingindex = bindingindex;
            this.drawmode = drawmode;
        }

        public DrawRangeElements(DrawRangeElements src, long flags){
            copy(src, flags);
        }

        protected DrawRangeElements(){

        }

        @Override
        public void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
            FSBufferBinding<?> binding = mesh.binding(FSElements.ELEMENT_INDEX, bindingindex);
            VLBufferTracker tracker = binding.tracker;

            binding.vbuffer.bind();
            GLES32.glDrawRangeElements(drawmode, start, end, count, FSElements.GLTYPES[FSElements.ELEMENT_INDEX], tracker.offset * FSElements.BYTES[FSElements.ELEMENT_INDEX]);
        }

        @Override
        public int getGLSLSize(){
            return 0;
        }

        @Override
        public void copy(FSConfig src, long flags){
            super.copy(src, flags);

            DrawRangeElements target = (DrawRangeElements)src;
            start = target.start;
            end = target.end;
            count = target.count;
            bindingindex = target.bindingindex;
        }

        @Override
        public DrawRangeElements duplicate(long flags){
            return new DrawRangeElements(this, flags);
        }

        @Override
        public void attachDebugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
            super.attachDebugInfo(pass, program, mesh, log, debug);

            log.append(" drawMode[");
            log.append(drawmode);
            log.append("] start[");
            log.append(start);
            log.append("] end[");
            log.append(end);
            log.append("] count[");
            log.append(count);
            log.append("] bindingIndex[");
            log.append(bindingindex);
            log.append("] tracker[");

            try{
                FSBufferBinding<?> binding = mesh.binding(FSElements.ELEMENT_INDEX, bindingindex);

                binding.tracker.log(log, null);
                log.append("] buffer[");

                binding.vbuffer.log(log, BUFFER_PRINT_LIMIT);
                log.append("]");

            }catch(Exception ex){
                log.append(" [FAILED]");
                throw new RuntimeException("Mesh does not have the necessary binding for this config to use.", ex);
            }
        }
    }

    public static class QueryResults implements VLLoggable{

        public static int BUFFER_SIZE = 30;

        public int[] length = new int[1];
        public int[] size = new int[1];
        public int[] type = new int[1];
        public byte[] name = new byte[BUFFER_SIZE];

        protected QueryResults(){

        }

        @Override
        public void log(VLLog log, Object data){
            log.append("[");
            log.append(getClass().getSimpleName());
            log.append("] sizeRow[");
            log.append(length[0]);
            log.append("] size[");
            log.append(size[0]);
            log.append("] type[");
            log.append(type[0]);
            log.append("] name[");
            log.append(new String(VLArrayUtils.slice(name, 0, length[0]), StandardCharsets.UTF_8));
            log.append("]");
        }
    }
}
