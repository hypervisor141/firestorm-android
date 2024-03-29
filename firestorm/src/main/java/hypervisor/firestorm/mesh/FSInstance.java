package hypervisor.firestorm.mesh;

import android.view.MotionEvent;

import hypervisor.firestorm.engine.FSControl;
import hypervisor.firestorm.engine.FSElements;
import hypervisor.firestorm.engine.FSGlobal;
import hypervisor.firestorm.program.FSLightMap;
import hypervisor.firestorm.program.FSLightMaterial;
import hypervisor.firestorm.program.FSTexture;
import hypervisor.vanguard.array.VLArrayFloat;
import hypervisor.vanguard.array.VLArrayShort;
import hypervisor.vanguard.utils.VLCopyable;
import hypervisor.vanguard.variable.VLVMatrix;

public class FSInstance implements FSTypeInstance{

    public static final long FLAG_UNIQUE_ID = 0x1L;
    public static final long FLAG_DUPLICATE_STORAGE = 0x2L;
    public static final long FLAG_DUPLICATE_SCHEMATICS = 0x4L;
    public static final long FLAG_DUPLICATE_MATERIAL = 0x8L;
    public static final long FLAG_DUPLICATE_MODEL_MATRIX_ENTRIES = 0x10L;
    public static final long FLAG_REFERENCE_MODEL_MATRIX_ENTRIES = 0x20L;

    protected FSTypeRenderGroup<?> parent;
    protected FSElementStore store;
    protected FSSchematics schematics;
    protected FSModelMatrix modelmatrix;
    protected FSTexture colortexture;
    protected FSLightMaterial material;
    protected FSLightMap lightmap;

    protected String name;
    protected long id;
    protected boolean assembled;

    public FSInstance(String name){
        this.name = name.toLowerCase();

        store = new FSElementStore(FSElements.COUNT);
        schematics = new FSSchematics();
        id = FSControl.generateUID();

        assembled = false;
    }

    public FSInstance(FSInstance src, long flags){
        copy(src, flags);
    }

    protected FSInstance(){

    }

    @Override
    public void construct(FSGlobal global){}

    @Override
    public void assemble(FSGlobal global){
        if(!assembled){
            construct(global);
            assembled = true;
        }
    }

    @Override
    public void name(String name){
        this.name = name;
    }

    @Override
    public void storage(FSElementStore store){
        this.store = store;
    }

    @Override
    public void modelMatrix(FSModelMatrix set){
        modelmatrix = set;
    }

    @Override
    public void parent(FSTypeRenderGroup<?> parent){
        this.parent = parent;
    }

    @Override
    public void colorTexture(FSTexture texture){
        this.colortexture = texture;
    }

    @Override
    public void material(FSLightMaterial material){
        this.material = material;
    }

    @Override
    public void lightMap(FSLightMap map){
        this.lightmap = map;
    }

    @Override
    public void allowReassmbly(){
        assembled = false;
    }

    @Override
    public void allocateElement(int element, int capacity, int resizeoverhead){
        store.allocateElement(element, capacity, resizeoverhead);
    }

    @Override
    public void storeElement(int element, FSElement<?, ?> data){
        store.add(element, data);
    }

    public void activateElement(int element, int index){
        store.activate(element, index);
    }

    @Override
    public void activateFirstElement(int element){
        store.activate(element, 0);
    }

    @Override
    public void activateLastElement(int element){
        store.activate(element, store.size(element) - 1);
    }

    @Override
    public FSTypeRenderGroup<?> parent(){
        return parent;
    }

