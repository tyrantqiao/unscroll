package com.unscroll;
import android.app.*;import android.content.*;import android.os.*;import android.provider.Settings;import android.graphics.Color;import android.widget.*;import java.util.*;
public class MainActivity extends Activity {
 private java.util.List<AppCatalog.App> apps=new java.util.ArrayList<>(); private LinearLayout appList; private String search=""; private boolean enabledOnly; private Store store;private LinearLayout page;private final Handler handler=new Handler(Looper.getMainLooper());private String tab="today";
 private int statsDays=1; private ScrollView contentScroll;
 private final Runnable refresh=new Runnable(){public void run(){if(tab.equals("today")||tab.equals("setup")||tab.equals("stats")){int y=contentScroll==null?0:contentScroll.getScrollY();render();contentScroll.post(()->contentScroll.scrollTo(0,y));}handler.postDelayed(this,2000);}};
 @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);if(state!=null){tab=state.getString("tab","today");statsDays=state.getInt("statsDays",1);}else if(!store.prefs.getBoolean("setupFinished",false))tab="setup";}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("tab",tab);state.putInt("statsDays",statsDays);}
 @Override protected void onResume(){super.onResume();apps=AppCatalog.load(this);render();handler.postDelayed(refresh,2000);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
 private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(25,70,51));v.setPadding(0,14,0,14);page.addView(v);return v;}
 private void button(String label,Runnable action){Button b=new Button(this);b.setText(label);page.addView(b);b.setOnClickListener(v->action.run());}
 private void render(){
  LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(0xfff7f9f1);shell.setPadding(24,48,24,24);
  ScrollView scroll=new ScrollView(this);contentScroll=scroll;page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout nav=new LinearLayout(this);for(String t:new String[]{"today","rules","stats","settings"}){Button b=new Button(this);b.setText(t.equals("today")?"今日":t.equals("rules")?"应用":t.equals("stats")?"统计":"设置");b.setSelected(tab.equals(t));nav.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{tab=t;render();});}shell.addView(nav);setContentView(shell); shell.setOnApplyWindowInsetsListener((view,insets)->{ view.setPadding(24,insets.getSystemWindowInsetTop()+24,24,insets.getSystemWindowInsetBottom()+16); return insets; }); shell.requestApplyInsets();
  if(tab.equals("today")){text("unscroll",18);text("今天，给自己留一点空白。",28);text(GuardService.connected?"● 实时服务已连接 · 真机稳定性待验证":"○ 保护未运行，请到设置开启无障碍服务",15);
   if(!GuardService.connected)button("快速开启保护",()->{tab="setup";render();});
   String date=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());
   int count=0;
   for(AppCatalog.App app:apps){
    String p=app.packageName;
    if(!store.enabled(p)||!app.controllable)continue;
    count++;
    text(app.name+" · 规则开启",22);
    long ms=store.prefs.getLong(p+"today"+date,0);
    text("今日总时长："+duration(ms),18);
    text("本轮连续时长："+duration(store.prefs.getLong(p+"used",0)),18);
    text("最近一轮连续："+duration(store.prefs.getLong(p+"lastSession",0)),14);
    text(ruleSummary(p),14);
    button("调整 "+app.name,()->edit(app));
   }
   if(count==0){text("还没有开启管控的应用",20);button("选择要管理的应用",()->{tab="rules";render();});}
   text("统计只覆盖服务运行期间；分屏、小窗及后台恢复仍待验证。",13);
  }else if(tab.equals("rules")){
   text("为每个应用，设定自己的边界。",26);
   button("快速开启 · 检查完成状态",()->{tab="setup";render();});
   text("当前用户空间有桌面入口的应用 · "+apps.size()+" 个",14);
   EditText searchBox=new EditText(this);
   searchBox.setSingleLine(true);searchBox.setHint("搜索应用名称或包名");searchBox.setText(search);page.addView(searchBox);
   Switch filter=new Switch(this);filter.setText("只看已开启管控");filter.setChecked(enabledOnly);page.addView(filter);
   button("刷新应用列表",()->{apps=AppCatalog.load(this);render();});
   appList=new LinearLayout(this);appList.setOrientation(LinearLayout.VERTICAL);page.addView(appList);
   searchBox.addTextChangedListener(new android.text.TextWatcher(){
    public void beforeTextChanged(CharSequence s,int start,int count,int after){}
    public void onTextChanged(CharSequence s,int start,int before,int count){search=s.toString();renderApps();}
    public void afterTextChanged(android.text.Editable s){}
   });
   filter.setOnCheckedChangeListener((view,checked)->{enabledOnly=checked;renderApps();});
   renderApps();
  }else if(tab.equals("setup")){renderSetup();
  }else if(tab.equals("stats")){renderStats();
  }  else{ text("设备与权限",28);text(Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE,16);
   button("快速开启引导",()->{tab="setup";render();});
   text("实时服务："+(GuardService.connected?"已连接":"未连接"),16);
   text("当前提醒、中断与统计不依赖使用情况访问，无需额外授权。",14);
   button("开启实时提醒与中断",this::explainAccessibility);
   button("应用系统设置 / 后台与电池",this::openAppSettings);
   text("ColorOS：请在应用系统设置检查后台活动和电池限制；具体菜单依实际版本。先用 1 分钟提醒 / 2 分钟中断测试，确认后再配置正式阈值。",15);text(store.prefs.getString("lockResult","系统锁屏：尚未测试"),14);text(store.prefs.getString("error",""),14);
  }
 }
 private java.util.List<AppCatalog.App> enabledApps(){
  java.util.List<AppCatalog.App> result=new java.util.ArrayList<>();
  for(AppCatalog.App app:apps)if(app.controllable&&store.enabled(app.packageName))result.add(app);
  return result;
 }
 private boolean accessibilityEnabled(){
  android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
  if(manager==null)return false;
  for(android.accessibilityservice.AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
   android.content.pm.ServiceInfo service=info.getResolveInfo().serviceInfo;
   if(getPackageName().equals(service.packageName)&&GuardService.class.getName().equals(service.name))return true;
  }
  return false;
 }
 private void renderSetup(){
  boolean connected=GuardService.connected, authorized=accessibilityEnabled();
  int count=enabledApps().size();
  text("快速开启",28);text("两步开始保护，返回后自动检查。",16);
  text("1 · 开启实时服务"+(connected?" ✓":authorized?" · 等待连接":""),22);
  text(connected?"服务已连接，可以识别受管控应用并执行提醒与休息规则。":authorized?"系统开关已开启，正在等待服务连接。若长时间未连接，可在系统设置中重新开启服务。":"点击下方按钮，在系统页面开启 Unscroll，然后返回。最后的开关需要你亲自确认。",15);
  if(!connected)button(authorized?"查看服务设置":"前往开启",this::explainAccessibility);
  text("2 · 选择应用并开启规则"+(count>0?" ✓":""),22);
  text(count>0?"已为 "+count+" 个应用开启规则。":"选择需要管理的 App，设置连续使用或每日总时长规则。",15);
  button(count>0?"调整应用规则":"选择要管理的应用",()->{tab="rules";render();});
  text("后台运行设置 · 可选",20);
  text("若服务经常断开，可检查应用系统设置中的后台活动和电池限制。不同手机的菜单名称可能不同。",14);
  button("查看后台与电池设置",this::openAppSettings);
  if(connected&&count>0){
   text("准备好了，保护已运行。",20);
   button("完成，查看今日",()->{store.prefs.edit().putBoolean("setupFinished",true).apply();tab="today";render();});
  }else text("尚未完成："+(!connected?"服务未连接":"应用规则未开启"),15);
  button("稍后设置",()->{store.prefs.edit().putBoolean("setupFinished",true).apply();tab="today";render();});
 }
 private void explainAccessibility(){
  new AlertDialog.Builder(this).setTitle("开启实时保护").setMessage("Unscroll 使用窗口包名识别当前应用，并展示提醒与休息页；不读取页面文字、聊天或截图。系统锁屏仅在你开启规则开关后尝试一次。你可以随时撤销权限。\n\n接下来：开启 Unscroll 开关 → 确认系统提示 → 返回应用，状态会自动更新。")
   .setPositiveButton("了解，前往开启",(d,w)->openAccessibilitySettings()).setNegativeButton("取消",null).show();
 }
 private void openAccessibilitySettings(){
  Intent details=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
  details.putExtra("android.intent.extra.COMPONENT_NAME",new ComponentName(this,GuardService.class).flattenToString());
  try{startActivity(details);}catch(ActivityNotFoundException|SecurityException e){openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
 }
 private void openAppSettings(){openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
 private void openSettings(Intent intent){
  try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,"无法打开此设置，请在系统设置中查找 Unscroll",Toast.LENGTH_LONG).show();}
 }
 private void renderStats(){
  text("应用统计",28);text("仅统计当前已开启规则的 App",15);
  LinearLayout range=new LinearLayout(this);
  for(int days:new int[]{1,7}){Button b=new Button(this);String label=getString(days==1?R.string.stats_today:R.string.stats_week);b.setText(statsDays==days?getString(R.string.stats_selected,label):label);range.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{statsDays=days;render();});}page.addView(range);
  java.util.List<String> dates=Store.recentDays(statsDays,new Date());
  text(dates.get(0)+(statsDays==1?"":" 至 "+dates.get(dates.size()-1)),14);
  java.util.List<AppCatalog.App> selected=enabledApps();
  java.util.Map<String,Long> totals=new java.util.HashMap<>();
  long total=0;int usedApps=0;
  for(AppCatalog.App app:selected){long value=store.monitoredTime(app.packageName,dates);totals.put(app.packageName,value);total+=value;if(value>0)usedApps++;}
  text("监测总时长 · "+duration(total),24);
  text("规则开启 "+selected.size()+" 个 · 有使用记录 "+usedApps+" 个",16);
  text(GuardService.connected?"● 服务已连接":"○ 服务未连接，当前不会新增监测数据",14);
  if(!GuardService.connected)button("快速开启保护",()->{tab="setup";render();});
  if(selected.isEmpty()){text("还没有开启规则的 App",20);button("去开启应用规则",()->{tab="rules";render();});}
  else{
   text("每日监测时长",20);
   for(String date:dates){long dayTotal=0;for(AppCatalog.App app:selected)dayTotal+=store.monitoredTime(app.packageName,java.util.Collections.singletonList(date));text(date+" · "+duration(dayTotal),15);}
   selected.sort((a,b)->{int comparison=Long.compare(totals.get(b.packageName),totals.get(a.packageName));return comparison==0?a.name.compareTo(b.name):comparison;});
   text("App 用量排行",22);
   int rank=0;
   String today=dates.get(dates.size()-1);
   for(AppCatalog.App app:selected){
    String p=app.packageName;long value=totals.get(p);
    text((++rank)+" · "+app.name,20);
    text(duration(value)+" · 占比 "+(total==0?"0":String.format(Locale.ROOT,"%.1f",value*100.0/total))+"%",16);
    ProgressBar share=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);share.setMax(1000);share.setProgress(total==0?0:(int)(value*1000.0/total));share.setContentDescription(app.name+" 用量占比");page.addView(share);
    long todayTime=store.monitoredTime(p,java.util.Collections.singletonList(today));
    if(store.daily(p)){
     long limit=store.value(p,"dailyStop",60)*UsagePolicy.MINUTE;
     text("今日每日上限："+duration(todayTime)+" / "+store.value(p,"dailyStop",60)+" 分钟"+(todayTime>=limit?" · 已达上限":" · 剩余 "+duration(limit-todayTime)),14);
     ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgress(limit<=0?0:(int)Math.min(1000,todayTime*1000.0/limit));progress.setContentDescription(app.name+" 今日上限进度");page.addView(progress);
    }else text("每日总时长规则未开启",14);
    text("本轮连续："+duration(store.prefs.getLong(p+"used",0))+" · 最近一轮："+duration(store.prefs.getLong(p+"lastSession",0)),14);
    button("查看 / 调整 "+app.name+" 规则",()->edit(app));
   }
  }
  text("数据仅来自规则开启且服务运行期间的前台监测，不代表系统完整用量。无记录显示 0；关闭规则后会从此页隐藏，历史数据仍保留。",13);
 }
 private String duration(long ms){return ms/60000+" 分 "+ms/1000%60+" 秒";}
 private String ruleSummary(String p){
  return "连续规则："+(store.continuous(p)?store.value(p,"warn",15)+" 分钟提醒 / "+store.value(p,"stop",20)+" 分钟中断 / 休息 "+store.value(p,"rest",5)+" 分钟":"关闭")
   +"\n每日规则："+(store.daily(p)?store.value(p,"dailyWarn",45)+" 分钟提醒 / "+store.value(p,"dailyStop",60)+" 分钟限制至次日":"关闭")
   +"\n"+(store.prefs.getBoolean(p+"lock",false)?"中断时尝试一次锁屏":"显示休息页");
 } private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private void renderApps(){
  if(appList==null)return;
  appList.removeAllViews();
  String query=search.trim().toLowerCase(Locale.ROOT);
  java.util.List<AppCatalog.App> sorted=new java.util.ArrayList<>(apps);
  sorted.sort((a,b)->Boolean.compare(store.enabled(b.packageName),store.enabled(a.packageName)));
  int shown=0;
  for(AppCatalog.App app:sorted){
   String p=app.packageName;
   if(enabledOnly&&!store.enabled(p))continue;
   if(!app.name.toLowerCase(Locale.ROOT).contains(query)&&!p.toLowerCase(Locale.ROOT).contains(query))continue;
   shown++;
   LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackgroundColor(Color.WHITE);
   LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
   ImageView icon=new ImageView(this);icon.setImageDrawable(app.icon);icon.setContentDescription(app.name+" 图标");row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));
   TextView name=new TextView(this);name.setText(app.name);name.setTextSize(18);name.setPadding(dp(12),0,dp(8),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
   Switch toggle=new Switch(this);toggle.setContentDescription(app.name+" 管控开关");toggle.setChecked(store.enabled(p)&&app.controllable);toggle.setEnabled(app.controllable);row.addView(toggle);card.addView(row);
   TextView packageLabel=new TextView(this);packageLabel.setText(p);packageLabel.setTextSize(12);card.addView(packageLabel);
   TextView summary=new TextView(this);summary.setText(app.controllable?ruleSummary(p):"必要系统功能，保持可用");summary.setPadding(0,dp(8),0,dp(8));card.addView(summary);
   if(app.controllable){
    Button config=new Button(this);config.setText("配置提醒与中断");config.setOnClickListener(v->edit(app));card.addView(config);
    toggle.setOnCheckedChangeListener((view,checked)->{
     if(checked){try{getPackageManager().getPackageInfo(p,0);}catch(android.content.pm.PackageManager.NameNotFoundException e){toggle.setChecked(false);Toast.makeText(this,"应用已卸载，请刷新列表",Toast.LENGTH_SHORT).show();return;}}
     if(checked&&!store.continuous(p)&&!store.daily(p)){toggle.setChecked(false);edit(app);return;}
     store.setEnabled(p,checked);
     if(enabledOnly&&!checked)renderApps();
    });
   }
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(12);appList.addView(card,params);
  }
  if(shown==0){TextView empty=new TextView(this);empty.setText(enabledOnly?"没有符合条件的已开启应用":"未找到应用，试试更换搜索词或刷新列表");appList.addView(empty);}
 } private void edit(AppCatalog.App app){
  String p=app.packageName;
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(12),dp(20),dp(12));
  Switch enabled=new Switch(this);enabled.setText("启用此应用管控");enabled.setChecked(store.enabled(p));enabled.setEnabled(app.controllable);box.addView(enabled);
  Switch continuous=new Switch(this);continuous.setText("连续使用规则");continuous.setChecked(store.continuous(p));box.addView(continuous);
  TextView explanation=new TextView(this);explanation.setText("切到其他应用或锁屏结束本轮；10、30、60 分钟分别给一次小提醒。");box.addView(explanation);
  EditText[] inputs=new EditText[5];
  String[] labels={"连续：提醒分钟（1–1439）","连续：中断分钟（2–1440）","连续：休息分钟（1–60）","每日：提醒分钟（1–1439）","每日：累计上限分钟（2–1440）"};
  String[] keys={"warn","stop","rest","dailyWarn","dailyStop"};int[] defaults={15,20,5,45,60};
  for(int i=0;i<5;i++){
   if(i==3){TextView heading=new TextView(this);heading.setText("每日总时长规则 · 到达上限后限制至次日");heading.setPadding(0,dp(20),0,dp(8));box.addView(heading);}
   TextView label=new TextView(this);label.setText(labels[i]);box.addView(label);
   inputs[i]=new EditText(this);inputs[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);inputs[i].setSingleLine(true);inputs[i].setText(Integer.toString(store.value(p,keys[i],defaults[i])));box.addView(inputs[i]);
  }
  Switch daily=new Switch(this);daily.setText("开启每日总时长规则");daily.setChecked(store.daily(p));box.addView(daily);
  Switch lock=new Switch(this);lock.setText("达到任一上限时尝试一次锁屏");lock.setChecked(store.prefs.getBoolean(p+"lock",false));box.addView(lock);
  Runnable sync=()->{for(int i=0;i<3;i++)inputs[i].setEnabled(continuous.isChecked());for(int i=3;i<5;i++)inputs[i].setEnabled(daily.isChecked());};
  continuous.setOnCheckedChangeListener((v,c)->sync.run());daily.setOnCheckedChangeListener((v,c)->sync.run());sync.run();
  ScrollView scroll=new ScrollView(this);scroll.addView(box);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(app.name+" 使用边界").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   try{
    int[] values=new int[5];for(int i=0;i<5;i++)values[i]=inputs[i].isEnabled()?Integer.parseInt(inputs[i].getText().toString()):store.value(p,keys[i],defaults[i]);
    if(continuous.isChecked()&&(values[0]<1||values[0]>=values[1]||values[1]>1440||values[2]<1||values[2]>60))throw new IllegalArgumentException();
    if(daily.isChecked()&&(values[3]<1||values[3]>=values[4]||values[4]>1440))throw new IllegalArgumentException();
    if(enabled.isChecked()&&!continuous.isChecked()&&!daily.isChecked()){Toast.makeText(this,"请至少开启一套时长规则",Toast.LENGTH_SHORT).show();return;}
    if(enabled.isChecked()){try{getPackageManager().getPackageInfo(p,0);}catch(android.content.pm.PackageManager.NameNotFoundException e){Toast.makeText(this,"应用已卸载，请刷新列表",Toast.LENGTH_SHORT).show();return;}}
    store.saveRules(p,values[0],values[1],values[2],lock.isChecked(),enabled.isChecked(),continuous.isChecked(),daily.isChecked(),values[3],values[4]);dialog.dismiss();render();
   }catch(IllegalArgumentException e){Toast.makeText(this,"请填写有效整数；提醒时间必须小于上限",Toast.LENGTH_SHORT).show();}
  }));dialog.show();
 }
}
