package hypervisor.firestorm.engine;

import android.opengl.GLES32;

import hypervisor.firestorm.program.FSConfigGroup;
import hypervisor.firestorm.program.FSP;
import hypervisor.firestorm.tools.FSLog;
import hypervisor.firestorm.tools.FSTools;
import hypervisor.vanguard.list.arraybacked.VLListType;

public class FSRPass{

    protected VLListType<FSP> entries;

    protected long id;
    protected int debug;

    protected float[] clearcolor;
    protected int clearbits;

    protected FSConfigGroup preconfig;
    protected FSConfigGroup postconfig;
    protected FSLog log;
    
    public FSRPass(float[] clearcolor, int clearbits, FSConfigGroup preconfig, FSConfigGroup postconfig, int capacity, int debug){
        this.clearcolor = clearcolor;
        this.clearbits = clearbits;
        this.preconfig = preconfig;
        this.postconfig = postconfig;
        this.debug = debug;

        initialize(capacity);
    }

    public FSRPass(float[] clearcolor, int clearbits, int capacity, int debug){
        this.clearcolor = clearcolor;
        this.clearbits = clearbits;
        this.debug = debug;

        initialize(capacity);
    }

    protected FSRPass(){

    }

    private void initialize(int capacity){
        entries = new VLListType<>(capacity, capacity);
        id = FSControl.generateUID();

        if(debug >= FSControl.DEBUG_NORMAL){
            log = new FSLog(20);
            log.addTag(getClass().getSimpleName() + "-" + id);
        }
    }

    public void clearBit(int bits){
        clearbits = bits;
    }

    public void color(float[] color){
        clearcolor = color;
    }

    public void preConfig(FSConfigGroup config){
        preconfig = config;
    }

    public void postConfig(FSConfigGroup config){
        postconfig = config;
    }

    public void add(FSP entry){
        entries.add(entry);
    }

    public void add(int index, FSP entry){
        entries.add(index, entry);
    }

    public void remove(FSP target){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            if(entries.get(i).id() == target.id()){
                entries.remove(i);
                i--;
            }
        }
    }

    public FSP get(int index){
        return entries.get(index);
    }

    public VLListType<FSP> get(){
        return entries;
    }

    public float[] color(){
        return clearcolor;
    }

    public FSConfigGroup preConfig(){
        return preconfig;
    }

    public FSConfigGroup postConfig(){
        return postconfig;
    }

    public int clearBits(){
        return clearbits;
    }

    public long id(){
        return id;
    }

    public FSP findEntryByID(int id){
        FSP entry;

        for(int i = 0; i < entries.size(); i++){
            entry = entries.get(i);

            if(entry.id() == id){
                return entry;
            }
        }

        return null;
    }

    public int size(){
        return entries.size();
    }

    protected void noitifyPostFrameSwap(){
        for(int i = 0; i < entries.size(); i++){
            entries.get(i).postFrame(this, FSR.CURRENT_PASS_INDEX);

            if(debug >= FSControl.DEBUG_NORMAL){
                try{
                    FSTools.checkGLError();

                }catch(Exception ex){
                    throw new RuntimeException("Error running postFrameSwap() for Entry[" + i + "] PassIndex[" + FSR.CURRENT_PASS_INDEX + "]", ex);
                }
            }
        }
    }

    protected void draw(){
        if(debug >= FSControl.DEBUG_NORMAL){
            try{
                FSTools.checkGLError();
                FSTools.checkGLError();

            }catch(Exception ex){
                throw new RuntimeException("Pre-pass-draw error, there is an unchecked EGL/GL error somewhere in your code before this point.", ex);
            }

            if(preconfig != null){
                preconfig.runDebug(this, null, null, -1, FSR.CURRENT_PASS_INDEX, log, debug);
            }

        }else{
            if(preconfig != null){
                preconfig.run(this, null, null, -1, FSR.CURRENT_PASS_INDEX);
            }
        }

        GLES32.glClear(clearbits);
        GLES32.glClearColor(clearcolor[0], clearcolor[1], clearcolor[2], clearcolor[3]);

        try{
            FSTools.checkGLError();
            FSTools.checkGLError();

        }catch(Exception ex){
            throw new RuntimeException("two.", ex);
        }

        try{
            FSTools.checkGLError();
            FSTools.checkGLError();

        }catch(Exception ex){
            throw new RuntimeException("three.", ex);
        }

        int size = entries.size();

        for(int index = 0; index < size; index++){
            FSP entry = entries.get(index);
            FSR.CURRENT_PASS_ENTRY_INDEX = index;

            entry.draw(this, FSR.CURRENT_PASS_INDEX);

            if(debug >= FSControl.DEBUG_NORMAL){
                try{
                    FSTools.checkGLError();
                    FSTools.checkEGLError();

                }catch(Exception ex){
                    throw new RuntimeException("GL/EGL error running draw() for Entry[" + index + "] PassIndex[" + FSR.CURRENT_PASS_INDEX + "]", ex);
                }
            }
        }

        if(postconfig != null){
            if(debug >= FSControl.DEBUG_NORMAL){
                postconfig.runDebug(this, null, null, -1, FSR.CURRENT_PASS_INDEX, log, debug);

            }else{
                postconfig.run(this, null, null, -1, FSR.CURRENT_PASS_INDEX);
            }
        }
    }
}