    @Override
    public FSTypeRenderGroup<?> parentRoot(){
        return parent == null ? null : parent.parentRoot();
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
    public boolean assembled(){
        return assembled;
    }

    @Override
    public int vertexSize(){
        return positions().size() / FSElements.UNIT_SIZES[FSElements.ELEMENT_POSITION];
    }

    @Override
    public FSTexture colorTexture(){
        return colortexture;
    }

    @Override
    public FSLightMaterial material(){
        return material;
    }

    @Override
    public FSLightMap lightMap(){
        return lightmap;
    }

    @Override
    public FSModelMatrix modelMatrix(){
        return modelmatrix;
    }

    @Override
    public FSSchematics schematics(){
        return schematics;
    }

    @Override
    public FSElementStore storage(){
        return store;
    }

    @Override
    public int elementUnitsCount(int element){
        return element(element).size() / FSElements.UNIT_SIZES[element];
    }

    @Override
    public Object elementData(int element){
        return store.active(element).data;
    }

    @Override
    public FSElement<?, ?> element(int element){
        return store.active(element);
    }

    @Override
    public FSModelArray model(){
        return (FSModelArray)elementData(FSElements.ELEMENT_MODEL);
    }

    @Override
    public VLArrayFloat positions(){
        return (VLArrayFloat)elementData(FSElements.ELEMENT_POSITION);
    }

    @Override
    public VLArrayFloat colors(){
        return (VLArrayFloat)elementData(FSElements.ELEMENT_COLOR);
    }

    @Override
    public VLArrayFloat texCoords(){
        return (VLArrayFloat)elementData(FSElements.ELEMENT_TEXCOORD);
    }

    @Override
    public VLArrayFloat normals(){
        return (VLArrayFloat)elementData(FSElements.ELEMENT_NORMAL);
    }

    @Override
    public VLArrayShort indices(){
        return (VLArrayShort)elementData(FSElements.ELEMENT_INDEX);
    }

    @Override
    public FSElement.FloatArray modelElement(){
        return (FSElement.FloatArray)element(FSElements.ELEMENT_MODEL);
    }

    @Override
    public FSElement.FloatArray positionsElement(){
        return (FSElement.FloatArray)element(FSElements.ELEMENT_POSITION);
    }

    @Override
    public FSElement.FloatArray colorsElement(){
        return (FSElement.FloatArray)element(FSElements.ELEMENT_COLOR);
    }

    @Override
    public FSElement.FloatArray texCoordsElement(){
        return (FSElement.FloatArray)element(FSElements.ELEMENT_TEXCOORD);
    }

    @Override
    public FSElement.FloatArray normalsElement(){
        return (FSElement.FloatArray)element(FSElements.ELEMENT_NORMAL);
    }

    @Override
    public FSElement.Short indicesElement(){
        return (FSElement.Short)element(FSElements.ELEMENT_INDEX);
    }

    @Override
    public void updateBuffer(int element){
        element(element).updateBuffer();
    }

    @Override
    public void updateBuffer(int element, int bindingindex){
        element(element).updateBuffer(bindingindex);
    }

    @Override
    public void updateVertexBuffer(int element, int bindingindex){
        element(element).bindings.get(bindingindex).updateVertexBuffer();
    }

    @Override
    public void updateVertexBufferStrict(int element, int bindingindex){
        element(element).bindings.get(bindingindex).updateVertexBufferStrict();
    }

    @Override
    public void applyModelMatrix(){
        model().transform(0, modelmatrix, true);
    }

    @Override
    public void schematicsRebuild(){
        schematics.rebuild();
    }

    @Override
    public void schematicsCheckFixLocalSpaceFlatness(){
        schematics.checkFixLocalSpaceFlatness();
    }

    @Override
    public void schematicsRefillLocalSpaceBounds(){
        schematics.refillLocalSpaceBounds();
    }

    @Override
    public void schematicsRebuildLocalSpaceCentroid(){
        schematics.rebuildLocalSpaceCentroid();
    }

    @Override
    public void schematicsCheckSortLocalSpaceBounds(){
        schematics.checkSortLocalSpaceBounds();
    }

    @Override
    public void schematicsCheckSortModelSpaceBounds(){
        schematics.checkSortModelSpaceBounds();
    }

    @Override
    public void schematicsRequestFullUpdate(){
        schematics.requestFullUpdate();
    }

    @Override
    public void schematicsRequestUpdateModelSpaceBounds(){
        schematics.requestUpdateModelSpaceBounds();
    }

    @Override
    public void schematicsRequestUpdateCentroid(){
        schematics.requestUpdateModelSpaceCentroid();
    }

    @Override
    public void schematicsRequestUpdateCollisionBounds(){
        schematics.requestUpdateCollisionBounds();
    }

    @Override
    public void schematicsRequestUpdateInputBounds(){
        schematics.requestUpdateInputBounds();
    }

    @Override
    public void schematicsDirectReloadLocalSpaceBounds(){
        schematics.directReloadLocalSpaceBounds();
    }

    @Override
    public void schematicsDirectUpdateModelSpaceBounds(){
        schematics.directUpdateModelSpaceBounds();
    }

    @Override
    public void schematicsDirectUpdateCentroid(){
        schematics.directUpdateModelSpaceCentroid();
    }

    @Override
    public void schematicsDirectUpdateCollisionBounds(){
        schematics.directUpdateCollisionBounds();
    }

    @Override
    public void schematicsDirectUpdateInputBounds(){
        schematics.directUpdateInputBounds();
    }

    @Override
    public void dispatch(FSTypeRenderGroup.Dispatch<FSTypeRender> dispatch){
        dispatch.process(this);
    }

    @Override
    public boolean checkInputs(MotionEvent e1, MotionEvent e2, float f1, float f2, float[] near, float[] far){
        return schematics.checkInputCollision(e1, e2, f1, f2, near, far);
    }

    @Override
    public void scanComplete(){}

    @Override
    public void bufferComplete(){}

    @Override
    public void buildComplete(){}

    @Override
    public void copy(FSTypeRender src, long flags){
        FSInstance target = (FSInstance)src;

        name = target.name;

        if((flags & FLAG_REFERENCE) == FLAG_REFERENCE){
            store = target.store;
            schematics = target.schematics;
            modelmatrix = target.modelmatrix;
            colortexture = target.colortexture;
            material = target.material;
            lightmap = target.lightmap;
            id = target.id;

        }else if((flags & FLAG_DUPLICATE) == FLAG_DUPLICATE){
            store = target.store.duplicate(FLAG_DUPLICATE);
            schematics = target.schematics.duplicate(FLAG_DUPLICATE);

            if(target.modelmatrix != null){
                modelmatrix = target.modelmatrix.duplicate(FLAG_DUPLICATE);
            }
            if(target.material != null){
                material = target.material.duplicate(FLAG_DUPLICATE);
            }

            colortexture = target.colortexture;
            lightmap = target.lightmap;
            id = FSControl.generateUID();

        }else if((flags & FLAG_CUSTOM) == FLAG_CUSTOM){
            colortexture = target.colortexture;
            lightmap = target.lightmap;

            if((flags & FLAG_DUPLICATE_STORAGE) == FLAG_DUPLICATE_STORAGE){
                store = target.store.duplicate(FLAG_DUPLICATE);

            }else{
                store = target.store.duplicate(FLAG_REFERENCE);
            }

            if((flags & FLAG_DUPLICATE_SCHEMATICS) == FLAG_DUPLICATE_SCHEMATICS){
                schematics = target.schematics.duplicate(FLAG_DUPLICATE);

            }else{
                schematics = target.schematics.duplicate(FLAG_REFERENCE);
            }

            if(target.material != null){
                if((flags & FLAG_DUPLICATE_MATERIAL) == FLAG_DUPLICATE_MATERIAL){
                    material = target.material.duplicate(VLCopyable.FLAG_DUPLICATE);

                }else{
                    material = target.material.duplicate(VLCopyable.FLAG_REFERENCE);
                }
            }

            if(target.modelmatrix != null){
                if((flags & FLAG_DUPLICATE_MODEL_MATRIX_ENTRIES) == FLAG_DUPLICATE_MODEL_MATRIX_ENTRIES){
                    modelmatrix = target.modelmatrix.duplicate(VLCopyable.FLAG_CUSTOM | VLVMatrix.FLAG_DUPLICATE_ENTRIES);

                }else if((flags & FLAG_REFERENCE_MODEL_MATRIX_ENTRIES) == FLAG_REFERENCE_MODEL_MATRIX_ENTRIES){
                    modelmatrix = target.modelmatrix.duplicate(VLCopyable.FLAG_CUSTOM | VLVMatrix.FLAG_REFERENCE_ENTRIES);

                }else{
                    modelmatrix = target.modelmatrix.duplicate(VLVMatrix.FLAG_REFERENCE);
                }
            }

            if((flags & FLAG_UNIQUE_ID) == FLAG_UNIQUE_ID){
                id = FSControl.generateUID();

            }else{
                id = target.id;
            }

        }else{
            Helper.throwMissingAllFlags();
        }
    }

    @Override
    public FSInstance duplicate(long flags){
        return new FSInstance(this, flags);
    }

    @Override
    public void paused(){}

    @Override
    public void resumed(){}

    @Override
    public void destroy(){}
}
