package hypervisor.firestorm.program;

import hypervisor.firestorm.engine.FSControl;
import hypervisor.firestorm.engine.FSRPass;
import hypervisor.firestorm.mesh.FSTypeMesh;
import hypervisor.vanguard.list.VLListType;
import hypervisor.vanguard.utils.VLCopyable;
import hypervisor.vanguard.utils.VLLog;

public class FSConfigGroup extends FSConfig{

    public static final long FLAG_DUPLICATE_CONFIGS = 0x1L;

    protected VLListType<FSConfig> configs;

    public FSConfigGroup(Mode mode, int capacity, int resizer){
        super(mode);
        configs = new VLListType<>(capacity, resizer);
    }

    public FSConfigGroup(FSConfigGroup src, long flags){
        super(null);
        copy(src, flags);
    }

    protected FSConfigGroup(){

    }

    public void add(FSConfig config){
        configs.add(config);
    }

    public VLListType<FSConfig> configs(){
        return configs;
    }

    @Override
    protected void notifyProgramBuilt(FSP program){
        int size = configs.size();

        for(int i = 0; i < size; i++){
            configs.get(i).notifyProgramBuilt(program);
        }
    }

    @Override
    public final void configure(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex){
        int size = configs.size();

        for(int i = 0; i < size; i++){
            configs.get(i).run(pass, program, mesh, meshindex, passindex);
        }
    }

    @Override
    public final void configureDebug(FSP program, FSRPass pass, FSTypeMesh<?> mesh, int meshindex, int passindex, VLLog log, int debug){
        int size = configs.size();

        log.addTag(getClass().getSimpleName());

        for(int i = 0; i < size; i++){
            log.append("[");
            log.append(i + 1);
            log.append("/");
            log.append(size);
            log.append("] ");

            configs.get(i).runDebug(pass, program, mesh, meshindex, passindex, log, debug);
        }

        log.removeLastTag();
    }

    @Override
    public int getGLSLSize(){
        return 0;
    }

    @Override
    public void copy(FSConfig src, long flags){
        super.copy(src, flags);

        FSConfigSequence target = (FSConfigSequence)src;

        if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
            configs = target.configs;

        }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
            configs = target.configs.duplicate(FLAG_DUPLICATE);

        }else if((flags & FLAG_CUSTOM) == FLAG_CUSTOM){
            if((flags & FLAG_DUPLICATE_CONFIGS) == FLAG_DUPLICATE_CONFIGS){
                configs = target.configs.duplicate(FLAG_CUSTOM | VLListType.FLAG_DUPLICATE_ARRAY_FULLY);

            }else{
                VLCopyable.Helper.throwMissingSubFlags("FLAG_CUSTOM", "FLAG_DUPLICATE_CONFIGS");
            }

        }else{
            VLCopyable.Helper.throwMissingAllFlags();
        }
    }

    @Override
    public FSConfigGroup duplicate(long flags){
        return new FSConfigGroup(this, flags);
    }

    @Override
    public void debugInfo(FSRPass pass, FSP program, FSTypeMesh<?> mesh, VLLog log, int debug){
        StringBuilder data = new StringBuilder();
        FSConfig c;

        int size = configs.size();

        data.append("sequence[");
        data.append(size);
        data.append("]");

        for(int i = 0; i < size; i++){
            c = configs.get(i);

            data.append("config[");
            data.append(i);
            data.append("] [");
            data.append(c.getClass().getSimpleName());

            if(debug >= FSControl.DEBUG_FULL){
                data.append("] [");
                c.debugInfo(pass, program, mesh, log, debug);
                data.append("]");
            }

            data.append("] ");
        }
    }
}
