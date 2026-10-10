import com.unscroll.Session;
public class SessionTest {
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
 public static void main(String[] args){
  Session s=new Session();check(s.advance(1000,1000,true,2000,3000,5000)==Session.Action.NONE,"before reminder");
  check(s.advance(2000,1000,true,2000,3000,5000)==Session.Action.REMIND,"remind once");
  check(s.advance(2500,500,true,2000,3000,5000)==Session.Action.NONE,"no duplicate");
  check(s.advance(3000,500,true,2000,3000,5000)==Session.Action.BLOCK,"block threshold");
  check(s.advance(4000,1000,false,2000,3000,5000)==Session.Action.NONE,"other apps unaffected");
  check(s.advance(5000,0,true,2000,3000,5000)==Session.Action.BLOCK,"reopen blocked");
  check(s.advance(8000,0,true,2000,3000,5000)==Session.Action.NONE&&s.used==0,"cooldown expires");
  s.advance(9000,1000,true,2000,3000,5000);s.advance(10000,0,false,2000,3000,5000);s.advance(11000,0,true,2000,3000,5000);check(s.used==1000,"short switch retains usage");
  s.advance(12000,0,false,2000,3000,5000);s.advance(18000,0,true,2000,3000,5000);check(s.used==0,"long rest resets");
  s.advance(19000,-500,true,2000,3000,5000);check(s.used==0,"negative time ignored");
  System.out.println("PASS: 10 timing policy checks");
 }
}
