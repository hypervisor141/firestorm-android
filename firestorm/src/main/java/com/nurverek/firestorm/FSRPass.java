package com.nurverek.firestorm;

import vanguard.VLListType;

public class FSRPass{

    private VLListType<FSP> entries;
    
    private final long id;
    protected int debug;
    
    public FSRPass(int capacity, int debug){
        entries = new VLListType<>(capacity, capacity);
        this.debug = debug;

        id = FSRFrames.getNextID();
    }

    public void addAll(FSG<?> source){
        VLListType<FSP> programs = source.programs();
        int size = programs.size();

        for(int i = 0; i < size; i++){
            entries.add(programs.get(i));
        }
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
            entries.get(i).postFrame(FSR.CURRENT_PASS_INDEX);

            if(FSControl.DEBUG_GLOBALLY && debug >= FSControl.DEBUG_NORMAL){
                try{
                    FSTools.checkGLError();

                }catch(Exception ex){
                    throw new RuntimeException("Error running postFrameSwap() for Entry[" + i + "] PassIndex[" + FSR.CURRENT_PASS_INDEX + "]", ex);
                }
            }
        }
    }

    protected void draw(){
        FSEvents events = FSControl.getSurface().events();

        events.GLPreDraw();

        int size = entries.size();
        FSP entry;

        for(int index = 0; index < size; index++){
            entry = entries.get(index);

            FSR.CURRENT_ENTRY_INDEX = index;

            entry.draw(FSR.CURRENT_PASS_INDEX);

            if(FSControl.DEBUG_GLOBALLY && debug >= FSControl.DEBUG_NORMAL){
                try{
                    FSTools.checkGLError();

                }catch(Exception ex){
                    throw new RuntimeException("Error running draw() for Entry[" + index + "] PassIndex[" + FSR.CURRENT_PASS_INDEX + "]", ex);
                }
            }
        }

        events.GLPostDraw();
    }

    public void destroy(){
        for(int i = 0; i < entries.size(); i++){
            entries.get(i).destroy();

            if(FSControl.DEBUG_GLOBALLY && debug >= FSControl.DEBUG_NORMAL){
                try{
                    FSTools.checkGLError();

                }catch(Exception ex){
                    throw new RuntimeException("Error running destroy() for Entry[" + i + "] PassIndex[" + FSR.CURRENT_PASS_INDEX + "]", ex);
                }
            }
        }

        entries = null;
    }
}
