package hypervisor.firestorm.engine;

import android.content.Context;
import android.view.Choreographer;

import hypervisor.vanguard.concurrency.VLThreadTaskType;
import hypervisor.vanguard.list.VLListType;

public class FSR{

    public static final FSRInterface DEFAULT_INTERFACE = new FSRInterface(){

        @Override
        public FSRThread create(){
            return new FSRThread();
        }
    };
    private static final Choreographer.FrameCallback CHOREOGRAPHER_CALLBACK = new Choreographer.FrameCallback(){

        @Override
        public void doFrame(long frameTimeNanos){
            postThreadTask(new FSRThread.TaskSignalFrameDraw());
        }
    };

    private static FSRInterface threadinterface;
    private static Choreographer choreographer;
    private static volatile FSRThread renderthread;

    protected static boolean isInitialized;

    private static final VLListType<Thread> threadsyncqueue = new VLListType<>(10, 10);;
    private static final VLListType<Thread> threadsyncqueuecache = new VLListType<>(10, 10);;

    private static final VLListType<FSRTask> tasks = new VLListType<>(10, 10);
    private static final VLListType<FSRTask> taskcache = new VLListType<>(10, 10);;

    public static int CURRENT_PASS_INDEX;
    public static int CURRENT_PASS_ENTRY_INDEX;

    protected static void initialize(FSRInterface threadsrc){
        FSR.threadinterface = threadsrc;

        choreographer = Choreographer.getInstance();
        isInitialized = true;

        CURRENT_PASS_INDEX = 0;
        CURRENT_PASS_ENTRY_INDEX = 0;
    }

    public static void setFSRThreadInterface(FSRInterface threadsrc){
        threadinterface = threadsrc;
    }

    protected static void requestStart(){
        if(renderthread != null){
            renderthread.unlock();

        }else{
            renderthread = threadinterface.create();
            renderthread.setDaemon(true);
            renderthread.setPriority(8);
            renderthread.setName("FSR");
            renderthread.requestStart();
        }
    }

    protected static void requestFrame(){
        if(renderthread != null && renderthread.running()){
            choreographer.postFrameCallback(CHOREOGRAPHER_CALLBACK);
        }
    }

    protected static FSRThread postThreadTask(VLThreadTaskType task){
        if(renderthread != null){
            renderthread.post(task);
        }

        return renderthread;
    }

    protected static void surfaceCreated(FSSurface surface, Context context, boolean continuing){
        FSEvents events = FSControl.events();

        events.GLPreCreated(surface, context, continuing);

        if(!continuing){
            FSGlobal.get().initialize(context);
        }

        events.GLPostCreated(surface, context, continuing);

        if(continuing){
            FSR.notifyResumed();
        }
    }

    protected static void surfaceChanged(FSSurface surface, Context context, int format, int width, int height){
        FSEvents events = FSControl.events();

        events.GLPreChange(surface, context, format, width, height);
        events.GLPostChange(surface, context, format, width, height);

        requestFrame();
    }

    protected static void drawFrame(){
        FSCFrames.timeFrameStarted();

        FSEvents events = FSControl.events();
        events.GLPreDraw();

        VLListType<FSRPass> passes = FSGlobal.get().passes;

        int size = passes.size();

        for(int i = 0; i < size; i++){
            CURRENT_PASS_INDEX = i;
            CURRENT_PASS_ENTRY_INDEX = -1;

            passes.get(i).draw();
        }

        events.GLPostDraw();
        finishFrame();
    }


    public static FSRThread renderThread(){
        return renderthread;
    }

    public static int currentPassIndex(){
        return CURRENT_PASS_INDEX;
    }

    protected static void notifyPaused(){
        FSGlobal.get().notifyPaused();
    }

    protected static void notifyResumed(){
        FSGlobal.get().notifyResumed();
    }

    public static void requestSyncWindow(){
        final Thread current = Thread.currentThread();

        synchronized(threadsyncqueue){
            threadsyncqueue.add(current);
        }
        synchronized(current){
            try{
                current.wait();

            }catch(InterruptedException ex){
                //
            }
        }
    }

    public static void notifySyncCompleted(){
        final Thread current = Thread.currentThread();

        synchronized(current){
            current.notifyAll();
        }
    }

    private static void processSyncRequests(){
        int size;

        synchronized(threadsyncqueue){
            size = threadsyncqueue.size();

            if(size == 0){
                return;

            }else{
                threadsyncqueuecache.add(threadsyncqueue);
                threadsyncqueue.clear();
            }
        }

        for(int i = 0; i < size; i++){
            Thread active = threadsyncqueuecache.get(i);

            synchronized(active){
                active.notifyAll();

                try{
                    active.wait();

                }catch(InterruptedException ex){
                    //
                }
            }
        }

        threadsyncqueuecache.clear();
    }

    public static void post(FSRTask task){
        synchronized(tasks){
            tasks.add(task);
        }

        requestFrame();
    }

    private static void processTasks(){
        synchronized(tasks){
            taskcache.add(tasks);
            tasks.clear();
        }

        int size = taskcache.size();

        for(int i = 0; i < size; i++){
            taskcache.get(i).run();
        }

        taskcache.clear();
    }

    protected static void finishFrame(){
        processTasks();
        processSyncRequests();

        FSCFrames.timeFrameEnded();
        FSCEGL.swapBuffers();
        FSGlobal.get().notifyFrameSwap();
        FSCFrames.finalizeFrame();
    }

    protected static void destroy(boolean destroyonpause){
        renderthread.lockdown();

        if(!destroyonpause){
            FSR.notifyPaused();

        }else{
            renderthread.requestDestruction();
            renderthread = null;

            CURRENT_PASS_INDEX = -1;
            CURRENT_PASS_ENTRY_INDEX = -1;

            isInitialized = false;

            threadinterface = null;
            choreographer = null;
        }
    }
}

