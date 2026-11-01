from PIL import Image, ImageDraw, ImageFont

ARIAL = r"C:\Windows\Fonts\arial.ttf"
ARIAL_B = r"C:\Windows\Fonts\arialbd.ttf"
def font(size, bold=False): return ImageFont.truetype(ARIAL_B if bold else ARIAL, size)
def rounded(draw, xy, r=18, fill=(235,244,255), outline=(31,78,121), width=3):
    draw.rounded_rectangle(xy, radius=r, fill=fill, outline=outline, width=width)
def box(draw, x, y, w, h, title, lines=(), title_size=32, line_size=24):
    rounded(draw, (x,y,x+w,y+h))
    draw.text((x+18,y+12), title, font=font(title_size, True), fill=(15,45,80))
    yy=y+56
    for line in lines:
        draw.text((x+18,yy), line, font=font(line_size), fill=(30,40,50))
        yy += line_size + 8
def arrow(draw, pts, dashed=False, color=(31,78,121), width=4):
    if dashed:
        for i in range(len(pts)-1):
            x1,y1=pts[i]; x2,y2=pts[i+1]; dx,dy=x2-x1,y2-y1
            length=(dx*dx+dy*dy)**0.5
            if not length: continue
            steps=max(1,int(length//14))
            for s in range(steps+1):
                if s%2: continue
                a=s/steps; b=min(1,(s+1)/steps)
                draw.line((x1+dx*a,y1+dy*a,x1+dx*b,y1+dy*b), fill=color, width=width)
    else:
        draw.line(pts, fill=color, width=width, joint='curve')
    x1,y1=pts[-2]; x2,y2=pts[-1]; dx,dy=x2-x1,y2-y1; length=(dx*dx+dy*dy)**0.5
    if length:
        ux,uy=dx/length,dy/length; px,py=-uy,ux
        draw.polygon([(x2,y2),(x2-ux*18+px*9,y2-uy*18+py*9),(x2-ux*18-px*9,y2-uy*18-py*9)], fill=color)

# Architecture
img=Image.new('RGB',(2200,1250),'white'); d=ImageDraw.Draw(img)
d.text((50,30),'Master of Reins - Layer / Package Architecture',font=font(48,True),fill=(15,45,80))
d.text((50,92),'Presentation -> World -> Entities + Systems; AI and collision systems are plugged into the world.',font=font(24),fill=(80,80,80))
box(d,70,180,520,250,'game.app (Presentation)',['Main','GamePanel (JPanel)','Camera, InputState','SpacebarControl, GameConfig'],28,24)
box(d,690,180,520,250,'game.world (Domain)',['RaceWorld','TiledMap, TreeSprite','RaceTrack, RaceProgress','TerrainEffects'],28,24)
box(d,1310,180,520,250,'game.racer (Entities)',['Racer','RacerAction, RacerStatus','Stamina, ChargeState','RacerController'],28,24)
box(d,70,560,520,250,'game.collision (System)',['CollisionSystem','CollisionShape','CollisionMapLoader','SweptTrigger'],28,24)
box(d,690,560,520,250,'game.items (System)',['StaminaPickup','Definition, Type','SpriteAnimation','Pickup hitbox'],28,24)
box(d,1310,560,520,250,'game.combat (System)',['CombatSystem','CombatState','CombatAction','CombatRenderer'],28,24)
box(d,70,940,520,220,'game.bot (AI)',['BotBrain','BotController, BotRoute','BotRouteGraph, BotNavigation','BotPersonality'],28,24)
box(d,690,940,520,220,'Data / Assets',['assets/Map/*.tmx, *.tsx','asset-packs/**/*.png','Tiled map + collision','External files, not classpath'],28,24)
box(d,1310,940,520,220,'Deployment',['Java SE 17+','Swing / AWT','Executable JAR','assets + asset-packs beside JAR'],28,24)
arrow(d,[(330,430),(330,560)],True)
arrow(d,[(950,430),(950,560)],True)
arrow(d,[(1570,430),(1570,560)],True)
arrow(d,[(590,305),(690,305)])
arrow(d,[(1210,305),(1310,305)])
arrow(d,[(590,670),(690,670)],True)
arrow(d,[(1210,670),(1310,670)],True)
d.text((70,1195),'Figure 1. Overall package architecture of the game.',font=font(22),fill=(90,90,90))
img.save('report-assets/architecture.png')

# Class diagram
img=Image.new('RGB',(2700,1900),'white'); d=ImageDraw.Draw(img)
d.text((50,30),'Master of Reins - Core Class Diagram (simplified)',font=font(48,True),fill=(15,45,80))
d.text((50,92),'Key classes and ownership / implementation relationships. See Table 2 for all packages and responsibilities.',font=font(24),fill=(80,80,80))
W,H=540,250; x=[50,710,1370,2030]; y=[180,510,840,1170]
box(d,x[0],y[0],W,H,'Main',['+ main(String[] args)','creates JFrame + GamePanel'],30,24)
box(d,x[0],y[1],W,H,'GamePanel : JPanel',['- TiledMap map','- RaceWorld world','- Racer player','+ paintComponent(Graphics)'],30,24)
box(d,x[0],y[2],W,H,'TiledMap',['- TileSet[] tileSets','- TileLayer[] layers','+ load(File): TiledMap','+ getTrees(), getCollisionShapes()'],30,24)
box(d,x[0],y[3],W,H,'TreeSprite',['- BufferedImage image','- double groundY','+ covers(...)','+ draw(...)'],30,24)
box(d,x[1],y[0],W,H,'RaceWorld',['- RaceTrack track','- List<Racer> racers','- CollisionSystem collisions','+ start(), update(), pause()'],30,24)
box(d,x[1],y[1],W,H,'RaceTrack',['- Gate[] checkpoints','- Gate finish','- BotRoute botRoute','+ load(Element,int,int)'],30,24)
box(d,x[1],y[2],W,H,'TerrainEffects',['- List<CollisionShape> waterAreas','+ isWet(Rectangle2D)','+ travelCost(...)'],30,24)
box(d,x[1],y[3],W,H,'RaceProgress',['- int passed','- double finishTime','+ check(RaceTrack,Racer)'],30,24)
box(d,x[2],y[0],W,H,'Racer',['- Stamina stamina','- RacerStatus status','- CombatState combat','- ChargeState charge','+ update(RacerAction,...)'],30,23)
box(d,x[2],y[1],W,H,'<<interface>> RacerController',['+ createAction(InputState,Racer,double)','+ createAction(InputState)'],27,23)
box(d,x[2],y[2],W,H,'PlayerController',['implements RacerController','reads InputState','returns RacerAction'],30,23)
box(d,x[2],y[3],W,H,'BotController',['implements RacerController','follows BotRoute','A* detour + fallback'],30,23)
box(d,x[3],y[0],W,H,'CollisionSystem',['- List<CollisionShape> shapes','+ moveWithContact(...)','+ overlaps(Rectangle2D)'],30,23)
box(d,x[3],y[1],W,H,'CollisionShape',['- Path2D path','- Area expanded','+ intersects(Rectangle2D)','+ expandedEdges(w,h)'],30,23)
box(d,x[3],y[2],W,H,'BotBrain',['- BotController route','- BotRouteGraph.Plan plan','- behaviors','+ decide(...)'],30,23)
box(d,x[3],y[3],W,H,'BotRouteGraph',['- points, links, difficulties','+ plan(...)','+ validate(...)'],30,23)
arrow(d,[(x[0]+W/2,y[0]+H),(x[0]+W/2,y[1])])
arrow(d,[(x[0]+W/2,y[1]+H),(x[0]+W/2,y[2])],True)
arrow(d,[(x[0]+W/2,y[2]+H),(x[0]+W/2,y[3])],True)
arrow(d,[(x[1]+W/2,y[0]+H),(x[1]+W/2,y[1])])
arrow(d,[(x[1]+W/2,y[1]+H),(x[1]+W/2,y[2])],True)
arrow(d,[(x[1]+W/2,y[2]+H),(x[1]+W/2,y[3])],True)
arrow(d,[(x[1]+W,y[0]+H/2),(x[2],y[0]+H/2)])
arrow(d,[(x[1]+W/2,y[0]+H),(x[1]+W/2,y[1])])
arrow(d,[(x[2]+W/2,y[1]+H),(x[2]+W/2,y[2])],True)
arrow(d,[(x[2]+W/2,y[2]+H),(x[2]+W/2,y[3])],True)
arrow(d,[(x[3]+W/2,y[0]+H),(x[3]+W/2,y[1])])
arrow(d,[(x[3]+W/2,y[2]+H),(x[3]+W/2,y[3])])
# RaceWorld uses CollisionSystem: route below the first row
arrow(d,[(x[1]+W/2,y[0]+H),(x[1]+W/2,470),(x[3]+W/2,470),(x[3]+W/2,y[1])],True)
d.text((50,1815),'Legend: solid arrow = creates/owns/reads; dashed arrow = uses/implements. RaceWorld also owns RaceProgress and the pickup list; GamePanel also owns Camera/InputState/SpacebarControl.',font=font(21),fill=(80,80,80))
img.save('report-assets/class-diagram.png')

# GUI
img=Image.new('RGB',(1900,1300),'white'); d=ImageDraw.Draw(img)
d.text((50,30),'Master of Reins - GUI Structure',font=font(48,True),fill=(15,45,80))
d.text((50,92),'JFrame -> GamePanel (custom painted) -> Swing controls + 2D HUD.',font=font(24),fill=(80,80,80))
box(d,600,150,650,150,'JFrame (Main)',['title: "Master of Reins"','MAXIMIZED_BOTH','EXIT_ON_CLOSE'],30,24)
box(d,470,370,950,720,'GamePanel : JPanel',['setLayout(null), double buffered','Key bindings + Swing Timer (16 ms)'],32,24)
box(d,510,500,430,145,'stageMenu : JPanel',['GridLayout(1,2,20,0)','marathonButton, sprintRaceButton'],25,22)
box(d,970,500,410,145,'pauseButton : PauseButton',['extends RoundedButton','icon painted in paintComponent'],25,22)
box(d,510,680,430,145,'pauseMenu : RoundedPanel',['GridLayout(3,1)','title, Resume, Quit'],25,22)
box(d,970,680,410,145,'finishMenu : RoundedPanel',['FlowLayout','Race Again, Quit Game'],25,22)
box(d,510,860,870,100,'HUD / world : Graphics2D',['stamina bar, SPRINT ON/OFF, SPEED value, checkpoint messages'],25,22)
box(d,40,370,390,160,'InputMap / ActionMap',['W, S, UP, DOWN','SPACE, O, J, K','P, ESC, F3'],26,22)
box(d,40,620,390,160,'InputState',['movingUp / movingDown','sprinting, charging','attack / guard flags'],26,22)
box(d,40,850,390,160,'Controllers',['PlayerController','BotController','RacerAction'],26,22)
box(d,1480,370,380,160,'Swing Timer',['16 ms update loop','RaceWorld.update','repaint()'],26,22)
box(d,1480,620,380,160,'ActionListener',['start race','pause / resume','quit / race again'],26,22)
box(d,1480,850,380,160,'Component / Focus',['resize -> positionControls','focus lost -> clear input'],26,22)
arrow(d,[(430,470),(470,470)],True)
arrow(d,[(235,530),(235,620)])
arrow(d,[(430,700),(470,700)],True)
arrow(d,[(1480,470),(1420,470)],True)
arrow(d,[(1480,700),(1420,700)],True)
arrow(d,[(1480,930),(1420,930)],True)
img.save('report-assets/gui-structure.png')
print('diagrams regenerated')
