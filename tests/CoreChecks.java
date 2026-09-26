import de.xianmu.arotation.overlay.Placement;
import de.xianmu.arotation.rotation.RotationPolicy;
import java.util.Random;

public final class CoreChecks {
    private static int checks;
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args) {
        for(boolean natural:new boolean[]{false,true})for(int r=0;r<4;r++)for(boolean reverse:new boolean[]{false,true}) {
            boolean landscape=RotationPolicy.isLandscape(r,natural);
            int width=landscape?1600:1000,height=landscape?1000:1600;
            check(RotationPolicy.naturalLandscape(width,height,r)==natural,"natural orientation");
            int target=RotationPolicy.toggleTarget(r,natural,reverse);
            check(target>=0&&target<4,"rotation range");
            check(RotationPolicy.isLandscape(target,natural)!=landscape,"one tap switches orientation");
            int next=RotationPolicy.toggleTarget(target,natural,reverse);
            check(RotationPolicy.isLandscape(next,natural)==landscape,"round trip");
        }
        Random random=new Random(20260926);
        for(int i=0;i<12000;i++) {
            int min=random.nextInt(100),max=min+random.nextInt(3000);
            float fraction=random.nextFloat();int position=Placement.resolve(fraction,min,max);
            check(position>=min&&position<=max,"visible bounds");
            int restored=Placement.resolve(Placement.normalize(position,min,max),min,max);
            check(position==restored,"saved position round trip");
            int edge=Placement.nearestEdge(position,min,max);check(edge==min||edge==max,"snap to an edge");
            check(Placement.resolve(-1,min,max)==min,"negative preference clamp");
            check(Placement.resolve(2,min,max)==max,"oversized preference clamp");
        }
        check(Placement.clamp(Float.NaN,4,8)==4,"NaN preference");
        check(Placement.normalize(10,10,10)==.5f,"zero available space");
        check(Placement.resolve(.8f,100,20)==100,"oversized control fallback");
        for(int current=0;current<4;current++)for(int last=0;last<4;last++) {
            check(RotationPolicy.ownsSettings(0,current,last)==(current==last),"rotation ownership");
            check(!RotationPolicy.ownsSettings(1,current,last),"respect user's automatic rotation");
        }
        System.out.println("PASS: "+checks+" deterministic core assertions");
    }
}
