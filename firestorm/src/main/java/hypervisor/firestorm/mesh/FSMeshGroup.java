package hypervisor.firestorm.mesh;

import hypervisor.firestorm.automation.FSHScanner;
import hypervisor.firestorm.engine.FSControl;
import hypervisor.firestorm.engine.FSGlobal;
import hypervisor.firestorm.program.FSLightMap;
import hypervisor.firestorm.program.FSLightMaterial;
import hypervisor.firestorm.program.FSTexture;
import hypervisor.vanguard.list.VLListType;
import hypervisor.vanguard.utils.VLCopyable;

public class FSMeshGroup<ENTRY extends FSTypeRenderGroup<?>> implements FSTypeMeshGroup<ENTRY>{

    public static final long FLAG_UNIQUE_ID = 0x10L;
    public static final long FLAG_UNIQUE_NAME = 0x100L;
    public static final long FLAG_FORCE_DUPLICATE_entryS = 0x1000L;

    protected FSTypeRenderGroup<?> parent;
    protected VLListType<ENTRY> entries;
    protected String name;
    protected long id;

    public FSMeshGroup(String name, int capacity, int resizer){
        this.name = name.toLowerCase();

        entries = new VLListType<>(capacity, resizer);
        id = FSControl.getNextID();
    }

    public FSMeshGroup(FSMeshGroup<ENTRY> src, long flags){
        copy(src, flags);
    }

    protected FSMeshGroup(){

    }

    @Override
    public void build(FSGlobal global){

    }

    @Override
    public void name(String name){
        this.name = name;
    }

    @Override
    public void parent(FSTypeRenderGroup<?> parent){
        this.parent = parent;
    }

    @Override
    public void add(ENTRY entry){
        entries.add(entry);
        entry.parent(this);
    }

    @Override
    public ENTRY first(){
        return entries.get(0);
    }

    @Override
    public ENTRY last(){
        return entries.get(entries.size() - 1);
    }

    @Override
    public void remove(ENTRY entry){
        remove(entries.indexOf(entry));
    }

    @Override
    public void remove(int index){
        ENTRY entry = entries.get(index);
        entries.remove(index);
        entry.parent(null);
    }

    @Override
    public ENTRY get(int index){
        return entries.get(index);
    }

    @Override
    public VLListType<ENTRY> get(){
        return entries;
    }

    @Override
    public FSTypeRenderGroup<?> parent(){
        return parent;
    }

    @Override
    public FSTypeRenderGroup<?> parentRoot(){
        return parent == null ? this : parent.parentRoot();
    }

    @Override
    public String name(){
        return name;
    }

    @Override
    public long id(){
        return id;
    }

    @Override
    public int size(){
        return entries.size();
    }

    @Override
    public void scanComplete(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).scanComplete();
        }
    }

    @Override
    public void bufferComplete(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).bufferComplete();
        }
    }

    @Override
    public void buildComplete(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).buildComplete();
        }
    }

    @Override
    public void register(FSHScanner<?> scanner){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).register(scanner);
        }
    }

    @Override
    public void allocateElement(int element, int capacity, int resizer){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).allocateElement(element, capacity, resizer);
        }
    }

    @Override
    public void storeElement(int element, FSElement<?, ?> data){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).storeElement(element, data);
        }
    }

    @Override
    public void activateFirstElement(int element){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).activateFirstElement(element);
        }
    }

    @Override
    public void activateLastElement(int element){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).activateLastElement(element);
        }
    }

    @Override
    public void material(FSLightMaterial material){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).material(material);
        }
    }

    @Override
    public void lightMap(FSLightMap map){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).lightMap(map);
        }
    }

    @Override
    public void colorTexture(FSTexture texture){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).colorTexture(texture);
        }
    }

    @Override
    public void updateBuffer(int element){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).updateBuffer(element);
        }
    }

    @Override
    public void updateSchematicBoundaries(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).updateSchematicBoundaries();
        }
    }

    @Override
    public void markSchematicsForUpdate(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).markSchematicsForUpdate();
        }
    }

    @Override
    public void applyModelMatrix(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).applyModelMatrix();
        }
    }

    @Override
    public void dispatch(Dispatch<FSTypeRender> dispatch){
        dispatch.process(this);
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).dispatch(dispatch);
        }
    }

    @Override
    public void copy(FSTypeRender src, long flags){
        FSMeshGroup<ENTRY> target = (FSMeshGroup<ENTRY>)src;

        if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
            entries = target.entries;
            name = target.name;
            id = target.id;

        }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
            entries = target.entries.duplicate(VLListType.FLAG_FORCE_DUPLICATE_ARRAY);
            name = target.name.concat("_duplicate").concat(String.valueOf(id));
            id = FSControl.getNextID();

        }else if((flags & FLAG_CUSTOM) == FLAG_CUSTOM){
            if((flags & FLAG_FORCE_DUPLICATE_entryS) == FLAG_FORCE_DUPLICATE_entryS){
                entries = target.entries.duplicate(VLCopyable.FLAG_CUSTOM | VLListType.FLAG_FORCE_DUPLICATE_ARRAY);

            }else{
                entries = target.entries.duplicate(VLListType.FLAG_REFERENCE);
            }

            if((flags & FLAG_UNIQUE_ID) == FLAG_UNIQUE_ID){
                id = FSControl.getNextID();

            }else{
                id = target.id;
            }

            if((flags & FLAG_UNIQUE_NAME) == FLAG_UNIQUE_NAME){
                name = target.name.concat("_duplicate").concat(String.valueOf(id));

            }else{
                name = target.name;
            }

        }else{
            Helper.throwMissingAllFlags();
        }
    }

    @Override
    public FSTypeRender duplicate(long flags){
        return new FSMeshGroup<>(this, flags);
    }

    @Override
    public void destroy(){
        int size = entries.size();

        for(int i = 0; i < size; i++){
            entries.get(i).destroy();
        }

        parent = null;
        entries = null;
        name = null;

        id = -1;
    }
}