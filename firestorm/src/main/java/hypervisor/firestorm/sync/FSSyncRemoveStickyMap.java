package hypervisor.firestorm.sync;

import hypervisor.firestorm.engine.FSR;
import hypervisor.vanguard.sync.VLSyncType;
import hypervisor.vanguard.utils.VLCopyable;

public class FSSyncRemoveStickyMap<SOURCE> implements VLSyncType<SOURCE>{

    public FSRTaskSyncWrapper<SOURCE> task;

    public FSSyncRemoveStickyMap(FSRTaskSyncWrapper<SOURCE> task){
        this.task = task;
    }

    public FSSyncRemoveStickyMap(FSSyncRemoveStickyMap<SOURCE> src, long flags){
        copy(src, flags);
    }

    protected FSSyncRemoveStickyMap(){

    }

    @Override
    public void sync(SOURCE source){
        FSR.removeStickyTask(task);
    }

    @Override
    public void copy(VLSyncType<SOURCE> src, long flags){
        if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
            task = ((FSSyncRemoveStickyMap<SOURCE>)src).task;

        }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
            task = ((FSSyncRemoveStickyMap<SOURCE>)src).task.duplicate(VLCopyable.FLAG_DUPLICATE);

        }else{
            Helper.throwMissingAllFlags();
        }
    }

    @Override
    public FSSyncRemoveStickyMap<SOURCE> duplicate(long flags){
        return new FSSyncRemoveStickyMap<>(this, flags);
    }
}
